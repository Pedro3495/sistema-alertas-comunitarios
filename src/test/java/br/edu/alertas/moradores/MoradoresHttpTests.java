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
        jdbc.update("""
                DELETE FROM registros_auditoria WHERE responsavel_tipo = 'MORADOR'
                AND responsavel_id IN (SELECT id FROM moradores WHERE residencia_id = ?)""", residencia);
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

    @Test
    void moradorCadastradoEntraComCpfTrocaTemporariaEUsaSenhaDefinitiva() throws Exception {
        long id = cadastrarPeloRepresentante();
        String temporaria = senhaEnviada();
        var morador = new Navegador();
        assertThat(morador.get("/morador").headers().firstValue("Location").orElseThrow()).endsWith("/morador/entrar");
        String pagina = morador.get("/morador/entrar").body();
        assertThat(pagina).contains("CPF", "senha temporária");
        assertThat(morador.form("/morador/entrar", Map.of("cpf", "52998224725", "senha", temporaria)).statusCode())
                .as("sem CSRF").isEqualTo(403);
        var entrada = morador.post("/morador/entrar", pagina, Map.of("cpf", "529.982.247-25", "senha", temporaria));
        assertThat(entrada.headers().firstValue("Location").orElseThrow()).endsWith("/morador/trocar-senha");
        assertThat(morador.get("/morador").statusCode()).as("temporária não dá acesso").isEqualTo(403);
        assertThat(morador.get("/moradores").statusCode()).isEqualTo(403);
        assertThat(morador.get("/representante").statusCode()).isEqualTo(403);

        String troca = morador.get("/morador/trocar-senha").body();
        var curta = morador.post("/morador/trocar-senha", troca, Map.of("novaSenha", "1234567"));
        assertThat(curta.body()).contains("Use no mínimo 8 caracteres.").doesNotContain("1234567\"");
        var salva = morador.post("/morador/trocar-senha", curta.body(), Map.of("novaSenha", DEFINITIVA));
        assertThat(salva.headers().firstValue("Location").orElseThrow()).endsWith("/morador/entrar?senhaAlterada");
        assertThat(morador.get("/morador/trocar-senha").headers().firstValue("Location").orElseThrow()).endsWith("/morador/entrar");
        assertThat(jdbc.queryForObject("SELECT troca_senha_obrigatoria FROM moradores WHERE id = ?", Boolean.class, id)).isFalse();
        String hash = jdbc.queryForObject("SELECT senha_hash FROM moradores WHERE id = ?", String.class, id);
        assertThat(jdbc.queryForList("SELECT acao FROM registros_auditoria WHERE responsavel_tipo = 'MORADOR' AND responsavel_id = ?", String.class, id))
                .as("primeira troca auditada sem segredo").containsExactly("DEFINIR_SENHA_DEFINITIVA_MORADOR #" + id)
                .allSatisfy(acao -> assertThat(acao).doesNotContain(DEFINITIVA, temporaria, hash));

        var negada = morador.post("/morador/entrar", morador.get("/morador/entrar").body(), Map.of("cpf", "52998224725", "senha", temporaria));
        assertThat(negada.headers().firstValue("Location").orElseThrow()).endsWith("/morador/entrar?erro");
        var definitiva = morador.post("/morador/entrar", morador.get("/morador/entrar").body(), Map.of("cpf", "52998224725", "senha", DEFINITIVA));
        assertThat(definitiva.headers().firstValue("Location").orElseThrow()).endsWith("/morador");
        String inicio = morador.get("/morador").body();
        assertThat(inicio).contains("Você entrou na sua conta").doesNotContain("52998224725", "81900000001");
        assertThat(morador.get("/moradores").statusCode()).as("morador não usa funções da entidade").isEqualTo(403);
        var saida = morador.post("/morador/sair", inicio, Map.of());
        assertThat(saida.headers().firstValue("Location").orElseThrow()).endsWith("/morador/entrar?saiu");
        assertThat(morador.get("/morador").headers().firstValue("Location").orElseThrow()).endsWith("/morador/entrar");

        morador.post("/morador/entrar", morador.get("/morador/entrar").body(), Map.of("cpf", "52998224725", "senha", DEFINITIVA));
        assertThat(morador.get("/morador").statusCode()).isEqualTo(200);
        jdbc.update("UPDATE moradores SET ativa = false WHERE id = ?", id);
        assertThat(morador.get("/morador").headers().firstValue("Location").orElseThrow())
                .as("desativação encerra sessão existente").endsWith("/morador/entrar?expirado");
    }

    @Test
    void cincoFalhasHttpBloqueiamOMoradorComMensagemGenericaSemDevolverSenha() throws Exception {
        long id = cadastrarPeloRepresentante();
        String temporaria = senhaEnviada();
        var morador = new Navegador();
        for (int i = 0; i < 5; i++) {
            var falha = morador.post("/morador/entrar", morador.get("/morador/entrar").body(), Map.of("cpf", "52998224725", "senha", "errada-ficticia"));
            assertThat(falha.headers().firstValue("Location").orElseThrow()).endsWith("/morador/entrar?erro");
        }
        assertThat(jdbc.queryForObject("SELECT falhas_login FROM moradores WHERE id = ?", Integer.class, id)).isEqualTo(5);
        var bloqueada = morador.post("/morador/entrar", morador.get("/morador/entrar").body(), Map.of("cpf", "52998224725", "senha", temporaria));
        assertThat(bloqueada.headers().firstValue("Location").orElseThrow()).endsWith("/morador/entrar?erro");
        String erro = morador.get("/morador/entrar?erro").body();
        assertThat(erro).contains("Não foi possível entrar", "15 minutos", "value=\"52998224725\"").doesNotContain(temporaria, "errada-ficticia");
        var inexistente = new Navegador();
        var desconhecido = inexistente.post("/morador/entrar", inexistente.get("/morador/entrar").body(), Map.of("cpf", "11144477735", "senha", "qualquer-ficticia"));
        assertThat(desconhecido.headers().firstValue("Location").orElseThrow()).endsWith("/morador/entrar?erro");
    }

    @Test
    void moradorRecuperaAcessoPorCodigoNoTelefoneECriaNovaSenhaSemEntrarDireto(CapturedOutput saida) throws Exception {
        cadastrarPeloRepresentante();
        String temporaria = senhaEnviada();
        var morador = new Navegador();
        String entrar = morador.get("/morador/entrar").body();
        assertThat(entrar).contains("href=\"/morador/recuperar\"");
        String pagina = morador.get("/morador/recuperar").body();
        assertThat(pagina).contains("Confirme o telefone", "Procure a entidade pública");
        assertThat(morador.form("/morador/recuperar", Map.of("celular", "81900000001")).statusCode()).as("sem CSRF").isEqualTo(403);
        assertThat(morador.get("/morador/recuperar/codigo").headers().firstValue("Location").orElseThrow())
                .as("sem telefone confirmado").endsWith("/morador/recuperar");

        var envioCodigo = morador.post("/morador/recuperar", pagina, Map.of("celular", "(81) 90000-0001"));
        assertThat(envioCodigo.headers().firstValue("Location").orElseThrow()).endsWith("/morador/recuperar/codigo");
        var codigos = ArgumentCaptor.forClass(String.class);
        verify(envio).enviarCodigoRecuperacao(eq(CanalCredenciais.SMS), eq("81900000001"), codigos.capture());
        String codigo = codigos.getValue();
        String formulario = morador.get("/morador/recuperar/codigo").body();
        assertThat(formulario).contains("10 minutos", "único uso").doesNotContain(codigo, "81900000001");

        var errado = morador.post("/morador/recuperar/codigo", formulario, Map.of("codigo", "000000".equals(codigo) ? "111111" : "000000", "novaSenha", DEFINITIVA));
        assertThat(errado.body()).contains("Código inválido", "5 erros");
        var curta = morador.post("/morador/recuperar/codigo", errado.body(), Map.of("codigo", codigo, "novaSenha", "1234567"));
        assertThat(curta.body()).contains("Use no mínimo 8 caracteres.");
        var redefinida = morador.post("/morador/recuperar/codigo", curta.body(), Map.of("codigo", codigo, "novaSenha", DEFINITIVA));
        assertThat(redefinida.headers().firstValue("Location").orElseThrow()).endsWith("/morador/entrar?senhaAlterada");
        assertThat(morador.get("/morador").headers().firstValue("Location").orElseThrow()).as("código não dá entrada").endsWith("/morador/entrar");
        assertThat(morador.get("/morador/recuperar/codigo").headers().firstValue("Location").orElseThrow()).endsWith("/morador/recuperar");

        var negada = morador.post("/morador/entrar", morador.get("/morador/entrar").body(), Map.of("cpf", "52998224725", "senha", temporaria));
        assertThat(negada.headers().firstValue("Location").orElseThrow()).endsWith("/morador/entrar?erro");
        var entrada = morador.post("/morador/entrar", morador.get("/morador/entrar").body(), Map.of("cpf", "52998224725", "senha", DEFINITIVA));
        assertThat(entrada.headers().firstValue("Location").orElseThrow()).endsWith("/morador");
        assertThat(saida.getAll()).doesNotContain(codigo);
    }

    @Test
    void falhaNoEnvioDoCodigoInformaEReenviaPeloMesmoCanalETelefoneDesconhecidoNaoERevelado() throws Exception {
        cadastrarPeloRepresentante();
        doReturn(false).when(envio).enviarCodigoRecuperacao(any(), any(), any());
        var morador = new Navegador();
        var falha = morador.post("/morador/recuperar", morador.get("/morador/recuperar").body(), Map.of("celular", "81900000001"));
        assertThat(falha.statusCode()).isEqualTo(200);
        assertThat(falha.body()).contains("O código não foi enviado", "mesmo canal", "Reenviar código", "value=\"81900000001\"");
        doCallRealMethod().when(envio).enviarCodigoRecuperacao(any(), any(), any());
        var reenvio = morador.post("/morador/recuperar", falha.body(), Map.of("celular", "81900000001"));
        assertThat(reenvio.headers().firstValue("Location").orElseThrow()).endsWith("/morador/recuperar/codigo");
        verify(envio, times(2)).enviarCodigoRecuperacao(eq(CanalCredenciais.SMS), eq("81900000001"), anyString());

        var outro = new Navegador();
        var desconhecido = outro.post("/morador/recuperar", outro.get("/morador/recuperar").body(), Map.of("celular", "81900000099"));
        assertThat(desconhecido.headers().firstValue("Location").orElseThrow()).endsWith("/morador/recuperar/codigo");
        verify(envio, times(2)).enviarCodigoRecuperacao(any(), any(), any());
    }

    @Test
    void moradorAlteraPreferenciasDeCanaisNoAplicativoComAuditoria() throws Exception {
        long id = cadastrarPeloRepresentante();
        jdbc.update("UPDATE moradores SET senha_hash = ?, troca_senha_obrigatoria = false WHERE id = ?", encoder.encode(DEFINITIVA), id);
        assertThat(new Navegador().get("/morador/preferencias").statusCode()).as("anônimo").isEqualTo(403);
        assertThat(autenticado().get("/morador/preferencias").statusCode()).as("representante").isEqualTo(403);
        var morador = new Navegador();
        morador.post("/morador/entrar", morador.get("/morador/entrar").body(), Map.of("cpf", "52998224725", "senha", DEFINITIVA));
        assertThat(morador.get("/morador").body()).contains("href=\"/morador/preferencias\"");
        String pagina = morador.get("/morador/preferencias").body();
        assertThat(pagina).contains("<option value=\"SMS\" selected", "<option value=\"AMBOS\" selected", "outro disponível", "procure a entidade")
                .doesNotContain("81900000001");
        assertThat(morador.form("/morador/preferencias", Map.of("canalCredenciais", "WHATSAPP", "preferenciaAlertas", "SMS")).statusCode())
                .as("sem CSRF").isEqualTo(403);
        var invalida = morador.post("/morador/preferencias", pagina, Map.of("canalCredenciais", "EMAIL", "preferenciaAlertas", "SMS"));
        assertThat(invalida.statusCode()).isEqualTo(200);
        assertThat(invalida.body()).contains("Escolha SMS ou WhatsApp");
        var salva = morador.post("/morador/preferencias", pagina, Map.of("canalCredenciais", "WHATSAPP", "preferenciaAlertas", "SMS"));
        assertThat(salva.headers().firstValue("Location").orElseThrow()).endsWith("/morador/preferencias?salvas");
        assertThat(morador.get("/morador/preferencias?salvas").body()).contains("Preferências salvas.", "<option value=\"WHATSAPP\" selected");
        assertThat(jdbc.queryForMap("SELECT canal_credenciais, preferencia_alertas, celular FROM moradores WHERE id = ?", id))
                .containsEntry("canal_credenciais", "WHATSAPP").containsEntry("preferencia_alertas", "SMS").containsEntry("celular", "81900000001");
        assertThat(jdbc.queryForList("SELECT acao FROM registros_auditoria WHERE responsavel_tipo = 'MORADOR' AND responsavel_id = ?", String.class, id))
                .containsExactly("ALTERAR_PREFERENCIAS_MORADOR #" + id);
    }

    private long cadastrarPeloRepresentante() throws Exception {
        var navegador = autenticado();
        var resposta = navegador.post("/moradores/novo", navegador.get("/moradores/novo").body(), dados("52998224725", "81900000001", "SMS"));
        return Long.parseLong(URI.create(resposta.headers().firstValue("Location").orElseThrow()).getPath().substring("/moradores/".length()));
    }

    private String senhaEnviada() {
        var senhas = ArgumentCaptor.forClass(String.class);
        verify(envio).enviarSenhaTemporaria(eq(CanalCredenciais.SMS), eq("81900000001"), senhas.capture());
        return senhas.getValue();
    }

    private Map<String, String> dados(String cpf, String celular, String canal) {
        return Map.of("nomeCompleto", "Pessoa Fictícia", "cpf", cpf, "celular", celular, "residenciaId", Long.toString(residencia),
                "canalCredenciais", canal, "preferenciaAlertas", "AMBOS");
    }

    private List<String> acoes() {
        return jdbc.queryForList("SELECT acao FROM registros_auditoria WHERE responsavel_tipo = 'REPRESENTANTE' AND responsavel_id = ? AND acao NOT LIKE 'DEFINIR_SENHA_DEFINITIVA_%' ORDER BY id", String.class, representante.getId()); // a primeira troca do preparo do ator não é a ação testada
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
