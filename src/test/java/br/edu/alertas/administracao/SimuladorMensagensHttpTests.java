package br.edu.alertas.administracao;

import br.edu.alertas.moradores.CanalCredenciais;
import br.edu.alertas.moradores.EnvioCredenciais;
import br.edu.alertas.representantes.RepresentanteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Caixa de saída do envio simulado ligada (demonstração): visível só ao administrador autenticado. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "alertas.envio-simulado.exibir=true")
class SimuladorMensagensHttpTests {
    private static final String SENHA = "senha-ficticia-teste";
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"|value=\"([^\"]+)\"[^>]*name=\"_csrf\"");

    @LocalServerPort int port;
    @Autowired AdministradorService administradores;
    @Autowired RepresentanteService representantes;
    @Autowired EnvioCredenciais envio;
    @Autowired JdbcTemplate jdbc;
    private String loginAdmin;
    private String loginRepresentante;

    @BeforeEach
    void criaContasFicticias() {
        loginAdmin = "admin-sim-" + UUID.randomUUID();
        administradores.criarPrimeiro(loginAdmin, SENHA);
        loginRepresentante = "rep-sim-" + UUID.randomUUID();
        representantes.cadastrar("Pessoa Fictícia", "Entidade", loginRepresentante, SENHA);
    }

    @AfterEach
    void removeContasFicticias() {
        jdbc.update("DELETE FROM registros_auditoria WHERE responsavel_tipo = 'REPRESENTANTE' AND responsavel_id IN (SELECT id FROM representantes WHERE login = ?)", loginRepresentante);
        jdbc.update("DELETE FROM representantes WHERE login = ?", loginRepresentante);
        jdbc.update("DELETE FROM registros_auditoria WHERE responsavel_tipo = 'ADMINISTRADOR' AND responsavel_id = 1");
        jdbc.update("DELETE FROM administradores WHERE id = 1 AND login = ?", loginAdmin);
    }

    @Test
    void administradorVeMensagemSimuladaEDemaisPerfisNao() throws Exception {
        String codigo = "%06d".formatted(new java.security.SecureRandom().nextInt(1_000_000));
        assertThat(envio.enviarCodigoRecuperacao(CanalCredenciais.WHATSAPP, "81900004321", codigo)).isTrue();

        var admin = new Navegador();
        assertThat(admin.entrar("/admin/entrar", loginAdmin).headers().firstValue("location").orElseThrow()).endsWith("/admin/representantes");
        var pagina = admin.get("/admin/simulador/mensagens");
        assertThat(pagina.statusCode()).isEqualTo(200);
        assertThat(pagina.body()).contains("ENVIO SIMULADO — demonstração", codigo, "***4321", "WHATSAPP").doesNotContain("81900004321");
        assertThat(admin.get("/admin/representantes").body()).contains("/admin/simulador/mensagens");

        var anonimo = new Navegador().get("/admin/simulador/mensagens");
        assertThat(anonimo.statusCode()).isNotEqualTo(200);
        assertThat(anonimo.body()).doesNotContain(codigo);

        var representante = new Navegador();
        representante.entrar("/entrar", loginRepresentante);
        var negado = representante.get("/admin/simulador/mensagens");
        assertThat(negado.statusCode()).isNotEqualTo(200);
        assertThat(negado.body()).doesNotContain(codigo);
    }

    private class Navegador {
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).build();

        HttpResponse<String> get(String caminho) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(caminho)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> entrar(String caminho, String login) throws Exception {
            var m = CSRF.matcher(get(caminho).body());
            assertThat(m.find()).isTrue();
            String token = m.group(1) != null ? m.group(1) : m.group(2);
            String corpo = "login=" + URLEncoder.encode(login, StandardCharsets.UTF_8) + "&senha=" + SENHA + "&_csrf=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            return client.send(HttpRequest.newBuilder(uri(caminho)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(corpo)).build(), HttpResponse.BodyHandlers.ofString());
        }

        private URI uri(String caminho) { return URI.create("http://localhost:" + port + caminho); }
    }
}
