package br.edu.alertas.moradores;

import br.edu.alertas.representantes.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MoradoresHttpTests {
    private static final String TEMPORARIA = "temporaria-ficticia-moradores";
    private static final String DEFINITIVA = "definitiva-ficticia-moradores";
    @LocalServerPort int port;
    @Autowired RepresentanteService representantes;
    @Autowired AutenticacaoRepresentanteService acesso;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @MockitoSpyBean EnvioCredenciais envio;
    private Representante representante;
    private long area;
    private long residencia;

    @BeforeEach
    void preparar() {
        representante = representantes.cadastrar("Representante fictício", "Entidade fictícia", "moradores-" + UUID.randomUUID(), TEMPORARIA);
        acesso.trocarSenha(acesso.autenticar(representante.getLogin(), TEMPORARIA).orElseThrow(), DEFINITIVA);
        area = jdbc.queryForObject("INSERT INTO areas_risco(nome, descricao_localizacao_limites) VALUES (?, 'Limites') RETURNING id", Long.class, "Área moradores " + UUID.randomUUID());
        residencia = jdbc.queryForObject("INSERT INTO residencias(rua_logradouro, numero, bairro, municipio, area_id) VALUES ('Rua Fictícia Moradores', 's/n', 'Bairro', 'Município', ?) RETURNING id", Long.class, area);
    }

    @AfterEach
    void limpar() {
        reset(envio);
        jdbc.update("DELETE FROM moradores WHERE residencia_id = ?", residencia);
        jdbc.update("DELETE FROM residencias WHERE id = ?", residencia);
        jdbc.update("DELETE FROM areas_risco WHERE id = ?", area);
        jdbc.update("DELETE FROM registros_auditoria WHERE responsavel_tipo = 'REPRESENTANTE' AND responsavel_id = ?", representante.getId());
        jdbc.update("DELETE FROM representantes WHERE id = ?", representante.getId());
    }

    @Test
    void cadastroWebEnviaSenhaTemporariaSimuladaSemExibirNemRegistrarSegredo(CapturedOutput saida) throws Exception {
        var navegador = autenticado();
        var formulario = navegador.get("/moradores/novo").body();
        assertThat(formulario).contains("Rua Fictícia Moradores", "Se um canal de alerta falhar", "envio é simulado", "não é anexado");
        var resposta = navegador.post("/moradores/novo", formulario, dados("52998224725", "81900000001", "WHATSAPP"));
        assertThat(resposta.statusCode()).isEqualTo(302);
        String detalheUrl = URI.create(resposta.headers().firstValue("Location").orElseThrow()).getPath();
        long id = Long.parseLong(detalheUrl.substring("/moradores/".length()));
        var senhas = ArgumentCaptor.forClass(String.class);
        verify(envio).enviarSenhaTemporaria(eq(CanalCredenciais.WHATSAPP), eq("81900000001"), senhas.capture());
        String senha = senhas.getValue();
        assertThat(encoder.matches(senha, jdbc.queryForObject("SELECT senha_hash FROM moradores WHERE id = ?", String.class, id))).isTrue();
        var detalhe = navegador.get(detalheUrl).body();
        assertThat(detalhe).contains("Pessoa Fictícia", "Envio simulado da senha temporária concluído", "Nenhuma mensagem real").doesNotContain(senha, "Reenviar");
        assertThat(navegador.get("/moradores?termo=529982").body()).contains("Pessoa Fictícia");
        assertThat(acoes()).containsExactly("CADASTRAR_MORADOR #" + id, "ENVIO_SIMULADO_SENHA_TEMPORARIA_WHATSAPP #" + id);
        assertThat(navegador.post("/moradores/" + id + "/reenviar-senha", detalhe, Map.of()).body()).contains("só é permitido");
        assertThat(acoes()).hasSize(2);
        assertThat(saida.getAll()).doesNotContain(senha);
    }

    @Test
    void falhaDoEnvioSimuladoInformaEPermiteReenvioManualPeloMesmoCanal() throws Exception {
        doReturn(false).when(envio).enviarSenhaTemporaria(any(), any(), any());
        var navegador = autenticado();
        var resposta = navegador.post("/moradores/novo", navegador.get("/moradores/novo").body(), dados("52998224725", "81900000001", "SMS"));
        String detalheUrl = URI.create(resposta.headers().firstValue("Location").orElseThrow()).getPath();
        var detalhe = navegador.get(detalheUrl).body();
        assertThat(detalhe).contains("A senha temporária não foi entregue", "mesmo canal", "Reenviar senha temporária");
        assertThat(navegador.get("/moradores").body()).contains("envio simulado falhou");
        doCallRealMethod().when(envio).enviarSenhaTemporaria(any(), any(), any());
        assertThat(navegador.post(detalheUrl + "/reenviar-senha", detalhe, Map.of()).statusCode()).isEqualTo(302);
        verify(envio, times(2)).enviarSenhaTemporaria(eq(CanalCredenciais.SMS), eq("81900000001"), anyString());
        assertThat(navegador.get(detalheUrl).body()).contains("Envio simulado da senha temporária concluído").doesNotContain("Reenviar");
        long id = Long.parseLong(detalheUrl.substring("/moradores/".length()));
        assertThat(acoes()).containsExactly("CADASTRAR_MORADOR #" + id, "FALHA_ENVIO_SIMULADO_SENHA_TEMPORARIA_SMS #" + id,
                "ENVIO_SIMULADO_SENHA_TEMPORARIA_SMS #" + id);
    }

    @Test
    void erroDeCampoPreservaDadosSemCadastrarNemEnviar() throws Exception {
        var navegador = autenticado();
        var campos = new HashMap<>(dados("12345678900", "8190", "SMS"));
        campos.put("canalCredenciais", "EMAIL");
        var resposta = navegador.post("/moradores/novo", navegador.get("/moradores/novo").body(), campos);
        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).contains("verificadores válidos", "DDD e 11 dígitos", "Escolha SMS ou WhatsApp", "Pessoa Fictícia", "12345678900");
        jdbc.update("INSERT INTO moradores(nome_completo, cpf, celular, residencia_id, canal_credenciais, preferencia_alertas, ativa) VALUES ('Outro', '11144477735', '81900000009', ?, 'SMS', 'SMS', true)", residencia);
        var duplicado = navegador.post("/moradores/novo", navegador.get("/moradores/novo").body(), dados("11144477735", "81900000001", "SMS"));
        assertThat(duplicado.body()).contains("O CPF já pertence a outra conta.");
        verifyNoInteractions(envio);
        assertThat(acoes()).isEmpty();
    }

    @Test
    void anonimoNaoAcessaCadastroNemReenvio() throws Exception {
        var anonimo = new Navegador();
        assertThat(anonimo.get("/moradores/novo").statusCode()).isEqualTo(403);
        assertThat(anonimo.form("/moradores/1/reenviar-senha", Map.of()).statusCode()).isEqualTo(403);
        verifyNoInteractions(envio);
    }

    private Map<String, String> dados(String cpf, String celular, String canal) {
        return Map.of("nomeCompleto", "Pessoa Fictícia", "cpf", cpf, "celular", celular, "residenciaId", Long.toString(residencia),
                "canalCredenciais", canal, "preferenciaAlertas", "AMBOS");
    }

    private List<String> acoes() {
        return jdbc.queryForList("SELECT acao FROM registros_auditoria WHERE responsavel_tipo = 'REPRESENTANTE' AND responsavel_id = ? ORDER BY id", String.class, representante.getId());
    }

    private Navegador autenticado() throws Exception {
        var navegador = new Navegador();
        var entrada = navegador.post("/entrar", navegador.get("/entrar").body(), Map.of("login", representante.getLogin(), "senha", DEFINITIVA));
        assertThat(entrada.headers().firstValue("Location").orElseThrow()).endsWith("/representante");
        return navegador;
    }

    private static String csrf(String html) {
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"|value=\"([^\"]+)\"[^>]*name=\"_csrf\"").matcher(html);
        assertThat(matcher.find()).as("formulário com CSRF").isTrue();
        return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
    }

    private class Navegador {
        final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();
        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> post(String path, String paginaComCsrf, Map<String, String> campos) throws Exception {
            var comToken = new HashMap<>(campos);
            comToken.put("_csrf", csrf(paginaComCsrf));
            return form(path, comToken);
        }
        HttpResponse<String> form(String path, Map<String, String> campos) throws Exception {
            String body = campos.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
