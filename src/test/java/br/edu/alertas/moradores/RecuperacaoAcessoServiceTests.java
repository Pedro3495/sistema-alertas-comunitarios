package br.edu.alertas.moradores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import br.edu.alertas.moradores.RecuperacaoAcessoService.ResultadoEnvio;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
class RecuperacaoAcessoServiceTests {
    private static final Instant AGORA = Instant.parse("2026-11-22T12:00:00Z");
    private static final String CPF = "52998224725";
    private static final String CELULAR = "81900000001";
    private static final String TEMPORARIA = "temporaria-ficticia";
    private static final String NOVA = "nova-senha-ficticia";

    @Autowired RecuperacaoAcessoService recuperacao;
    @Autowired AutenticacaoMoradorService autenticacao;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean Clock clock;
    @MockitoSpyBean EnvioCredenciais envio;
    private long area;
    private long residencia;
    private long morador;

    @BeforeEach
    void preparar() {
        relogio(AGORA);
        area = jdbc.queryForObject("INSERT INTO areas_risco(nome, descricao_localizacao_limites) VALUES (?, 'Limites') RETURNING id", Long.class, "Área recuperação " + UUID.randomUUID());
        residencia = jdbc.queryForObject("INSERT INTO residencias(rua_logradouro, numero, bairro, municipio, area_id) VALUES ('Rua Fictícia', '1', 'Bairro', 'Município', ?) RETURNING id", Long.class, area);
        morador = jdbc.queryForObject("""
                INSERT INTO moradores(nome_completo, cpf, celular, residencia_id, canal_credenciais, preferencia_alertas, ativa,
                    senha_hash, senha_temporaria_expira_em, credenciais_pendentes)
                VALUES ('Pessoa Fictícia', ?, ?, ?, 'WHATSAPP', 'SMS', true, ?, ?, false) RETURNING id""", Long.class,
                CPF, CELULAR, residencia, encoder.encode(TEMPORARIA), Timestamp.from(AGORA.plus(Duration.ofHours(24))));
    }

    @AfterEach
    void limpar() {
        reset(envio);
        jdbc.update("DELETE FROM registros_auditoria WHERE responsavel_tipo = 'MORADOR' AND responsavel_id = ?", morador);
        jdbc.update("DELETE FROM moradores WHERE residencia_id = ?", residencia);
        jdbc.update("DELETE FROM residencias WHERE id = ?", residencia);
        jdbc.update("DELETE FROM areas_risco WHERE id = ?", area);
    }

    @Test
    void codigoPeloCanalDeCredenciaisRedefineUmaVezESoCpfESenhaEntram() {
        // Temporária entregue que venceu sem uso: o RF007 permite criar nova senha (questão aberta do RF005).
        relogio(AGORA.plus(Duration.ofHours(25)));
        assertThat(autenticacao.autenticar(CPF, TEMPORARIA)).isEmpty();
        String codigo = solicitar();
        assertThat(codigo).matches("[0-9]{6}");
        assertThat(jdbc.queryForObject("SELECT codigo_recuperacao_hash FROM moradores WHERE id = ?", String.class, morador))
                .isNotEqualTo(codigo);
        assertThatIllegalArgumentException().isThrownBy(() -> recuperacao.redefinirSenha(CELULAR, codigo, "1234567"));

        assertThat(recuperacao.redefinirSenha(CELULAR, codigo, NOVA)).isTrue();
        assertThat(recuperacao.redefinirSenha(CELULAR, codigo, "outra-senha-ficticia")).as("uso único").isFalse();
        assertThat(autenticacao.autenticar(CPF, TEMPORARIA)).isEmpty();
        AcessoMorador acesso = autenticacao.autenticar(CPF, NOVA).orElseThrow();
        assertThat(acesso.trocaSenhaObrigatoria()).isFalse();
        assertThat(jdbc.queryForObject("SELECT codigo_recuperacao_hash IS NULL AND NOT credenciais_pendentes FROM moradores WHERE id = ?",
                Boolean.class, morador)).isTrue();
    }

    @Test
    void redefinicaoEncerraSessaoAbertaComSenhaAnterior() {
        AcessoMorador antigo = autenticacao.autenticar(CPF, TEMPORARIA).orElseThrow();
        autenticacao.trocarSenha(antigo, "definitiva-ficticia");
        AcessoMorador sessao = autenticacao.autenticar(CPF, "definitiva-ficticia").orElseThrow();
        assertThat(recuperacao.redefinirSenha(CELULAR, solicitar(), NOVA)).isTrue();
        assertThat(autenticacao.validarSessao(sessao)).isFalse();
    }

    @Test
    void codigoVenceExatamenteEmDezMinutos() {
        String codigo = solicitar();
        relogio(AGORA.plus(Duration.ofMinutes(10)).minusMillis(1));
        assertThat(recuperacao.redefinirSenha(CELULAR, "000000".equals(codigo) ? "111111" : "000000", NOVA)).isFalse();
        relogio(AGORA.plus(Duration.ofMinutes(10)));
        assertThat(recuperacao.redefinirSenha(CELULAR, codigo, NOVA)).isFalse();
        assertThat(autenticacao.autenticar(CPF, TEMPORARIA)).isPresent();
    }

