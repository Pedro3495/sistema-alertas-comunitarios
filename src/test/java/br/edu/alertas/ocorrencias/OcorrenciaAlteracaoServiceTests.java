package br.edu.alertas.ocorrencias;

import br.edu.alertas.auditoria.AuditoriaService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;

// RF011 regras 1–6, alteração e exclusão; auditoria do RF013 regra 4. PostgreSQL real e envio simulado.
@SpringBootTest
class OcorrenciaAlteracaoServiceTests extends OcorrenciasFixtures {
    @MockitoSpyBean AuditoriaService auditoria; // mesmos spies do AlertaServiceTests: reaproveita o contexto e o pool
    @MockitoSpyBean EnvioAlertas envio;
    @Autowired AlertaService alertas;

    @Test
    void corrigeCamposPermitidosComAuditoriaAnteriorENovoSemAvisoQuandoNaoHaAlerta() {
        long outraArea = novaArea();
        String cpf = novoMorador(outraArea);
        var salva = service.registrar(dados(UUID.randomUUID()));
        var p = salva.getProtocolo();
        var fotoAntes = jdbc.queryForObject("SELECT conteudo FROM fotos_ocorrencia WHERE ocorrencia_id = ?", byte[].class, salva.getId());

        service.alterar(p, TipoOcorrencia.OUTROS, "Queda de árvore", "Descrição corrigida", "Ponte fictícia", outraArea,
                "  Morador informou endereço errado.  ", null, "Evite a ponte");

        var atual = service.buscar(p);
        assertThat(atual.getTipo()).isEqualTo(TipoOcorrencia.OUTROS);
        assertThat(atual.getEspecificacaoOutros()).isEqualTo("Queda de árvore");
        assertThat(atual.getDescricao()).isEqualTo("Descrição corrigida");
        assertThat(atual.getAreaId()).isEqualTo(outraArea);
        assertThat(atual.getSituacao()).isEqualTo("Em verificação");
        // Regra 5: fotos preservadas, sem remoção nem substituição.
        assertThat(jdbc.queryForObject("SELECT conteudo FROM fotos_ocorrencia WHERE ocorrencia_id = ?", byte[].class, salva.getId())).isEqualTo(fotoAntes);
        String areaOriginal = jdbc.queryForObject("SELECT nome FROM areas_risco WHERE id = ?", String.class, area);
        String areaNova = jdbc.queryForObject("SELECT nome FROM areas_risco WHERE id = ?", String.class, outraArea);
        String resumo = "Tipo: de \"Desastre\" para \"Outros\". Especificação de Outros: de \"\" para \"Queda de árvore\"."
                + " Descrição: de \"Descrição fictícia\" para \"Descrição corrigida\"."
                + " Área de risco: de \"" + areaOriginal + "\" para \"" + areaNova + "\".";
        assertThat(service.alteracoes(p)).singleElement().satisfies(a -> {
            assertThat(a.getJustificativa()).isEqualTo("Morador informou endereço errado.");
            assertThat(a.getResumo()).isEqualTo(resumo);
            assertThat(a.getRepresentanteNome()).isEqualTo(representante.getNomeCompleto());
        });
        // Regra 6 / RF013 regra 4: valores anteriores e novos, responsável e instante; justificativa fora da auditoria.
        assertThat(acoes()).containsExactly("REGISTRAR_OCORRENCIA #" + p, "ALTERAR_OCORRENCIA #" + p + ": " + resumo);
        assertThat(String.join(" ", acoes())).doesNotContain("endereço errado");
        // Regra 2: sem alerta anterior, nenhum aviso; a ocorrência atualizada fica para consulta no aplicativo.
        assertThat(alertas.daOcorrencia(p)).isEmpty();
        entrarComoMorador(cpf);
        assertThat(service.buscarDoMorador(p).getDescricao()).isEqualTo("Descrição corrigida");
    }

