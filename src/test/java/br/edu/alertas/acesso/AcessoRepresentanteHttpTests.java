package br.edu.alertas.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.edu.alertas.representantes.AutenticacaoRepresentanteService;
import br.edu.alertas.representantes.Representante;
import br.edu.alertas.representantes.RepresentanteService;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AcessoRepresentanteHttpTests {
    private static final Instant AGORA = Instant.parse("2026-11-21T12:00:00Z");
    private static final String TEMPORARIA = "temporaria-http";
    private static final String DEFINITIVA = "definitiva-http";
    private static final Pattern INPUT = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    @LocalServerPort int port;
    @Autowired RepresentanteService representantes;
    @Autowired AutenticacaoRepresentanteService autenticacao;
    @Autowired JdbcTemplate jdbc;
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
    void percorreLoginPrimeiraTrocaAcessoELogoutComCsrfESessaoRenovada() throws Exception {
        Representante conta = cadastrar();
        Navegador navegador = new Navegador();
        HttpResponse<String> formularioLogin = navegador.get("/entrar");
        assertThat(formularioLogin.statusCode()).isEqualTo(200);
        String sessaoAnonima = navegador.sessao();
        assertThat(sessaoAnonima).isNotNull();

        redireciona(navegador.postComCsrf("/entrar", formularioLogin.body(),
                Map.of("login", conta.getLogin(), "senha", TEMPORARIA)), "/trocar-senha");
        assertThat(navegador.sessao()).isNotEqualTo(sessaoAnonima);
        assertThat(navegador.get("/representante").statusCode()).isEqualTo(403);
        assertThat(navegador.get("/representantes").statusCode()).isEqualTo(403);
        assertThat(navegador.get("/areas").statusCode()).isEqualTo(403);

        HttpResponse<String> formularioTroca = navegador.get("/trocar-senha");
        assertThat(formularioTroca.statusCode()).isEqualTo(200);
        HttpResponse<String> erro = navegador.postComCsrf("/trocar-senha", formularioTroca.body(),
                Map.of("novaSenha", "curta"));
        assertThat(erro.statusCode()).isEqualTo(200);
        assertThat(erro.body()).contains("8").doesNotContain("value=\"curta\"");
        redireciona(navegador.postComCsrf("/trocar-senha", erro.body(),
                Map.of("novaSenha", DEFINITIVA)), "/entrar?senhaAlterada");
        redireciona(navegador.get("/representante"), "/entrar");

        redireciona(navegador.entrar(conta.getLogin(), DEFINITIVA), "/representante");
        HttpResponse<String> pagina = navegador.get("/representante");
        assertThat(pagina.statusCode()).isEqualTo(200);
        assertThat(pagina.body()).doesNotContain(TEMPORARIA, DEFINITIVA);
        assertThat(navegador.get("/trocar-senha").statusCode()).isEqualTo(403);
        assertThat(navegador.get("/auditoria").statusCode()).isEqualTo(403);

        assertThat(navegador.get("/sair").statusCode()).isIn(403, 404, 405);
        assertThat(navegador.get("/representante").statusCode()).isEqualTo(200);
        assertThat(navegador.post("/sair", Map.of()).statusCode()).isEqualTo(403);
        redireciona(navegador.postComCsrf("/sair", pagina.body(), Map.of()), "/entrar?saiu");
        redireciona(navegador.get("/representante"), "/entrar");
    }

    @Test
    void loginHttpPreservaEspacosDaContaSemAutenticarOutroLogin() throws Exception {
        Representante semEspacos = cadastrar();
        Representante literal = representantes.cadastrar("Pessoa Literal", "Defesa Civil",
                " " + semEspacos.getLogin() + " ", "temporaria-literal");
        criados.add(literal.getId());
        Navegador navegador = new Navegador();
        redireciona(navegador.entrar(literal.getLogin(), "temporaria-literal"), "/trocar-senha");
        var formulario = navegador.get("/trocar-senha");
        redireciona(navegador.postComCsrf("/trocar-senha", formulario.body(),
                Map.of("novaSenha", DEFINITIVA)), "/entrar?senhaAlterada");
        assertThat(autenticacao.autenticar(literal.getLogin(), DEFINITIVA)).isPresent();
        assertThat(autenticacao.autenticar(semEspacos.getLogin(), TEMPORARIA)).isPresent();
        assertThat(jdbc.queryForObject("SELECT falhas_login FROM representantes WHERE id = ?",
                Integer.class, semEspacos.getId())).isZero();
    }

    @Test
    void loginETrocaRejeitamPostSemCsrfOuComTokenInvalido() throws Exception {
        Representante conta = cadastrar();
        Navegador navegador = new Navegador();
        navegador.get("/entrar");
        assertThat(navegador.post("/entrar", Map.of("login", conta.getLogin(), "senha", TEMPORARIA))
                .statusCode()).isEqualTo(403);
        assertThat(navegador.post("/entrar", Map.of("login", conta.getLogin(), "senha", TEMPORARIA,
                "_csrf", "invalido")).statusCode()).isEqualTo(403);

        redireciona(navegador.entrar(conta.getLogin(), TEMPORARIA), "/trocar-senha");
        assertThat(navegador.post("/trocar-senha", Map.of("novaSenha", DEFINITIVA)).statusCode())
                .isEqualTo(403);
        assertThat(autenticacao.autenticar(conta.getLogin(), TEMPORARIA)).isPresent();
    }

    @Test
    void falhaGenericaPreservaLoginEscapadoSemDevolverSenha() throws Exception {
        Navegador navegador = new Navegador();
        String login = "rf005-<script>alert(1)</script>";
        redireciona(navegador.entrar(login, "senha-nao-devolver"), "/entrar?erro");

        HttpResponse<String> erro = navegador.get("/entrar?erro");
        assertThat(erro.statusCode()).isEqualTo(200);
        assertThat(erro.body()).contains("&lt;script&gt;").doesNotContain("<script>alert(1)</script>",
                "senha-nao-devolver");
        Representante inativa = cadastrar();
        representantes.desativar(inativa.getId());
        redireciona(new Navegador().entrar(inativa.getLogin(), TEMPORARIA), "/entrar?erro");
    }

    @Test
    void cincoFalhasHttpPersistemApesarDaExcecaoDoProviderEBloqueiamSenhaCorreta() throws Exception {
        Representante conta = cadastrar();
        Navegador navegador = new Navegador();
        for (int tentativa = 0; tentativa < 5; tentativa++) {
            redireciona(navegador.entrar(conta.getLogin(), "incorreta"), "/entrar?erro");
        }
        assertThat(jdbc.queryForObject("SELECT falhas_login FROM representantes WHERE id = ?",
                Integer.class, conta.getId())).isEqualTo(5);
        redireciona(navegador.entrar(conta.getLogin(), TEMPORARIA), "/entrar?erro");

        when(clock.instant()).thenReturn(AGORA.plus(Duration.ofMinutes(15)));
        redireciona(navegador.entrar(conta.getLogin(), TEMPORARIA), "/trocar-senha");
    }

    @ParameterizedTest
    @ValueSource(strings = {"desativar", "redefinir", "corrigirLogin"})
    void manutencaoRevogaSessaoJaAutenticadaNaProximaRequisicao(String operacao) throws Exception {
        Representante conta = cadastrar();
        autenticacao.trocarSenha(autenticacao.autenticar(conta.getLogin(), TEMPORARIA).orElseThrow(), DEFINITIVA);
        Navegador navegador = new Navegador();
        redireciona(navegador.entrar(conta.getLogin(), DEFINITIVA), "/representante");
        assertThat(navegador.get("/representante").statusCode()).isEqualTo(200);

        switch (operacao) {
            case "desativar" -> representantes.desativar(conta.getId());
            case "redefinir" -> representantes.redefinirSenhaTemporaria(conta.getId(), "temporaria-nova");
            case "corrigirLogin" -> representantes.alterar(conta.getId(), conta.getNomeCompleto(),
                    conta.getEntidadePublica(), conta.getLogin() + "-novo");
            default -> throw new IllegalArgumentException(operacao);
        }

        redirecionaParaEntrada(navegador.get("/representante"));
        if (operacao.equals("desativar")) {
            representantes.reativar(conta.getId());
            redirecionaParaEntrada(navegador.get("/representante"));
        }
    }

    @Test
    void temporariaVencidaRecusaLoginEPostDeTrocaEmSessaoExistente() throws Exception {
        Representante conta = cadastrar();
        Navegador navegador = new Navegador();
        redireciona(navegador.entrar(conta.getLogin(), TEMPORARIA), "/trocar-senha");
        HttpResponse<String> formulario = navegador.get("/trocar-senha");
        when(clock.instant()).thenReturn(AGORA.plus(Duration.ofHours(24)));

        redirecionaParaEntrada(navegador.postComCsrf("/trocar-senha", formulario.body(),
                Map.of("novaSenha", DEFINITIVA)));
        redireciona(new Navegador().entrar(conta.getLogin(), TEMPORARIA), "/entrar?erro");
        assertThat(autenticacao.autenticar(conta.getLogin(), DEFINITIVA)).isEmpty();
    }

    @Test
    void rotasProtegidasExigemLoginENaoExisteContaPadrao() throws Exception {
        Navegador navegador = new Navegador();
        redireciona(navegador.get("/representante"), "/entrar");
        redireciona(navegador.get("/trocar-senha"), "/entrar");
        assertThat(navegador.get("/moradores").statusCode()).isEqualTo(403);
        redireciona(navegador.entrar("user", "password"), "/entrar?erro");
        assertThat(navegador.get("/representantes").statusCode()).isEqualTo(403);
    }

    private Representante cadastrar() {
        Representante conta = representantes.cadastrar("Pessoa Exemplo HTTP", "Defesa Civil",
                "rf005-http-" + UUID.randomUUID(), TEMPORARIA);
        criados.add(conta.getId());
        return conta;
    }

    private static void redireciona(HttpResponse<String> resposta, String destino) {
        assertThat(resposta.statusCode()).isIn(302, 303);
        URI location = URI.create(resposta.headers().firstValue("location").orElseThrow());
        assertThat(location.getRawPath() + (location.getRawQuery() == null ? "" : "?" + location.getRawQuery()))
                .isEqualTo(destino);
    }

    private static void redirecionaParaEntrada(HttpResponse<String> resposta) {
        assertThat(resposta.statusCode()).isIn(302, 303);
        assertThat(URI.create(resposta.headers().firstValue("location").orElseThrow()).getPath())
                .isEqualTo("/entrar");
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
        Matcher atributo = Pattern.compile("\\b" + nome + "\\s*=\\s*([\"'])(.*?)\\1",
                Pattern.CASE_INSENSITIVE).matcher(tag);
        return atributo.find() ? atributo.group(2) : null;
    }

    private class Navegador {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER).build();

        HttpResponse<String> get(String caminho) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(caminho)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> entrar(String login, String senha) throws Exception {
            return postComCsrf("/entrar", get("/entrar").body(), Map.of("login", login, "senha", senha));
        }

        HttpResponse<String> postComCsrf(String caminho, String html, Map<String, String> campos) throws Exception {
            Map<String, String> formulario = new LinkedHashMap<>(campos);
            formulario.put("_csrf", csrf(html));
            return post(caminho, formulario);
        }

        HttpResponse<String> post(String caminho, Map<String, String> campos) throws Exception {
            String body = campos.entrySet().stream()
                    .map(campo -> URLEncoder.encode(campo.getKey(), StandardCharsets.UTF_8) + "="
                            + URLEncoder.encode(campo.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            return client.send(HttpRequest.newBuilder(uri(caminho))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }

        String sessao() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> cookie.getName().equals("JSESSIONID"))
                    .map(cookie -> cookie.getValue()).findFirst().orElse(null);
        }

        private URI uri(String caminho) {
            return URI.create("http://localhost:" + port + caminho);
        }
    }
}