    @Test
    void novaEmissaoInvalidaAAnterior() {
        String primeiro = solicitar();
        reset(envio);
        String segundo = solicitar();
        if (!primeiro.equals(segundo)) {
            assertThat(recuperacao.redefinirSenha(CELULAR, primeiro, NOVA)).isFalse();
        }
        assertThat(recuperacao.redefinirSenha(CELULAR, segundo, NOVA)).isTrue();
    }

    @Test
    void cincoErrosInvalidamOCodigoEQuatroNao() {
        String codigo = solicitar();
        String errado = "000000".equals(codigo) ? "111111" : "000000";
        for (int i = 0; i < 4; i++) { assertThat(recuperacao.redefinirSenha(CELULAR, errado, NOVA)).isFalse(); }
        assertThat(jdbc.queryForObject("SELECT falhas_codigo_recuperacao FROM moradores WHERE id = ?", Integer.class, morador)).isEqualTo(4);
        assertThat(recuperacao.redefinirSenha(CELULAR, codigo, NOVA)).isTrue();

        reset(envio);
        String outro = solicitar();
        String erradoOutro = "000000".equals(outro) ? "111111" : "000000";
        for (int i = 0; i < 5; i++) { assertThat(recuperacao.redefinirSenha(CELULAR, erradoOutro, "mais-uma-ficticia")).isFalse(); }
        assertThat(recuperacao.redefinirSenha(CELULAR, outro, "mais-uma-ficticia")).as("exige nova emissão").isFalse();
        assertThat(autenticacao.autenticar(CPF, NOVA)).isPresent();
    }

    @Test
    void falhaDeEnvioInformaSemTrocarCanalETelefoneDesconhecidoOuInativoNaoRecebe() {
        doReturn(false).when(envio).enviarCodigoRecuperacao(any(), any(), any());
        assertThat(recuperacao.solicitarCodigo(CELULAR)).isEqualTo(ResultadoEnvio.FALHA_ENVIO);
        doThrow(new IllegalStateException("provedor fora")).when(envio).enviarCodigoRecuperacao(any(), any(), any());
        assertThat(recuperacao.solicitarCodigo(CELULAR)).isEqualTo(ResultadoEnvio.FALHA_ENVIO);
        verify(envio, times(2)).enviarCodigoRecuperacao(eq(CanalCredenciais.WHATSAPP), eq(CELULAR), any());
        verify(envio, never()).enviarCodigoRecuperacao(eq(CanalCredenciais.SMS), any(), any());
        reset(envio);

        assertThat(recuperacao.solicitarCodigo("81900000099")).isEqualTo(ResultadoEnvio.SOLICITADO);
        assertThat(recuperacao.solicitarCodigo(null)).isEqualTo(ResultadoEnvio.SOLICITADO);
        assertThat(recuperacao.redefinirSenha("81900000099", "123456", NOVA)).isFalse();
        jdbc.update("UPDATE moradores SET ativa = false WHERE id = ?", morador);
        assertThat(recuperacao.solicitarCodigo(CELULAR)).isEqualTo(ResultadoEnvio.SOLICITADO);
        verifyNoInteractions(envio);
    }

    @Test
    void contaDesativadaDepoisDaEmissaoNaoRedefine() {
        String codigo = solicitar();
        jdbc.update("UPDATE moradores SET ativa = false WHERE id = ?", morador);
        assertThat(recuperacao.redefinirSenha(CELULAR, codigo, NOVA)).isFalse();
    }

    @Test
    void tresPedidosPorHoraEmJanelaMovelComRecusaIgualAoSucessoEAuditoriaSemSegredo() {
        String codigo = solicitar();
        relogio(AGORA.plus(Duration.ofMinutes(20)));
        reset(envio);
        solicitar();
        relogio(AGORA.plus(Duration.ofMinutes(40)));
        reset(envio);
        String terceiro = solicitar();
        reset(envio);
        relogio(AGORA.plus(Duration.ofMinutes(59)));
        assertThat(recuperacao.solicitarCodigo(CELULAR)).as("4º: mesma resposta de telefone desconhecido")
                .isEqualTo(recuperacao.solicitarCodigo("81900000099"));
        verifyNoInteractions(envio);

        relogio(AGORA.plus(Duration.ofHours(1)));
        String liberado = solicitar();
        assertThat(recuperacao.redefinirSenha(CELULAR, liberado, NOVA)).isTrue();

        List<String> acoes = jdbc.queryForList("SELECT acao FROM registros_auditoria WHERE responsavel_tipo = 'MORADOR' AND responsavel_id = ? ORDER BY id",
                String.class, morador);
        assertThat(acoes).containsExactly(RecuperacaoAcessoService.PEDIDO, RecuperacaoAcessoService.PEDIDO, RecuperacaoAcessoService.PEDIDO,
                RecuperacaoAcessoService.RECUSA_LIMITE, RecuperacaoAcessoService.PEDIDO,
                RecuperacaoAcessoService.REDEFINICAO);
        assertThat(String.join(" ", acoes)).doesNotContain(codigo, terceiro, liberado, NOVA);
    }

    private String solicitar() {
        assertThat(recuperacao.solicitarCodigo(CELULAR)).isEqualTo(ResultadoEnvio.SOLICITADO);
        var codigos = ArgumentCaptor.forClass(String.class);
        verify(envio).enviarCodigoRecuperacao(eq(CanalCredenciais.WHATSAPP), eq(CELULAR), codigos.capture());
        return codigos.getValue();
    }

    private void relogio(Instant instante) {
        when(clock.instant()).thenReturn(instante);
    }
}
