package br.edu.alertas.ocorrencias;

import br.edu.alertas.auditoria.AuditoriaService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;

// RF009 regras 2 e 4, alteração e auditoria (RF013 regras 1 e 4), com PostgreSQL real.
@SpringBootTest
class OcorrenciaVerificacaoServiceTests extends OcorrenciasFixtures {
    @MockitoSpyBean AuditoriaService auditoria; // mesma configuração de contexto das outras classes: reaproveita o pool

    @Test
    void confirmaERevePersistindoJustificativaEAuditoriaComValoresAnteriorENovo() {
        var salva = service.registrar(dados(UUID.randomUUID()));
        var p = salva.getProtocolo();

        assertThat(service.verificar(p, SituacaoOcorrencia.CONFIRMADA, "  Vistoria fictícia confirmou.  ").getSituacao()).isEqualTo("Confirmada");
        assertThat(service.verificar(p, SituacaoOcorrencia.NAO_CONFIRMADA, "Revisão fictícia: trote.").getSituacao()).isEqualTo("Não confirmada");

        assertThat(jdbc.queryForObject("SELECT situacao FROM ocorrencias WHERE id = ?", String.class, salva.getId())).isEqualTo("NAO_CONFIRMADA");
        assertThat(service.verificacoes(p)).extracting(VerificacaoOcorrencia::getSituacao, VerificacaoOcorrencia::getJustificativa)
                .containsExactly(tuple("Não confirmada", "Revisão fictícia: trote."), tuple("Confirmada", "Vistoria fictícia confirmou."));
        assertThat(acoes()).containsExactly("REGISTRAR_OCORRENCIA #" + p,
                "VERIFICAR_OCORRENCIA #" + p + ": Em verificação -> Confirmada",
                "VERIFICAR_OCORRENCIA #" + p + ": Confirmada -> Não confirmada");
        assertThat(String.join(" ", acoes())).doesNotContain("trote", "Vistoria"); // justificativa interna, fora da auditoria
        assertThat(service.pesquisar(null, null, SituacaoOcorrencia.NAO_CONFIRMADA, area)).extracting(ResumoOcorrencia::protocolo).containsExactly(p);
    }

    @Test
    void rejeitaDecisaoInvalidaJustificativaVaziaRepeticaoEEncerradaSemGravar() {
        var p = service.registrar(dados(UUID.randomUUID())).getProtocolo();
        for (var decisao : new SituacaoOcorrencia[] {null, SituacaoOcorrencia.EM_VERIFICACAO, SituacaoOcorrencia.ENCERRADA}) {
            assertThatExceptionOfType(ErroOcorrencia.class).isThrownBy(() -> service.verificar(p, decisao, "Justificativa"))
                    .satisfies(e -> assertThat(e.getCampo()).isEqualTo("situacao"));
        }
        for (String vazia : new String[] {null, "", "   "}) {
            assertThatExceptionOfType(ErroOcorrencia.class).isThrownBy(() -> service.verificar(p, SituacaoOcorrencia.CONFIRMADA, vazia))
                    .satisfies(e -> assertThat(e.getCampo()).isEqualTo("justificativa"));
        }
        assertThat(service.buscar(p).getSituacao()).isEqualTo("Em verificação");
        service.verificar(p, SituacaoOcorrencia.CONFIRMADA, "Confirmada");
        assertThatExceptionOfType(ErroOcorrencia.class).isThrownBy(() -> service.verificar(p, SituacaoOcorrencia.CONFIRMADA, "De novo"));

        // RF008 regra 3 / alteração do RF009: encerrada não é reaberta nem revista (o encerramento em si é do RF012).
        jdbc.update("UPDATE ocorrencias SET situacao = 'ENCERRADA' WHERE protocolo = ?", p);
        assertThatExceptionOfType(ErroOcorrencia.class).isThrownBy(() -> service.verificar(p, SituacaoOcorrencia.NAO_CONFIRMADA, "Rever"))
                .withMessageContaining("encerrada");
        assertThat(service.buscar(p).getSituacao()).isEqualTo("Encerrada");
        assertThat(service.verificacoes(p)).hasSize(1);
        assertThat(acoes()).hasSize(2);
    }

    @Test
    void qualquerRepresentanteVerificaQualquerAreaEOutrosPerfisNao() {
        String cpf = novoMorador(novaArea());
        entrarComoMorador(cpf);
        var doMorador = service.registrarPorMorador(new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.OBRA, null, "D", "L", null, List.of(imagem("png"))));
        var p = doMorador.getProtocolo();
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.verificar(p, SituacaoOcorrencia.CONFIRMADA, "Morador"));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.verificacoes(p));
        autenticar(administrador(), "ADMINISTRADOR");
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.verificar(p, SituacaoOcorrencia.CONFIRMADA, "Admin"));
        SecurityContextHolder.clearContext();
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.verificar(p, SituacaoOcorrencia.CONFIRMADA, "Anônimo"));

        var outro = novaConta(true);
        autenticar(acesso.autenticar(outro.getLogin(), DEFINITIVA).orElseThrow(), "REPRESENTANTE");
        service.verificar(p, SituacaoOcorrencia.CONFIRMADA, "Outra área, outro representante");
        assertThat(service.verificacoes(p)).singleElement().extracting(VerificacaoOcorrencia::getRepresentanteNome).isEqualTo(outro.getNomeCompleto());
        assertThatExceptionOfType(jakarta.persistence.EntityNotFoundException.class)
                .isThrownBy(() -> service.verificar(UUID.randomUUID(), SituacaoOcorrencia.CONFIRMADA, "Inexistente"));
    }
}
