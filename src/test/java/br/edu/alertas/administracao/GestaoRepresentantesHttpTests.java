package br.edu.alertas.administracao;

import br.edu.alertas.representantes.AutenticacaoRepresentanteService;
import br.edu.alertas.representantes.Representante;
import br.edu.alertas.representantes.RepresentanteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GestaoRepresentantesHttpTests {
    private static final Instant AGORA = Instant.parse("2026-11-21T12:00:00Z");
    private static final String SENHA_ADMIN = "senha-admin-ficticia";
    private static final String TEMPORARIA = "temporaria-representante";
    private static final String DEFINITIVA = "definitiva-representante";
    private static final Pattern INPUT = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    @LocalServerPort int port;
    @Autowired AdministradorService administradores;
    @Autowired RepresentanteService representantes;
    @Autowired AutenticacaoRepresentanteService autenticacao;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean Clock clock;

    private final List<Long> criados = new ArrayList<>();
    private final List<String> loginsCriadosPorHttp = new ArrayList<>();
    private String loginAdmin;
    private boolean adminCriado;

    @BeforeEach
    void configuraSomenteAdministradorFicticioDesteTeste() {
        when(clock.instant()).thenReturn(AGORA);
        loginAdmin = " admin-http-" + UUID.randomUUID() + " ";
        administradores.criarPrimeiro(loginAdmin, SENHA_ADMIN);
        adminCriado = true;
    }

    @AfterEach
    void removeSomenteFixturesCriadasPorEsteTeste() {
        if (adminCriado) {
            jdbc.update("DELETE FROM registros_auditoria WHERE responsavel_tipo = 'ADMINISTRADOR' AND responsavel_id = 1");
        }
        criados.forEach(id -> jdbc.update("DELETE FROM registros_auditoria WHERE responsavel_tipo = 'REPRESENTANTE' AND responsavel_id = ?", id));
        loginsCriadosPorHttp.forEach(login -> jdbc.update("DELETE FROM registros_auditoria WHERE responsavel_tipo = 'REPRESENTANTE' AND responsavel_id IN (SELECT id FROM representantes WHERE login = ?)", login));
        criados.forEach(id -> jdbc.update("DELETE FROM representantes WHERE id = ?", id));
        loginsCriadosPorHttp.forEach(login -> jdbc.update("DELETE FROM representantes WHERE login = ?", login));
        if (adminCriado) {
            jdbc.update("DELETE FROM administradores WHERE id = 1 AND login = ?", loginAdmin);
        }
    }

    @Test
    void caixaDeEnvioSimuladoDesligadaPorPadraoRespondeNaoEncontrada() throws Exception {
        assertThat(adminAutenticado().get("/admin/simulador/mensagens").statusCode()).isEqualTo(404);
        assertThat(adminAutenticado().get("/admin/representantes").body()).doesNotContain("/admin/simulador/mensagens");
        caminho(new Navegador().get("/admin/simulador/mensagens"), "/admin/entrar");
    }

    @Test
    void percorreGestaoCompletaMantendoContaHistoricoESegredosForaDaAuditoria() throws Exception {
        Navegador admin = adminAutenticado();
        String login = "gestao-" + UUID.randomUUID();
        loginsCriadosPorHttp.add(login);
        String formulario = admin.get("/admin/representantes/novo").body();
        caminho(admin.postComCsrf("/admin/representantes/novo", formulario,
                cadastro("Pessoa Fictícia Gestão", "Entidade A", login, TEMPORARIA)), "/admin/representantes");
        Long id = idDoLogin(login);
        criados.add(id);
        assertThat(acoes()).anySatisfy(acao -> assertThat(acao).contains("CADASTRAR_REPRESENTANTE", id.toString()));

        Navegador representante = new Navegador();
        caminho(representante.entrar("/entrar", login, TEMPORARIA), "/trocar-senha");
        caminho(representante.postComCsrf("/trocar-senha", representante.get("/trocar-senha").body(),
                Map.of("novaSenha", DEFINITIVA)), "/entrar");
        caminho(representante.entrar("/entrar", login, DEFINITIVA), "/representante");

        String novoLogin = login + "-corrigido";
        caminho(admin.postComCsrf(editar(id), admin.get(editar(id)).body(),
                Map.of("nomeCompleto", "Pessoa Corrigida", "entidadePublica", "Entidade B", "login", novoLogin)),
                "/admin/representantes");
        assertThat(idDoLogin(novoLogin)).isEqualTo(id);
        caminho(representante.get("/representante"), "/entrar");
        assertThat(autenticacao.autenticar(login, DEFINITIVA)).isEmpty();

        caminho(admin.postComCsrf(operacao(id, "desativar"), admin.get(editar(id)).body(),
                Map.of("confirmacao", "true")), "/admin/representantes");
        assertThat(ativa(id)).isFalse();
        assertThat(autenticacao.autenticar(novoLogin, DEFINITIVA)).isEmpty();
        caminho(admin.postComCsrf(operacao(id, "reativar"), admin.get(editar(id)).body(),
                Map.of("confirmacao", "true")), "/admin/representantes");
        assertThat(ativa(id)).isTrue();
        caminho(representante.entrar("/entrar", novoLogin, DEFINITIVA), "/representante");

        String novaTemporaria = "temporaria-reset-ficticia";
        caminho(admin.postComCsrf(operacao(id, "senha-temporaria"), admin.get(editar(id)).body(),
                Map.of("confirmacao", "true", "senhaTemporaria", novaTemporaria)), "/admin/representantes");
        caminho(representante.get("/representante"), "/entrar");
        assertThat(autenticacao.autenticar(novoLogin, DEFINITIVA)).isEmpty();
        assertThat(autenticacao.autenticar(novoLogin, novaTemporaria)).get()
                .satisfies(acesso -> assertThat(acesso.trocaSenhaObrigatoria()).isTrue());
        assertThat(idDoLogin(novoLogin)).isEqualTo(id);

        assertThat(acoes()).hasSize(5);
        for (String prefixo : List.of("CADASTRAR_REPRESENTANTE", "ALTERAR_REPRESENTANTE",
                "DESATIVAR_REPRESENTANTE", "REATIVAR_REPRESENTANTE", "REDEFINIR_SENHA_REPRESENTANTE")) {
            assertThat(acoes()).anySatisfy(acao -> assertThat(acao).contains(prefixo, id.toString()));
        }
        assertThat(jdbc.queryForList("SELECT DISTINCT responsavel_id FROM registros_auditoria WHERE responsavel_tipo = 'ADMINISTRADOR'", Long.class))
                .containsExactly(1L);
        String hash = jdbc.queryForObject("SELECT senha_hash FROM representantes WHERE id = ?", String.class, id);
        String pagina = admin.get(editar(id)).body();
        assertThat(pagina).doesNotContain(TEMPORARIA, DEFINITIVA, novaTemporaria, hash);
        assertThat(String.join("|", acoes())).doesNotContain(TEMPORARIA, DEFINITIVA, novaTemporaria, hash);
        assertThat(admin.get("/admin/auditoria").body()).doesNotContain(TEMPORARIA, DEFINITIVA, novaTemporaria, hash);
    }

    @Test
    void validacaoEDuplicidadePreservamCamposNaoSecretosSemGravarOperacao() throws Exception {
        Navegador admin = adminAutenticado();
        Representante existente = representante("Nome Existente", "Entidade Original", null);
        int antes = acoes().size();
        var invalido = admin.postComCsrf("/admin/representantes/novo", admin.get("/admin/representantes/novo").body(),
                cadastro("", "Entidade Preservada", "novo-login", "curta"));
        assertThat(invalido.statusCode()).isEqualTo(200);
        assertThat(invalido.body()).contains("Entidade Preservada", "novo-login").doesNotContain("value=\"curta\"");
        var duplicado = admin.postComCsrf("/admin/representantes/novo", invalido.body(),
                cadastro("Pessoa Duplicada", "Entidade Preservada", existente.getLogin(), TEMPORARIA));
        assertThat(duplicado.statusCode()).isEqualTo(200);
        assertThat(duplicado.body()).contains("Pessoa Duplicada", "Entidade Preservada").doesNotContain(TEMPORARIA);
        assertThat(acoes()).hasSize(antes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM representantes WHERE login = ?", Integer.class, existente.getLogin()))
                .isEqualTo(1);
    }

    @Test
    void pesquisaCombinaTermoEntidadeESituacaoSemLiberarExclusao() throws Exception {
        Navegador admin = adminAutenticado();
        Representante procurado = representante("Pessoa Procurada Única", "Entidade Alvo", "busca-" + UUID.randomUUID());
        Representante oculto = representante("Pessoa Fora Da Busca", "Outra Entidade", null);
        representantes.desativar(oculto.getId());
        var resposta = admin.get("/admin/representantes?" + query(Map.of("termo", procurado.getLogin(),
                "entidadePublica", "Entidade Alvo", "ativa", "true")));
        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).contains("Pessoa Procurada Única").doesNotContain("Pessoa Fora Da Busca");
        var porNome = admin.get("/admin/representantes?" + query(Map.of("termo", "Pessoa Fora", "ativa", "false")));
        assertThat(porNome.body()).contains("Pessoa Fora Da Busca").doesNotContain("Pessoa Procurada Única");
        String formulario = admin.get(editar(procurado.getId())).body();
        assertThat(admin.postComCsrf(operacao(procurado.getId(), "excluir"), formulario, Map.of()).statusCode())
                .isIn(403, 404, 405);
        assertThat(idDoLogin(procurado.getLogin())).isEqualTo(procurado.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"desativar", "senha-temporaria"})
    void operacoesSensiveisExigemConfirmacaoExplicita(String operacao) throws Exception {
        Navegador admin = adminAutenticado();
        Representante conta = representante("Pessoa Confirmacao", "Entidade", null);
        var resposta = admin.postComCsrf(operacao(conta.getId(), operacao), admin.get(editar(conta.getId())).body(),
                Map.of("senhaTemporaria", "outra-temporaria-ficticia"));
        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(ativa(conta.getId())).isTrue();
        assertThat(autenticacao.autenticar(conta.getLogin(), TEMPORARIA)).isPresent();
        assertThat(acoes()).isEmpty();
    }

    @Test
    void perfisComMesmoLoginNaoCruzamCredenciaisNemGanhamPermissoesDoOutro() throws Exception {
        Representante conta = representante("Pessoa Mesmo Login", "Entidade", loginAdmin);
        autenticacao.trocarSenha(autenticacao.autenticar(conta.getLogin(), TEMPORARIA).orElseThrow(), DEFINITIVA);
        Navegador navegador = new Navegador();
        var falhaAdmin = navegador.entrar("/admin/entrar", loginAdmin, DEFINITIVA);
        caminho(falhaAdmin, "/admin/entrar");
        assertThat(URI.create(falhaAdmin.headers().firstValue("location").orElseThrow()).getQuery()).isNotNull();
        var falhaRepresentante = navegador.entrar("/entrar", loginAdmin, SENHA_ADMIN);
        caminho(falhaRepresentante, "/entrar");
        assertThat(URI.create(falhaRepresentante.headers().firstValue("location").orElseThrow()).getQuery()).isNotNull();
        caminho(navegador.entrar("/entrar", loginAdmin, DEFINITIVA), "/representante");
        assertThat(navegador.get("/admin/representantes").statusCode()).isEqualTo(403);
        assertThat(navegador.get("/admin/auditoria").statusCode()).isEqualTo(403);
        assertThat(navegador.postComCsrf("/admin/representantes/novo", navegador.get("/representante").body(),
                cadastro("Tentativa", "Entidade", "forjado", TEMPORARIA)).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM representantes WHERE login = 'forjado'", Integer.class)).isZero();
        Navegador admin = adminAutenticado();
        assertThat(admin.get("/representante").statusCode()).isEqualTo(403);
        assertThat(admin.get("/areas").statusCode()).isEqualTo(403);
        assertThat(acoes()).isEmpty();
    }

    @Test
    void csrfProtegeLoginGestaoELogoutEGetNaoEncerraSessao() throws Exception {
        Navegador navegador = new Navegador();
        navegador.get("/admin/entrar");
        assertThat(navegador.post("/admin/entrar", Map.of("login", loginAdmin, "senha", SENHA_ADMIN)).statusCode())
                .isEqualTo(403);
        caminho(navegador.entrar("/admin/entrar", loginAdmin, SENHA_ADMIN), "/admin/representantes");
        assertThat(navegador.post("/admin/representantes/novo", cadastro("CSRF", "Entidade", "nao-gravar", TEMPORARIA))
                .statusCode()).isEqualTo(403);
        assertThat(navegador.post("/admin/representantes/novo", Map.of("_csrf", "invalido")).statusCode()).isEqualTo(403);
        assertThat(navegador.get("/admin/sair").statusCode()).isIn(403, 404, 405);
        assertThat(navegador.get("/admin/representantes").statusCode()).isEqualTo(200);
        assertThat(navegador.post("/admin/sair", Map.of()).statusCode()).isEqualTo(403);
        caminho(navegador.postComCsrf("/admin/sair", navegador.get("/admin/representantes").body(), Map.of()), "/admin/entrar");
        caminho(navegador.get("/admin/representantes"), "/admin/entrar");
        assertThat(acoes()).isEmpty();
    }

    @Test
    void recuperacaoLocalRevogaSessaoAdministrativaJaAberta() throws Exception {
        Navegador admin = adminAutenticado();
        administradores.redefinirSenhaLocal(loginAdmin, "nova-admin-ficticia");
        caminho(admin.get("/admin/representantes"), "/admin/entrar");
        caminho(new Navegador().entrar("/admin/entrar", loginAdmin, "nova-admin-ficticia"), "/admin/representantes");
        assertThat(admin.get("/admin/recuperar").statusCode()).isIn(302, 403, 404, 405);
    }

    @Test
    void auditoriaPermiteSomenteConsultaComFiltrosEIdentidadeEstavel() throws Exception {
        Navegador admin = adminAutenticado();
        String login = "auditado-" + UUID.randomUUID();
        loginsCriadosPorHttp.add(login);
        caminho(admin.postComCsrf("/admin/representantes/novo", admin.get("/admin/representantes/novo").body(),
                cadastro("Pessoa Auditada", "Entidade", login, TEMPORARIA)), "/admin/representantes");
        Long id = idDoLogin(login);
        criados.add(id);
        String filtro = query(Map.of("responsavel", "ADMINISTRADOR:1", "acao", "cadastrar_representante",
                "inicio", "2026-11-21T12:00", "fim", "2026-11-21T12:00"));
        var pagina = admin.get("/admin/auditoria?" + filtro);
        assertThat(pagina.statusCode()).isEqualTo(200);
        assertThat(pagina.body()).contains("CADASTRAR REPRESENTANTE", id.toString());
        var foraDoPeriodo = admin.get("/admin/auditoria?" + query(Map.of("inicio", "2026-11-21T12:01")));
        assertThat(foraDoPeriodo.body()).doesNotContain("CADASTRAR REPRESENTANTE");
        var erroPeriodo = admin.get("/admin/auditoria?" + query(Map.of("inicio", "2026-11-22T12:00", "fim", "2026-11-21T12:00")));
        assertThat(erroPeriodo.statusCode()).isEqualTo(200);
        assertThat(erroPeriodo.body()).contains("2026-11-22T12:00", "2026-11-21T12:00", "role=\"alert\"");
        assertThat(admin.get("/admin/auditoria?inicio=nao-e-data").statusCode()).isEqualTo(200);
        assertThat(admin.get("/admin/auditoria?responsavel=forjado").statusCode()).isEqualTo(200);

        Representante conta = representante("Pessoa Consulta", "Entidade", null);
        Navegador representante = new Navegador();
        caminho(representante.entrar("/entrar", conta.getLogin(), TEMPORARIA), "/trocar-senha");
        assertThat(representante.get("/auditoria").statusCode()).isEqualTo(403);
        autenticacao.trocarSenha(autenticacao.autenticar(conta.getLogin(), TEMPORARIA).orElseThrow(), DEFINITIVA);
        assertThat(representante.get("/entrar").statusCode()).isIn(302, 303);
        caminho(representante.entrar("/entrar", conta.getLogin(), DEFINITIVA), "/representante");
        assertThat(representante.get("/auditoria").statusCode()).isEqualTo(200);
        List<String> antes = acoes();
        String csrfAdmin = admin.get("/admin/representantes/novo").body();
        assertThat(admin.postComCsrf("/admin/auditoria", csrfAdmin, Map.of("acao", "forjada")).statusCode()).isIn(403, 404, 405);
        assertThat(admin.postComCsrf("/admin/auditoria/1/excluir", csrfAdmin, Map.of()).statusCode()).isIn(403, 404, 405);
        assertThat(representante.postComCsrf("/auditoria/1/editar", representante.get("/representante").body(),
                Map.of("acao", "forjada")).statusCode()).isIn(403, 404, 405);
        assertThat(acoes()).containsExactlyElementsOf(antes);
    }

    @Test
    void cincoFalhasAdministrativasPersistemEBloqueioTerminaNoLimite() throws Exception {
        Navegador admin = new Navegador();
        for (int i = 0; i < 5; i++) {
            caminho(admin.entrar("/admin/entrar", loginAdmin, "incorreta"), "/admin/entrar");
        }
        assertThat(jdbc.queryForObject("SELECT falhas_login FROM administradores WHERE id = 1", Integer.class)).isEqualTo(5);
        var bloqueado = admin.entrar("/admin/entrar", loginAdmin, SENHA_ADMIN);
        caminho(bloqueado, "/admin/entrar");
        assertThat(URI.create(bloqueado.headers().firstValue("location").orElseThrow()).getQuery()).isNotNull();
        when(clock.instant()).thenReturn(AGORA.plus(Duration.ofMinutes(15)));
        caminho(admin.entrar("/admin/entrar", loginAdmin, SENHA_ADMIN), "/admin/representantes");
    }

    private Navegador adminAutenticado() throws Exception {
        Navegador admin = new Navegador();
        caminho(admin.entrar("/admin/entrar", loginAdmin, SENHA_ADMIN), "/admin/representantes");
        return admin;
    }

    private Representante representante(String nome, String entidade, String login) {
        Representante conta = representantes.cadastrar(nome, entidade,
                login == null ? "fixture-" + UUID.randomUUID() : login, TEMPORARIA);
        criados.add(conta.getId());
        return conta;
    }

    private static Map<String, String> cadastro(String nome, String entidade, String login, String senha) {
        return Map.of("nomeCompleto", nome, "entidadePublica", entidade, "login", login, "senhaTemporaria", senha);
    }

    private Long idDoLogin(String login) {
        return jdbc.queryForObject("SELECT id FROM representantes WHERE login = ?", Long.class, login);
    }

    private boolean ativa(Long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT ativa FROM representantes WHERE id = ?", Boolean.class, id));
    }

    private List<String> acoes() {
        return jdbc.queryForList("SELECT acao FROM registros_auditoria WHERE responsavel_tipo = 'ADMINISTRADOR' AND responsavel_id = 1 ORDER BY id", String.class);
    }

    private static String editar(Long id) { return "/admin/representantes/" + id + "/editar"; }
    private static String operacao(Long id, String operacao) { return "/admin/representantes/" + id + "/" + operacao; }

    private static void caminho(HttpResponse<String> resposta, String esperado) {
        assertThat(resposta.statusCode()).isIn(302, 303);
        assertThat(URI.create(resposta.headers().firstValue("location").orElseThrow()).getPath()).isEqualTo(esperado);
    }

    private static String query(Map<String, String> campos) {
        return campos.entrySet().stream().map(campo -> URLEncoder.encode(campo.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(campo.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
    }

    private static String csrf(String html) {
        Matcher inputs = INPUT.matcher(html);
        while (inputs.find()) {
            String input = inputs.group();
            if ("_csrf".equals(atributo(input, "name"))) {
                String token = atributo(input, "value");
                assertThat(token).isNotBlank();
                return token;
            }
        }
        throw new AssertionError("Formulário não contém input CSRF.");
    }

    private static String atributo(String tag, String nome) {
        Matcher atributo = Pattern.compile("\\b" + nome + "\\s*=\\s*([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE).matcher(tag);
        return atributo.find() ? atributo.group(2) : null;
    }

    private class Navegador {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();

        HttpResponse<String> get(String caminho) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(caminho)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> entrar(String endpoint, String login, String senha) throws Exception {
            return postComCsrf(endpoint, get(endpoint).body(), Map.of("login", login, "senha", senha));
        }

        HttpResponse<String> postComCsrf(String caminho, String html, Map<String, String> campos) throws Exception {
            Map<String, String> formulario = new LinkedHashMap<>(campos);
            formulario.put("_csrf", csrf(html));
            return post(caminho, formulario);
        }

        HttpResponse<String> post(String caminho, Map<String, String> campos) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(caminho))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(query(campos))).build(), HttpResponse.BodyHandlers.ofString());
        }

        private URI uri(String caminho) { return URI.create("http://localhost:" + port + caminho); }
    }
}
