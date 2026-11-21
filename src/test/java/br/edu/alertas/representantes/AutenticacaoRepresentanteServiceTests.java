package br.edu.alertas.representantes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class AutenticacaoRepresentanteServiceTests {
    private static final Instant AGORA = Instant.parse("2026-11-21T12:00:00Z");
    private static final String TEMPORARIA = "temporaria-teste";
    private static final String DEFINITIVA = "definitiva-teste";

    @Autowired AutenticacaoRepresentanteService autenticacao;
    @Autowired RepresentanteService representantes;
    @Autowired RepresentanteRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean Clock clock;

    private final List<Long> criados = new ArrayList<>();

    @BeforeEach
    void fixaHorario() {
        when(clock.instant()).thenReturn(AGORA);
    }

    @AfterEach
    void removeSomenteFixturesDesteTeste() {
        criados.forEach(id -> jdbc.update("DELETE FROM representantes WHERE id = ?", id));
    }

    @Test
    void autenticaTemporariaEForcaTrocaAntesDeEmitirAcessoDefinitivo() {
        Representante conta = cadastrar();
        AcessoRepresentante temporario = entrar(conta, TEMPORARIA);
        assertThat(temporario.id()).isEqualTo(conta.getId());
        assertThat(temporario.getName()).isEqualTo(conta.getLogin());
        assertThat(temporario.trocaSenhaObrigatoria()).isTrue();
        assertThat(autenticacao.validarSessao(temporario)).isTrue();

        autenticacao.trocarSenha(temporario, DEFINITIVA);

        assertThat(autenticacao.validarSessao(temporario)).isFalse();
        assertThat(autenticacao.autenticar(conta.getLogin(), TEMPORARIA)).isEmpty();
        AcessoRepresentante definitivo = entrar(conta, DEFINITIVA);
        assertThat(definitivo.trocaSenhaObrigatoria()).isFalse();
        assertThat(definitivo.versaoCredencial()).isGreaterThan(temporario.versaoCredencial());
        assertThat(autenticacao.validarSessao(definitivo)).isTrue();
        assertThat(repository.findById(conta.getId()).orElseThrow().isTrocaSenhaObrigatoria()).isFalse();
        assertThat(encoder.matches(DEFINITIVA, hash(conta.getId()))).isTrue();
        assertThatIllegalStateException().isThrownBy(() -> autenticacao.trocarSenha(definitivo, "outrasenha"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1234567", "😀😀😀😀😀😀😀"})
    void trocaInvalidaNaoConsomeSessaoNemMudaHash(String curta) {
        Representante conta = cadastrar();
        AcessoRepresentante acesso = entrar(conta, TEMPORARIA);
        String anterior = hash(conta.getId());

        assertThatIllegalArgumentException().isThrownBy(() -> autenticacao.trocarSenha(acesso, curta));

        assertThat(hash(conta.getId())).isEqualTo(anterior);
        assertThat(autenticacao.validarSessao(acesso)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("senhasSemRestricoesAdicionais")
    void primeiraTrocaAceitaMinimoUnicodeEspacosLongaEReusoSemInventarPolitica(String senha) {
        Representante conta = cadastrar();
        autenticacao.trocarSenha(entrar(conta, TEMPORARIA), senha);

        assertThat(entrar(conta, senha).trocaSenhaObrigatoria()).isFalse();
        assertThat(encoder.matches(senha, hash(conta.getId()))).isTrue();
        if (senha.endsWith("fim-A")) {
            assertThat(autenticacao.autenticar(conta.getLogin(), senha.replace("fim-A", "fim-B"))).isEmpty();
        }
    }

    static Stream<String> senhasSemRestricoesAdicionais() {
        return Stream.of("aaaaaaaa", "        ", "😀😀😀😀😀😀😀😀", TEMPORARIA,
                "á😀".repeat(100) + "fim-A");
    }

    @Test
    void falhasConfirmamNoBancoQuintaBloqueiaEExpiraExatamenteEmQuinzeMinutos() {
        Representante conta = cadastrar();
        for (int tentativa = 1; tentativa <= 5; tentativa++) {
            assertThat(autenticacao.autenticar(conta.getLogin(), "incorreta")).isEmpty();
            assertThat(falhas(conta.getId())).isEqualTo(tentativa);
        }
        Instant limite = AGORA.plus(Duration.ofMinutes(15));
        assertThat(bloqueadoAte(conta.getId())).isEqualTo(limite);

        when(clock.instant()).thenReturn(limite.minusMillis(1));
        assertThat(autenticacao.autenticar(conta.getLogin(), TEMPORARIA)).isEmpty();
        assertThat(autenticacao.autenticar(conta.getLogin(), "incorreta")).isEmpty();
        assertThat(bloqueadoAte(conta.getId())).isEqualTo(limite);

        when(clock.instant()).thenReturn(limite);
        assertThat(autenticacao.autenticar(conta.getLogin(), TEMPORARIA)).isPresent();
        assertThat(falhas(conta.getId())).isZero();
    }

    @Test
    void sucessoInterrompeSequenciaDeFalhasEExpiracaoIniciaNovaContagem() {
        Representante conta = cadastrar();
        errar(conta, 4);
        entrar(conta, TEMPORARIA);
        assertThat(falhas(conta.getId())).isZero();

        errar(conta, 4);
        assertThat(autenticacao.autenticar(conta.getLogin(), TEMPORARIA)).isPresent();
        errar(conta, 5);
        when(clock.instant()).thenReturn(AGORA.plus(Duration.ofMinutes(15)));
        errar(conta, 1);
        assertThat(falhas(conta.getId())).isEqualTo(1);
        assertThat(autenticacao.autenticar(conta.getLogin(), TEMPORARIA)).isPresent();
    }

    @Test
    void falhasConcorrentesNaoPerdemContagem() throws Exception {
        Representante conta = cadastrar();
        var prontas = new CountDownLatch(5);
        var iniciar = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(5)) {
            var tarefas = Stream.generate(() -> executor.submit(() -> {
                prontas.countDown();
                if (!iniciar.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Tentativas não iniciadas a tempo.");
                }
                return autenticacao.autenticar(conta.getLogin(), "incorreta");
            })).limit(5).toList();
            assertThat(prontas.await(10, TimeUnit.SECONDS)).isTrue();
            iniciar.countDown();
            for (var tarefa : tarefas) {
                assertThat(tarefa.get(30, TimeUnit.SECONDS)).isEmpty();
            }
        }
        assertThat(falhas(conta.getId())).isEqualTo(5);
        assertThat(autenticacao.autenticar(conta.getLogin(), TEMPORARIA)).isEmpty();
    }

    @Test
    void temporariaVenceEmVinteEQuatroHorasInclusiveNaSessaoDaPrimeiraTroca() {
        Representante conta = cadastrar();
        Instant vencimento = AGORA.plus(Duration.ofHours(24));
        when(clock.instant()).thenReturn(vencimento.minusMillis(1));
        AcessoRepresentante acesso = entrar(conta, TEMPORARIA);
        assertThat(autenticacao.validarSessao(acesso)).isTrue();

        when(clock.instant()).thenReturn(vencimento);
        assertThat(autenticacao.autenticar(conta.getLogin(), TEMPORARIA)).isEmpty();
        assertThat(autenticacao.validarSessao(acesso)).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> autenticacao.trocarSenha(acesso, DEFINITIVA));
        assertThat(encoder.matches(TEMPORARIA, hash(conta.getId()))).isTrue();
    }

    @Test
    void senhaDefinitivaNaoHerdaVencimentoDaTemporaria() {
        Representante conta = cadastrar();
        autenticacao.trocarSenha(entrar(conta, TEMPORARIA), DEFINITIVA);
        AcessoRepresentante acesso = entrar(conta, DEFINITIVA);
        when(clock.instant()).thenReturn(AGORA.plus(Duration.ofDays(10)));

        assertThat(autenticacao.validarSessao(acesso)).isTrue();
        assertThat(autenticacao.autenticar(conta.getLogin(), DEFINITIVA)).isPresent();
    }

    @Test
    void desativacaoBloqueiaLoginEReativacaoNaoRecuperaSessaoAnterior() {
        Representante conta = cadastrar();
        AcessoRepresentante acesso = entrar(conta, TEMPORARIA);
        representantes.desativar(conta.getId());

        assertThat(autenticacao.autenticar(conta.getLogin(), TEMPORARIA)).isEmpty();
        assertThat(autenticacao.validarSessao(acesso)).isFalse();
        representantes.reativar(conta.getId());
        assertThat(autenticacao.validarSessao(acesso)).isFalse();
        assertThat(autenticacao.autenticar(conta.getLogin(), TEMPORARIA)).isPresent();
        assertThatIllegalStateException().isThrownBy(() -> autenticacao.trocarSenha(acesso, DEFINITIVA));
    }

    @Test
    void redefinicaoInvalidaSessaoESenhaAntigasSemLiberarBloqueioVigente() {
        Representante conta = cadastrar();
        AcessoRepresentante acesso = entrar(conta, TEMPORARIA);
        errar(conta, 5);
        Instant bloqueio = bloqueadoAte(conta.getId());
        representantes.redefinirSenhaTemporaria(conta.getId(), "temporaria-nova");

        assertThat(autenticacao.validarSessao(acesso)).isFalse();
        assertThat(autenticacao.autenticar(conta.getLogin(), "temporaria-nova")).isEmpty();
        assertThat(bloqueadoAte(conta.getId())).isEqualTo(bloqueio);

        when(clock.instant()).thenReturn(bloqueio);
        assertThat(autenticacao.autenticar(conta.getLogin(), TEMPORARIA)).isEmpty();
        assertThat(entrar(conta, "temporaria-nova").trocaSenhaObrigatoria()).isTrue();
    }

    @Test
    void correcaoDoLoginInvalidaSessaoMasPreservaIdentidade() {
        Representante conta = cadastrar();
        AcessoRepresentante acesso = entrar(conta, TEMPORARIA);
        String novoLogin = conta.getLogin() + "-novo";
        representantes.alterar(conta.getId(), conta.getNomeCompleto(), conta.getEntidadePublica(), novoLogin);

        assertThat(autenticacao.validarSessao(acesso)).isFalse();
        assertThat(autenticacao.autenticar(conta.getLogin(), TEMPORARIA)).isEmpty();
        assertThat(autenticacao.autenticar(novoLogin, TEMPORARIA).orElseThrow().id()).isEqualTo(conta.getId());
    }

    @Test
    void contaInexistenteOuPrincipalInvalidoNaoConcedeAcesso() {
        assertThat(autenticacao.autenticar("rf005-inexistente-" + UUID.randomUUID(), TEMPORARIA)).isEmpty();
        assertThat(autenticacao.validarSessao(new AcessoRepresentante(-1L, "inexistente", 0, true))).isFalse();
    }

    @Test
    void principalSerializavelNaoTransportaSenhaOuHash() throws Exception {
        Representante conta = cadastrar();
        AcessoRepresentante acesso = entrar(conta, TEMPORARIA);
        var bytes = new ByteArrayOutputStream();
        try (var stream = new ObjectOutputStream(bytes)) {
            stream.writeObject(acesso);
        }
        assertThat(bytes.toString(StandardCharsets.ISO_8859_1)).doesNotContain(TEMPORARIA, hash(conta.getId()));
        assertThat(acesso.toString()).doesNotContain(TEMPORARIA, hash(conta.getId()));
    }

    private Representante cadastrar() {
        Representante conta = representantes.cadastrar("Pessoa Exemplo RF005", "Defesa Civil",
                "rf005-servico-" + UUID.randomUUID(), TEMPORARIA);
        criados.add(conta.getId());
        return conta;
    }

    private AcessoRepresentante entrar(Representante conta, String senha) {
        return autenticacao.autenticar(conta.getLogin(), senha).orElseThrow();
    }

    private void errar(Representante conta, int vezes) {
        for (int tentativa = 0; tentativa < vezes; tentativa++) {
            assertThat(autenticacao.autenticar(conta.getLogin(), "incorreta")).isEmpty();
        }
    }

    private int falhas(Long id) {
        return jdbc.queryForObject("SELECT falhas_login FROM representantes WHERE id = ?", Integer.class, id);
    }

    private Instant bloqueadoAte(Long id) {
        return jdbc.queryForObject("SELECT bloqueado_ate FROM representantes WHERE id = ?",
                (rs, row) -> rs.getTimestamp(1).toInstant(), id);
    }

    private String hash(Long id) {
        return jdbc.queryForObject("SELECT senha_hash FROM representantes WHERE id = ?", String.class, id);
    }
}
