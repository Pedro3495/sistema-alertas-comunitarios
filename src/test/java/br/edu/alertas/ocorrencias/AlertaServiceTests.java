package br.edu.alertas.ocorrencias;

import br.edu.alertas.auditoria.AuditoriaService;
import br.edu.alertas.moradores.CanalCredenciais;
import jakarta.persistence.EntityNotFoundException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static br.edu.alertas.moradores.CanalCredenciais.SMS;
import static br.edu.alertas.moradores.CanalCredenciais.WHATSAPP;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// RF010 regras 1–7 e aviso de alteração do RF009 regra 4, com PostgreSQL real e envio simulado.
@SpringBootTest
class AlertaServiceTests extends OcorrenciasFixtures {
    @MockitoSpyBean AuditoriaService auditoria;
    @MockitoSpyBean EnvioAlertas envio;
    @Autowired AlertaService alertas;

    @Test
    void emiteSoDeConfirmadaComTituloMensagemEOrientacoesObrigatorios() {
        var p = service.registrar(dados(UUID.randomUUID())).getProtocolo();
        assertThatExceptionOfType(ErroOcorrencia.class).isThrownBy(() -> alertas.emitir(p, "T", "M", "O")).withMessageContaining("Confirmada");
        service.verificar(p, SituacaoOcorrencia.CONFIRMADA, "Vistoria fictícia");
        for (var campos : new String[][] {{" ", "M", "O", "titulo"}, {"T", null, "O", "mensagem"}, {"T", "M", "", "orientacoes"}}) {
            assertThatExceptionOfType(ErroOcorrencia.class).isThrownBy(() -> alertas.emitir(p, campos[0], campos[1], campos[2]))
                    .satisfies(e -> assertThat(e.getCampo()).isEqualTo(campos[3]));
        }
        assertThat(comunicados(p)).isZero();
        assertThat(acoes()).noneMatch(a -> a.startsWith("EMITIR_ALERTA"));
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> alertas.emitir(UUID.randomUUID(), "T", "M", "O"));
    }

    @Test
    void distribuiPelaPreferenciaTentaOOutroCanalEReenviaSoParaQuemFalhouNosDois() {
        long sms = morador(area, "SMS"), whats = morador(area, "WHATSAPP"), ambos = morador(area, "AMBOS");
        long inativo = morador(area, "SMS");
        jdbc.update("UPDATE moradores SET ativa = false WHERE id = ?", inativo);
        morador(novaArea(), "SMS");
        var p = confirmada();

        doReturn(false).when(envio).enviar(eq(SMS), anyString(), anyString());
        var primeiro = alertas.emitir(p, "Alerta fictício", "Água subindo", "Saia pela rua alta");
        assertThat(primeiro.getDestinatarios()).isEqualTo(3); // inativo e outra área ficam fora (regra 2 / RF001)
        assertThat(primeiro.getFalhas()).isZero();
        verify(envio).enviar(eq(SMS), eq(celular(sms)), contains("Protocolo: " + p));
        verify(envio).enviar(eq(WHATSAPP), eq(celular(sms)), anyString()); // alternativa automática (regra 3)
        verify(envio, never()).enviar(eq(SMS), eq(celular(whats)), anyString());
        verify(envio).enviar(eq(SMS), eq(celular(ambos)), anyString());
        verify(envio).enviar(eq(WHATSAPP), eq(celular(ambos)), anyString());

        Mockito.reset(envio);
        doReturn(false).when(envio).enviar(any(CanalCredenciais.class), eq(celular(sms)), anyString());
        var segundo = alertas.emitir(p, "Nova emissão", "Ainda subindo", "Mantenha distância"); // regra 6: nova emissão manual
        assertThat(segundo.getFalhas()).isEqualTo(1);
        assertThat(segundo.isReenviavel()).isTrue();
        assertThat(jdbc.queryForObject("SELECT entregue FROM destinatarios_comunicado WHERE comunicado_id = ? AND morador_id = ?", Boolean.class, segundo.getId(), sms)).isFalse();

        Mockito.reset(envio);
        var reenviado = alertas.reenviar(p, segundo.getId());
        assertThat(reenviado.getFalhas()).isZero();
        assertThat(reenviado.isReenviavel()).isFalse();
        verify(envio, times(1)).enviar(any(), anyString(), anyString()); // só o SMS de quem falhou; ninguém recebe de novo
        verify(envio).enviar(SMS, celular(sms), "Nova emissão\nAinda subindo\nOrientações: Mantenha distância\nProtocolo: " + p);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM destinatarios_comunicado WHERE entregue AND comunicado_id = ?", Long.class, segundo.getId())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT area_id FROM comunicados_ocorrencia WHERE id = ?", Long.class, segundo.getId())).isEqualTo(area);
        assertThat(acoes()).filteredOn(a -> a.startsWith("EMITIR_ALERTA")).containsExactly("EMITIR_ALERTA #" + p, "EMITIR_ALERTA #" + p);
        assertThat(acoes()).filteredOn(a -> a.startsWith("REENVIAR_ALERTA")).containsExactly("REENVIAR_ALERTA #" + p + " alerta " + segundo.getId())
                .allSatisfy(a -> assertThat(a).doesNotContain(celular(sms)));
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> alertas.reenviar(confirmada(), segundo.getId()));
    }

    @Test
    void avisoDeAlteracaoVaiParaAUniaoDosDestinatariosAnterioresSemDesativadosESemJustificativa() {
        String cpfMudou = novoMorador(area);
        long mudou = idDoMorador(cpfMudou), desativado = morador(area, "SMS");
        var semAlerta = confirmada();
        service.verificar(semAlerta, SituacaoOcorrencia.NAO_CONFIRMADA, "Sem alerta, sem aviso"); // RF011 regra 2
        assertThat(comunicados(semAlerta)).isZero();

        var p = confirmada();
        alertas.emitir(p, "Primeiro", "M", "O");
        long outraArea = novaArea();
        jdbc.update("UPDATE residencias SET area_id = ? WHERE id = (SELECT residencia_id FROM moradores WHERE id = ?)", outraArea, mudou);
        long chegou = morador(area, "WHATSAPP");
        alertas.emitir(p, "Segundo", "M", "O");
        jdbc.update("UPDATE moradores SET ativa = false WHERE id = ?", desativado);

        service.verificar(p, SituacaoOcorrencia.NAO_CONFIRMADA, "Justificativa interna fictícia", "Volte para casa");
        var aviso = alertas.daOcorrencia(p).get(0);
        assertThat(aviso.getTipo()).isEqualTo(TipoComunicado.ALTERACAO);
        assertThat(aviso.getTitulo()).isEqualTo("Alteração da ocorrência " + p);
        assertThat(aviso.getMensagem()).isEqualTo("Situação alterada de Confirmada para Não confirmada.").doesNotContain("Justificativa");
        assertThat(aviso.getOrientacoes()).isEqualTo("Volte para casa");
        assertThat(jdbc.queryForList("SELECT morador_id FROM destinatarios_comunicado WHERE comunicado_id = ? ORDER BY morador_id", Long.class, aviso.getId()))
                .containsExactly(mudou, chegou);
        assertThat(alertas.daOcorrencia(p)).extracting(Comunicado::getTitulo).containsExactly(aviso.getTitulo(), "Segundo", "Primeiro");
        assertThatExceptionOfType(ErroOcorrencia.class).isThrownBy(() -> alertas.emitir(p, "T", "M", "O")); // não está mais Confirmada
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> alertas.reenviar(p, aviso.getId())); // só alerta é reenviável

        // Quem mudou de área continua vendo o alerta antigo e o aviso, mas não a emissão feita depois da mudança.
        entrarComoMorador(cpfMudou);
        assertThat(alertas.doMorador()).extracting(AlertaDoMorador::titulo).containsExactly(aviso.getTitulo(), "Primeiro");
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> alertas.emitir(p, "T", "M", "O"));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> alertas.daOcorrencia(p));
    }

    @Test
    void soRepresentanteEmiteEReenviaEmQualquerArea() {
        var p = confirmada();
        autenticar(administrador(), "ADMINISTRADOR");
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> alertas.emitir(p, "T", "M", "O"));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> alertas.doMorador());
        SecurityContextHolder.clearContext();
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> alertas.reenviar(p, 1L));
        var outro = novaConta(true);
        autenticar(acesso.autenticar(outro.getLogin(), DEFINITIVA).orElseThrow(), "REPRESENTANTE");
        assertThat(alertas.emitir(p, "T", "M", "O").getRepresentanteNome()).isEqualTo(outro.getNomeCompleto());
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> alertas.doMorador());
    }

    @Test
    void envioSimuladoFalhaSoNosCanaisConfigurados() {
        var simulado = new EnvioAlertasSimulado(Set.of(SMS));
        assertThat(simulado.enviar(SMS, "81900000001", "texto fictício")).isFalse();
        assertThat(simulado.enviar(WHATSAPP, "81900000001", "texto fictício")).isTrue();
    }

    private UUID confirmada() {
        var p = service.registrar(dados(UUID.randomUUID())).getProtocolo();
        service.verificar(p, SituacaoOcorrencia.CONFIRMADA, "Vistoria fictícia");
        return p;
    }

    private long morador(long areaResidencia, String preferencia) {
        long id = idDoMorador(novoMorador(areaResidencia));
        jdbc.update("UPDATE moradores SET preferencia_alertas = ? WHERE id = ?", preferencia, id);
        return id;
    }

    private String celular(long morador) {
        return jdbc.queryForObject("SELECT celular FROM moradores WHERE id = ?", String.class, morador);
    }

    private long comunicados(UUID p) {
        return jdbc.queryForObject("SELECT count(*) FROM comunicados_ocorrencia c JOIN ocorrencias o ON o.id = c.ocorrencia_id WHERE o.protocolo = ?", Long.class, p);
    }
}
