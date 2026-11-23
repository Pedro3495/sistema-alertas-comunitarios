package br.edu.alertas.ocorrencias;

import br.edu.alertas.auditoria.AuditoriaService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;

// RF012 regras 1–4, alteração, exclusão e pesquisa; RF008 regras 3 e 6; RF013 regras 1 e 4. PostgreSQL real e envio simulado.
@SpringBootTest
class OcorrenciaEncerramentoServiceTests extends OcorrenciasFixtures {
    @MockitoSpyBean AuditoriaService auditoria; // mesmos spies do AlertaServiceTests: reaproveita o contexto e o pool
    @MockitoSpyBean EnvioAlertas envio;
    @Autowired AlertaService alertas;

    @Test
    void semAlertaAnteriorEncerraSemAvisoEFicaSoParaConsulta() {
        String cpf = novoMorador(area);
        var p = service.registrar(dados(UUID.randomUUID())).getProtocolo();

        service.encerrar(p, "  Vistoria concluída.  ", "", "");

        assertThat(service.buscar(p).getSituacao()).isEqualTo("Encerrada");
        assertThat(service.encerramento(p)).hasValueSatisfying(e -> {
            assertThat(e.getJustificativa()).isEqualTo("Vistoria concluída.");
            assertThat(e.getRepresentanteNome()).isEqualTo(representante.getNomeCompleto());
        });
        // RF013 regras 1 e 4: situação anterior e nova, sem a justificativa interna.
        assertThat(acoes()).containsExactly("REGISTRAR_OCORRENCIA #" + p, "ENCERRAR_OCORRENCIA #" + p + ": Em verificação -> Encerrada");
        // Regra 3: sem alerta anterior, nenhum aviso (o resumo não é exigido); pesquisa pelo RF008.
        assertThat(alertas.daOcorrencia(p)).isEmpty();
        assertThat(service.pesquisar(p, null, SituacaoOcorrencia.ENCERRADA, null)).extracting(ResumoOcorrencia::protocolo).containsExactly(p);
        entrarComoMorador(cpf);
        assertThat(service.pesquisarDoMorador(p, null, SituacaoOcorrencia.ENCERRADA)).extracting(ResumoOcorrencia::protocolo).containsExactly(p);
    }

    @Test
    void comAlertaAvisaAUniaoDosDestinatariosAtivosComResumoEOrientacoesSemJustificativa() {
        long recebeu = idDoMorador(novoMorador(area)), desativado = idDoMorador(novoMorador(area));
        var p = service.registrar(dados(UUID.randomUUID())).getProtocolo();
        service.verificar(p, SituacaoOcorrencia.CONFIRMADA, "Vistoria fictícia");
        alertas.emitir(p, "Alerta", "M", "O");
        jdbc.update("UPDATE moradores SET ativa = false WHERE id = ?", desativado);
        long chegouDepois = idDoMorador(novoMorador(area)); // não recebeu emissão anterior

        // Regra 3: o aviso precisa do resumo do motivo; nada é gravado sem ele.
        assertThatExceptionOfType(ErroOcorrencia.class).isThrownBy(() -> service.encerrar(p, "Justificativa interna fictícia", " ", "Volte para casa"))
                .satisfies(e -> assertThat(e.getCampo()).isEqualTo("resumo"));
        assertThat(service.buscar(p).getSituacao()).isEqualTo("Confirmada");
        assertThat(service.encerramento(p)).isEmpty();

        service.encerrar(p, "Justificativa interna fictícia", "O trecho já não apresenta risco.", "Volte para casa");

        var aviso = alertas.daOcorrencia(p).get(0);
        assertThat(aviso.getTipo()).isEqualTo(TipoComunicado.ENCERRAMENTO);
        assertThat(aviso.getTitulo()).isEqualTo("Encerramento da ocorrência " + p);
        assertThat(aviso.getMensagem()).isEqualTo("O trecho já não apresenta risco.");
        assertThat(aviso.getOrientacoes()).isEqualTo("Volte para casa");
        // Regra 2: união dos destinatários das emissões anteriores, exceto desativados.
        assertThat(jdbc.queryForList("SELECT morador_id FROM destinatarios_comunicado WHERE comunicado_id = ?", Long.class, aviso.getId()))
                .containsExactly(recebeu).doesNotContain(desativado, chegouDepois);
        assertThat(aviso.getFalhas()).isZero();
        assertThat(String.join(" ", acoes())).doesNotContain("Justificativa interna fictícia");
    }

    @Test
    void justificativaObrigatoriaSemReaberturaNemNovasAlteracoesEPreservaRegistros() {
        var p = service.registrar(dados(UUID.randomUUID())).getProtocolo();
        assertThatExceptionOfType(ErroOcorrencia.class).isThrownBy(() -> service.encerrar(p, "  ", null, null))
                .satisfies(e -> assertThat(e.getCampo()).isEqualTo("justificativa"));
        assertThat(service.buscar(p).getSituacao()).isEqualTo("Em verificação");

        service.encerrar(p, "Encerrada", null, null);

        assertThatExceptionOfType(ErroOcorrencia.class).isThrownBy(() -> service.encerrar(p, "De novo", null, null)).withMessageContaining("encerrada");
        assertThatExceptionOfType(ErroOcorrencia.class).isThrownBy(() -> service.verificar(p, SituacaoOcorrencia.CONFIRMADA, "Reabrir")).withMessageContaining("encerrada");
        assertThatExceptionOfType(ErroOcorrencia.class)
                .isThrownBy(() -> service.alterar(p, TipoOcorrencia.OBRA, null, "D", "L", area, "J", null, null)).withMessageContaining("encerrada");
        assertThatExceptionOfType(ErroOcorrencia.class).isThrownBy(() -> alertas.emitir(p, "T", "M", "O"));
        assertThat(service.buscar(p).getSituacao()).isEqualTo("Encerrada");
        assertThat(acoes()).hasSize(2);
        // Exclusão: encerramento, ocorrência e fotos não podem ser apagados.
        long id = service.buscar(p).getId();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ocorrencias WHERE id = ?", id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO encerramentos_ocorrencia (ocorrencia_id, justificativa, representante_id, encerrada_em) VALUES (?, 'x', ?, now())",
                id, representante.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void qualquerRepresentanteEncerraQualquerAreaEMoradorAdministradorEAnonimoNao() {
        String cpf = novoMorador(novaArea());
        entrarComoMorador(cpf);
        var p = service.registrarPorMorador(new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.OBRA, null, "D", "L", null, List.of(imagem("png")))).getProtocolo();
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.encerrar(p, "J", null, null));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.encerramento(p));
        autenticar(administrador(), "ADMINISTRADOR");
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.encerrar(p, "J", null, null));
        SecurityContextHolder.clearContext();
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.encerrar(p, "J", null, null));

        var outro = novaConta(true);
        autenticar(acesso.autenticar(outro.getLogin(), DEFINITIVA).orElseThrow(), "REPRESENTANTE");
        service.encerrar(p, "Outra área, outro representante", null, null);
        assertThat(service.encerramento(p)).hasValueSatisfying(e -> assertThat(e.getRepresentanteNome()).isEqualTo(outro.getNomeCompleto()));
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> service.encerrar(UUID.randomUUID(), "J", null, null));
    }
}
