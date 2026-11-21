package br.edu.alertas.representantes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.lang.reflect.Modifier;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RepresentanteServiceTests {
    private static final Instant AGORA = Instant.parse("2026-11-21T12:00:00Z");
    private static final String SENHA = "aaaaaaaa";

    @Autowired
    RepresentanteService service;

    @Autowired
    RepresentanteRepository repository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void fixaHorarioDaOperacao() {
        when(clock.instant()).thenReturn(AGORA);
    }

    @ParameterizedTest
    @MethodSource("cadastrosIncompletos")
    void exigeNomeEntidadeELogin(String nome, String entidade, String login) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.cadastrar(nome, entidade, login, SENHA));
    }

    static Stream<Arguments> cadastrosIncompletos() {
        return Stream.of(null, "", " \t\n").flatMap(vazio -> Stream.of(
                Arguments.of(vazio, "Defesa Civil", "rf004-cadastro"),
                Arguments.of("Pessoa Exemplo", vazio, "rf004-cadastro"),
                Arguments.of("Pessoa Exemplo", "Defesa Civil", vazio)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1234567", "       ", "😀😀😀😀😀😀😀"})
    void rejeitaSenhaComMenosDeOitoCaracteres(String senha) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.cadastrar("Pessoa Exemplo", "Defesa Civil", "rf004-senha", senha));
    }

    @ParameterizedTest
    @ValueSource(strings = {"aaaaaaaa", "        ", "😀😀😀😀😀😀😀😀"})
    void aceitaOitoCaracteresSemExigirComposicao(String senha) {
        Representante criado = service.cadastrar("Pessoa Exemplo", "Defesa Civil", "rf004-minimo", senha);
        entityManager.flush();

        assertThat(encoder.matches(senha, hash(criado.getId()))).isTrue();
    }

    @Test
    void naoImpoeMaximoNemTruncaSenhasLongasUnicode() {
        String prefixo = "á😀".repeat(100);
        String senha = prefixo + "A";
        Representante criado = service.cadastrar("Pessoa Exemplo", "Defesa Civil", "rf004-longa", senha);
        entityManager.flush();

        String salvo = hash(criado.getId());
        assertThat(encoder.matches(senha, salvo)).isTrue();
        assertThat(encoder.matches(prefixo + "B", salvo)).isFalse();
    }

    @Test
    void persisteHashComSalETemporariaDeVinteEQuatroHoras() {
        Representante primeiro = cadastrar("rf004-hash-1");
        Representante segundo = cadastrar("rf004-hash-2");
        entityManager.flush();
        entityManager.clear();

        Representante salvo = repository.findById(primeiro.getId()).orElseThrow();
        String primeiroHash = hash(primeiro.getId());
        assertThat(primeiroHash).isNotBlank().isNotEqualTo(SENHA).isNotEqualTo(hash(segundo.getId()));
        assertThat(encoder.matches(SENHA, primeiroHash)).isTrue();
        assertThat(encoder.matches("bbbbbbbb", primeiroHash)).isFalse();
        assertThat(salvo.isAtiva()).isTrue();
        assertThat(salvo.isTrocaSenhaObrigatoria()).isTrue();
        assertThat(salvo.getSenhaTemporariaExpiraEm()).isEqualTo(AGORA.plus(Duration.ofHours(24)));
        assertThat(salvo.toString()).doesNotContain(SENHA, primeiroHash);
    }

    @Test
    void naoExpoeSenhaOuHashEmGetterPublicoNemExclusaoNoServico() {
        assertThat(Arrays.stream(Representante.class.getMethods())
                .map(method -> method.getName()))
                .doesNotContain("getSenha", "getSenhaHash", "getSenhaOriginal", "getPassword", "getPasswordHash");
        assertThat(Arrays.stream(Representante.class.getDeclaredFields())
                .filter(field -> field.getName().toLowerCase().contains("senha"))
                .allMatch(field -> Modifier.isPrivate(field.getModifiers()))).isTrue();
        assertThat(Arrays.stream(RepresentanteService.class.getMethods())
                .map(method -> method.getName().toLowerCase()))
                .noneMatch(name -> name.startsWith("excluir") || name.startsWith("delet") || name.startsWith("remover"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejeitaLoginDuplicadoMesmoQuandoContaEstaDesativada(boolean ativa) {
        Representante existente = cadastrar("rf004-login-ocupado");
        if (!ativa) {
            service.desativar(existente.getId());
        }

        assertThatIllegalArgumentException().isThrownBy(() -> cadastrar("rf004-login-ocupado"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void correcaoNaoPodeUsarLoginDeOutraConta(boolean outraAtiva) {
        Representante original = cadastrar("rf004-login-original");
        Representante outra = cadastrar("rf004-login-outra");
        if (!outraAtiva) {
            service.desativar(outra.getId());
        }

        assertThatIllegalArgumentException().isThrownBy(() -> service.alterar(original.getId(),
                "Nome alterado", "Outra entidade", outra.getLogin()));

        assertThat(original.getNomeCompleto()).isEqualTo("Pessoa Exemplo");
        assertThat(original.getEntidadePublica()).isEqualTo("Defesa Civil");
        assertThat(original.getLogin()).isEqualTo("rf004-login-original");
    }

    @ParameterizedTest
    @MethodSource("cadastrosIncompletos")
    void alteracaoInvalidaPreservaTodosOsCampos(String nome, String entidade, String login) {
        Representante original = cadastrar("rf004-atomico");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.alterar(original.getId(), nome, entidade, login));

        assertThat(original.getNomeCompleto()).isEqualTo("Pessoa Exemplo");
        assertThat(original.getEntidadePublica()).isEqualTo("Defesa Civil");
        assertThat(original.getLogin()).isEqualTo("rf004-atomico");
    }

    @Test
    void permiteManterProprioLoginNaCorrecao() {
        Representante original = cadastrar("rf004-proprio-login");

        Representante alterado = service.alterar(original.getId(), "Nome corrigido", "Saúde", original.getLogin());
        entityManager.flush();
        entityManager.clear();

        Representante salvo = repository.findById(original.getId()).orElseThrow();
        assertThat(alterado.getId()).isEqualTo(original.getId());
        assertThat(salvo.getNomeCompleto()).isEqualTo("Nome corrigido");
        assertThat(salvo.getEntidadePublica()).isEqualTo("Saúde");
        assertThat(salvo.getLogin()).isEqualTo("rf004-proprio-login");
    }

    @Test
    void loginUsaIgualdadeExataSemNormalizacaoImplicita() {
        Representante primeiro = cadastrar("RF004-Login-Exato");
        Representante segundo = cadastrar("rf004-login-exato");
        entityManager.flush();

        assertThat(primeiro.getId()).isNotEqualTo(segundo.getId());
        assertThat(primeiro.getLogin()).isEqualTo("RF004-Login-Exato");
        assertThat(segundo.getLogin()).isEqualTo("rf004-login-exato");
    }

    @Test
    void desativacaoEReativacaoPreservamIdentidadeECredencial() {
        Representante original = cadastrar("rf004-situacao");
        entityManager.flush();
        String hashOriginal = hash(original.getId());
        Instant validadeOriginal = original.getSenhaTemporariaExpiraEm();

        Representante desativado = service.desativar(original.getId());
        assertThat(desativado.getId()).isEqualTo(original.getId());
        assertThat(desativado.isAtiva()).isFalse();
        entityManager.flush();
        entityManager.clear();
        assertThat(repository.findById(original.getId()).orElseThrow().isAtiva()).isFalse();

        Representante reativado = service.reativar(original.getId());
        entityManager.flush();
        assertThat(reativado.getId()).isEqualTo(original.getId());
        assertThat(reativado.isAtiva()).isTrue();
        assertThat(reativado.getLogin()).isEqualTo("rf004-situacao");
        assertThat(reativado.getSenhaTemporariaExpiraEm()).isEqualTo(validadeOriginal);
        assertThat(hash(original.getId())).isEqualTo(hashOriginal);
    }

    @Test
    void redefinicaoInvalidaSenhaAntigaERenovaTemporariaPorVinteEQuatroHoras() {
        Representante original = cadastrar("rf004-reset");
        entityManager.flush();
        String hashAnterior = hash(original.getId());
        Instant momentoDaRedefinicao = AGORA.plus(Duration.ofDays(2));
        when(clock.instant()).thenReturn(momentoDaRedefinicao);

        Representante redefinido = service.redefinirSenhaTemporaria(original.getId(), "bbbbbbbb");
        entityManager.flush();
        entityManager.clear();

        Representante salvo = repository.findById(original.getId()).orElseThrow();
        String hashNovo = hash(original.getId());
        assertThat(redefinido.getId()).isEqualTo(original.getId());
        assertThat(hashNovo).isNotEqualTo(hashAnterior);
        assertThat(encoder.matches(SENHA, hashNovo)).isFalse();
        assertThat(encoder.matches("bbbbbbbb", hashNovo)).isTrue();
        assertThat(salvo.isTrocaSenhaObrigatoria()).isTrue();
        assertThat(salvo.getSenhaTemporariaExpiraEm())
                .isEqualTo(momentoDaRedefinicao.plus(Duration.ofHours(24)));
    }

    @Test
    void senhaCurtaNaRedefinicaoPreservaCredencialEValidade() {
        Representante original = cadastrar("rf004-reset-invalido");
        entityManager.flush();
        String hashAnterior = hash(original.getId());
        Instant validadeAnterior = original.getSenhaTemporariaExpiraEm();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.redefinirSenhaTemporaria(original.getId(), "curta"));

        assertThat(original.getSenhaTemporariaExpiraEm()).isEqualTo(validadeAnterior);
        assertThat(hash(original.getId())).isEqualTo(hashAnterior);
    }

    @Test
    void pesquisaCombinaNomeOuLoginComEntidadeESituacao() {
        Representante alice = service.cadastrar("RF004 Busca Alice", "Defesa Civil", "rf004-busca-primeiro", SENHA);
        Representante bruno = service.cadastrar("RF004 Busca Bruno", "Saúde", "rf004-busca-alice", SENHA);
        Representante clara = service.cadastrar("RF004 Busca Clara", "Defesa Civil", "rf004-busca-clara", SENHA);
        service.cadastrar("RF004 Busca Dora", "Defesa Civil Regional", "rf004-busca-dora", SENHA);
        service.desativar(bruno.getId());
        service.desativar(clara.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(service.pesquisar("ALICE", null, null)).extracting(Representante::getId)
                .containsExactlyInAnyOrder(alice.getId(), bruno.getId());
        assertThat(service.pesquisar("alice", "defesa civil", true)).extracting(Representante::getId)
                .containsExactly(alice.getId());
        assertThat(service.pesquisar("RF004 Busca", "DEFESA CIVIL", false)).extracting(Representante::getId)
                .containsExactly(clara.getId());
        assertThat(service.pesquisar(null, "defesa civil", null)).extracting(Representante::getId)
                .containsExactlyInAnyOrder(alice.getId(), clara.getId());
        assertThat(service.pesquisar("sem-correspondencia-rf004", null, null)).isEmpty();
        assertThat(service.pesquisar("", "", null)).extracting(Representante::getId)
                .containsExactlyInAnyOrderElementsOf(
                        service.pesquisar(null, null, null).stream().map(Representante::getId).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_"})
    void pesquisaTrataCoringasComoTextoLiteral(String caractere) {
        Representante esperado = service.cadastrar("RF004" + caractere + "literal", "Defesa Civil", "rf004-literal", SENHA);
        service.cadastrar("RF004Xliteral", "Defesa Civil", "rf004-comum", SENHA);
        entityManager.flush();

        assertThat(service.pesquisar("RF004" + caractere + "literal", null, null))
                .extracting(Representante::getId).containsExactly(esperado.getId());
    }

    @Test
    void bancoImpedeDuplicidadeMesmoQuandoPrecheckDoServicoEhContornado() {
        Representante existente = cadastrar("rf004-unique-banco");
        entityManager.flush();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO representantes
                    (nome_completo, entidade_publica, login, senha_hash, ativa,
                     troca_senha_obrigatoria, senha_temporaria_expira_em)
                SELECT nome_completo, entidade_publica, login, senha_hash, ativa,
                       troca_senha_obrigatoria, senha_temporaria_expira_em
                  FROM representantes WHERE id = ?
                """, existente.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(error -> {
                    Throwable causa = ((DataIntegrityViolationException) error).getMostSpecificCause();
                    assertThat(causa).isInstanceOf(SQLException.class);
                    assertThat(((SQLException) causa).getSQLState()).isEqualTo("23505");
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void servicoConfirmaCadastroECorrecaoSemTransacaoExternaDoTeste() {
        Representante criado = service.cadastrar("Nome inicial", "Defesa Civil", "rf004-commit-proprio", SENHA);
        try {
            assertThat(repository.findById(criado.getId())).isPresent();
            service.alterar(criado.getId(), "Nome corrigido", "Saúde", "rf004-commit-corrigido");

            Representante salvo = repository.findById(criado.getId()).orElseThrow();
            assertThat(salvo.getId()).isEqualTo(criado.getId());
            assertThat(salvo.getNomeCompleto()).isEqualTo("Nome corrigido");
            assertThat(salvo.getEntidadePublica()).isEqualTo("Saúde");
            assertThat(salvo.getLogin()).isEqualTo("rf004-commit-corrigido");
        } finally {
            jdbc.update("DELETE FROM representantes WHERE id = ?", criado.getId());
        }
    }

    private Representante cadastrar(String login) {
        return service.cadastrar("Pessoa Exemplo", "Defesa Civil", login, SENHA);
    }

    private String hash(Long id) {
        return jdbc.queryForObject("SELECT senha_hash FROM representantes WHERE id = ?", String.class, id);
    }
}
