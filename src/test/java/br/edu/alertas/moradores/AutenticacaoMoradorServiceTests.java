package br.edu.alertas.moradores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class AutenticacaoMoradorServiceTests {
    private static final Instant AGORA = Instant.parse("2026-11-22T12:00:00Z");
    private static final String CPF = "52998224725";
    private static final String TEMPORARIA = "temporaria-ficticia";
    private static final String DEFINITIVA = "definitiva-ficticia";

    @Autowired AutenticacaoMoradorService autenticacao;
    @Autowired MoradorRepository repository;
    @Autowired br.edu.alertas.residencias.ResidenciaRepository residencias;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired TransactionTemplate transacao;
    @MockitoBean Clock clock;
    private long area;
    private long residencia;
    private long morador;

    @BeforeEach
    void preparar() {
        relogio(AGORA);
        area = jdbc.queryForObject("INSERT INTO areas_risco(nome, descricao_localizacao_limites) VALUES (?, 'Limites') RETURNING id", Long.class, "Área acesso " + UUID.randomUUID());
        residencia = jdbc.queryForObject("INSERT INTO residencias(rua_logradouro, numero, bairro, municipio, area_id) VALUES ('Rua Fictícia', '1', 'Bairro', 'Município', ?) RETURNING id", Long.class, area);
        morador = jdbc.queryForObject("""
                INSERT INTO moradores(nome_completo, cpf, celular, residencia_id, canal_credenciais, preferencia_alertas, ativa,
                    senha_hash, senha_temporaria_expira_em, credenciais_pendentes)
                VALUES ('Pessoa Fictícia', ?, '81900000001', ?, 'SMS', 'SMS', true, ?, ?, false) RETURNING id""", Long.class,
                CPF, residencia, encoder.encode(TEMPORARIA), Timestamp.from(AGORA.plus(Duration.ofHours(24))));
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM registros_auditoria WHERE responsavel_tipo = 'MORADOR' AND responsavel_id = ?", morador);
        jdbc.update("DELETE FROM moradores WHERE residencia_id = ?", residencia);
        jdbc.update("DELETE FROM residencias WHERE id = ?", residencia);
        jdbc.update("DELETE FROM areas_risco WHERE id = ?", area);
    }

    @Test
    void temporariaExigeTrocaEDepoisSoADefinitivaEntra() {
        AcessoMorador temporario = autenticacao.autenticar(CPF, TEMPORARIA).orElseThrow();
        assertThat(temporario.trocaSenhaObrigatoria()).isTrue();
        assertThat(temporario.getName()).doesNotContain(CPF);
        assertThatIllegalArgumentException().isThrownBy(() -> autenticacao.trocarSenha(temporario, "1234567"));
        autenticacao.trocarSenha(temporario, "12345678");
        // Decisão de Pedro (22/11/2026): a primeira troca é auditada em nome do morador, sem senha nem hash.
        String hash = jdbc.queryForObject("SELECT senha_hash FROM moradores WHERE id = ?", String.class, morador);
        assertThat(jdbc.queryForList("SELECT acao || '|' || responsavel_id FROM registros_auditoria WHERE responsavel_tipo = 'MORADOR' AND responsavel_id = ?", String.class, morador))
                .containsExactly("DEFINIR_SENHA_DEFINITIVA_MORADOR #" + morador + "|" + morador)
                .allSatisfy(linha -> assertThat(linha).doesNotContain("12345678", TEMPORARIA, hash));

        assertThat(autenticacao.validarSessao(temporario)).isFalse();
        assertThat(autenticacao.autenticar(CPF, TEMPORARIA)).isEmpty();
        AcessoMorador definitivo = autenticacao.autenticar(CPF, "12345678").orElseThrow();
        assertThat(definitivo.trocaSenhaObrigatoria()).isFalse();
        assertThat(autenticacao.validarSessao(definitivo)).isTrue();
        assertThatIllegalStateException().isThrownBy(() -> autenticacao.trocarSenha(definitivo, DEFINITIVA));
        // A senha definitiva não vence em 24 h (RF005 regra 4 vale só para a temporária).
        relogio(AGORA.plus(Duration.ofDays(30)));
        assertThat(autenticacao.autenticar(CPF, "12345678")).isPresent();
    }

    @Test
    void temporariaVenceExatamenteEm24Horas() {
        relogio(AGORA.plus(Duration.ofHours(24)).minusNanos(1000));
        AcessoMorador acesso = autenticacao.autenticar(CPF, TEMPORARIA).orElseThrow();
        relogio(AGORA.plus(Duration.ofHours(24)));
        assertThat(autenticacao.validarSessao(acesso)).isFalse();
        assertThat(autenticacao.autenticar(CPF, TEMPORARIA)).isEmpty();
        assertThatIllegalStateException().isThrownBy(() -> autenticacao.trocarSenha(acesso, DEFINITIVA));
        assertThat(falhas()).isZero(); // vencida não conta como erro de senha
    }

    @Test
    void cincoFalhasConsecutivasBloqueiamQuinzeMinutosInclusiveSenhaCorreta() {
        for (int i = 0; i < 4; i++) { assertThat(autenticacao.autenticar(CPF, "errada-" + i)).isEmpty(); }
        assertThat(autenticacao.autenticar(CPF, TEMPORARIA)).isPresent();
        assertThat(falhas()).as("sucesso zera a sequência").isZero();

        for (int i = 0; i < 5; i++) { assertThat(autenticacao.autenticar(CPF, "errada-" + i)).isEmpty(); }
        assertThat(falhas()).isEqualTo(5);
        Instant fim = AGORA.plus(Duration.ofMinutes(15));
        assertThat(jdbc.queryForObject("SELECT bloqueado_ate FROM moradores WHERE id = ?", Timestamp.class, morador).toInstant()).isEqualTo(fim);
        relogio(fim.minusNanos(1000));
        assertThat(autenticacao.autenticar(CPF, TEMPORARIA)).isEmpty();
        assertThat(autenticacao.autenticar(CPF, "errada")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT bloqueado_ate FROM moradores WHERE id = ?", Timestamp.class, morador).toInstant())
                .as("tentativa no bloqueio não estende o prazo").isEqualTo(fim);
        relogio(fim);
        assertThat(autenticacao.autenticar(CPF, TEMPORARIA)).isPresent();
        assertThat(falhas()).isZero();
    }

    @Test
    void contaDesativadaCpfInexistenteOuSemSenhaNaoEntram() {
        assertThat(autenticacao.autenticar("11144477735", TEMPORARIA)).isEmpty();
        assertThat(autenticacao.autenticar(null, null)).isEmpty();
        AcessoMorador acesso = autenticacao.autenticar(CPF, TEMPORARIA).orElseThrow();
        jdbc.update("UPDATE moradores SET ativa = false WHERE id = ?", morador);
        assertThat(autenticacao.autenticar(CPF, TEMPORARIA)).isEmpty();
        assertThat(falhas()).isZero();
        assertThat(autenticacao.validarSessao(acesso)).isFalse();

        jdbc.update("UPDATE moradores SET ativa = true, senha_hash = NULL, senha_temporaria_expira_em = NULL WHERE id = ?", morador);
        assertThat(autenticacao.autenticar(CPF, "")).isEmpty();
    }

    @Test
    void desativacaoReenvioOuTrocaDeCpfRevogamSessaoAberta() {
        AcessoMorador acesso = autenticacao.autenticar(CPF, TEMPORARIA).orElseThrow();
        alterar(m -> m.emitirSenhaTemporaria(encoder.encode("nova-temporaria"), AGORA.plus(Duration.ofHours(24))));
        assertThat(autenticacao.validarSessao(acesso)).as("reenvio emite nova temporária").isFalse();

        AcessoMorador novo = autenticacao.autenticar(CPF, "nova-temporaria").orElseThrow();
        alterar(m -> { m.desativar(); m.reativar(); });
        assertThat(autenticacao.validarSessao(novo)).as("reativação não restaura sessão").isFalse();

        AcessoMorador outro = autenticacao.autenticar(CPF, "nova-temporaria").orElseThrow();
        alterar(m -> m.alterar(m.getNomeCompleto(), "11144477735", m.getCelular(), residencias.findById(residencia).orElseThrow()));
        assertThat(autenticacao.validarSessao(outro)).isFalse();
    }

    private void alterar(java.util.function.Consumer<Morador> mudanca) {
        transacao.executeWithoutResult(status -> mudanca.accept(repository.findById(morador).orElseThrow()));
    }

    private int falhas() {
        return jdbc.queryForObject("SELECT falhas_login FROM moradores WHERE id = ?", Integer.class, morador);
    }

    private void relogio(Instant instante) {
        when(clock.instant()).thenReturn(instante);
    }
}