    @Test
    void comAlertaAnteriorAvisaAUniaoDosDestinatariosAtivosComResumoSemJustificativa() {
        long recebeu = idDoMorador(novoMorador(area)), desativado = idDoMorador(novoMorador(area));
        var p = service.registrar(dados(UUID.randomUUID())).getProtocolo();
        service.verificar(p, SituacaoOcorrencia.CONFIRMADA, "Vistoria fictícia");
        alertas.emitir(p, "Alerta", "M", "O");
        jdbc.update("UPDATE moradores SET ativa = false WHERE id = ?", desativado);
        long outraArea = novaArea();
        long daNovaArea = idDoMorador(novoMorador(outraArea));

        // Decisão de Pedro (23/11/2026): com alerta anterior, o resumo escrito pelo representante é obrigatório.
        assertThatExceptionOfType(ErroOcorrencia.class).isThrownBy(() -> service.alterar(p, TipoOcorrencia.DESASTRE, null,
                "Descrição fictícia", "Rua corrigida", outraArea, "Justificativa interna fictícia", " ", ""))
                .satisfies(e -> assertThat(e.getCampo()).isEqualTo("resumo"));
        assertThat(service.alteracoes(p)).isEmpty(); // nada gravado nem auditado
        assertThat(acoes()).noneMatch(a -> a.startsWith("ALTERAR_OCORRENCIA"));

        service.alterar(p, TipoOcorrencia.DESASTRE, null, "Descrição fictícia", "Rua corrigida", outraArea, "Justificativa interna fictícia",
                "O ponto de referência foi corrigido para a rua principal.", "");

        var aviso = alertas.daOcorrencia(p).get(0);
        assertThat(aviso.getTipo()).isEqualTo(TipoComunicado.ALTERACAO);
        assertThat(aviso.getTitulo()).isEqualTo("Alteração da ocorrência " + p);
        // O aviso leva o resumo aprovado, não o texto gerado por campo (que continua na auditoria e no histórico).
        assertThat(aviso.getMensagem()).isEqualTo("O ponto de referência foi corrigido para a rua principal.");
        assertThat(service.alteracoes(p)).singleElement().extracting(AlteracaoOcorrencia::getResumo).asString()
                .startsWith("Localização: de \"Ponte fictícia\" para \"Rua corrigida\".");
        assertThat(aviso.getOrientacoes()).isNull(); // orientações só quando necessárias
        // Regra 3: destinatários das emissões anteriores, sem desativado; quem só está na nova área não recebe.
        assertThat(jdbc.queryForList("SELECT morador_id FROM destinatarios_comunicado WHERE comunicado_id = ?", Long.class, aviso.getId()))
                .containsExactly(recebeu).doesNotContain(desativado, daNovaArea);
        assertThat(aviso.getFalhas()).isZero();
        assertThat(service.buscar(p).getSituacao()).isEqualTo("Confirmada");
    }

    @Test
    void mantemValidacoesExigeJustificativaEMudancaERecusaEncerradaSemGravar() {
        var p = service.registrar(dados(UUID.randomUUID())).getProtocolo();
        Object[][] invalidos = {
                {null, null, "D", "L", area, "J", "tipo"},
                {TipoOcorrencia.OUTROS, " ", "D", "L", area, "J", "especificacaoOutros"},
                {TipoOcorrencia.OBRA, null, " ", "L", area, "J", "descricao"},
                {TipoOcorrencia.OBRA, null, "D", "", area, "J", "localizacao"},
                {TipoOcorrencia.OBRA, null, "D", "L", null, "J", "areaId"},
                {TipoOcorrencia.OBRA, null, "D", "L", -1L, "J", "areaId"},
                {TipoOcorrencia.OBRA, null, "D", "L", area, "  ", "justificativa"},
                {TipoOcorrencia.DESASTRE, null, "Descrição fictícia", "Ponte fictícia", area, "Nada mudou", "alteracao"}};
        for (var c : invalidos) {
            assertThatExceptionOfType(ErroOcorrencia.class)
                    .isThrownBy(() -> service.alterar(p, (TipoOcorrencia) c[0], (String) c[1], (String) c[2], (String) c[3], (Long) c[4], (String) c[5], null, null))
                    .satisfies(e -> assertThat(e.getCampo()).isEqualTo(c[6]));
        }
        jdbc.update("UPDATE ocorrencias SET situacao = 'ENCERRADA' WHERE protocolo = ?", p);
        assertThatExceptionOfType(ErroOcorrencia.class)
                .isThrownBy(() -> service.alterar(p, TipoOcorrencia.OBRA, null, "D", "L", area, "J", null, null)).withMessageContaining("encerrada");
        assertThat(service.buscar(p).getTipo()).isEqualTo(TipoOcorrencia.DESASTRE);
        assertThat(service.alteracoes(p)).isEmpty();
        assertThat(acoes()).hasSize(1);
    }

    @Test
    void qualquerRepresentanteAlteraQualquerAreaEMoradorAdministradorEAnonimoNao() {
        String cpf = novoMorador(novaArea());
        entrarComoMorador(cpf);
        var p = service.registrarPorMorador(new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.OBRA, null, "D", "L", null, List.of(imagem("png")))).getProtocolo();
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.alterar(p, TipoOcorrencia.OBRA, null, "Autor", "L", area, "J", null, null));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.alteracoes(p));
        autenticar(administrador(), "ADMINISTRADOR");
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.alterar(p, TipoOcorrencia.OBRA, null, "Admin", "L", area, "J", null, null));
        SecurityContextHolder.clearContext();
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.alterar(p, TipoOcorrencia.OBRA, null, "Anônimo", "L", area, "J", null, null));

        var outro = novaConta(true);
        autenticar(acesso.autenticar(outro.getLogin(), DEFINITIVA).orElseThrow(), "REPRESENTANTE");
        service.alterar(p, TipoOcorrencia.OBRA, null, "Corrigida", "L", area, "Outra área, outro representante", null, null);
        assertThat(service.alteracoes(p)).singleElement().extracting(AlteracaoOcorrencia::getRepresentanteNome).isEqualTo(outro.getNomeCompleto());
        assertThat(service.buscar(p).getMoradorId()).isEqualTo(idDoMorador(cpf)); // autor preservado
        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> service.alterar(UUID.randomUUID(), TipoOcorrencia.OBRA, null, "D", "L", area, "J", null, null));
    }
}
