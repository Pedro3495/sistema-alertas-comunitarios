package br.edu.alertas.ocorrencias;

import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OcorrenciasHttpTests extends OcorrenciasFixtures {
    @LocalServerPort int port;

    @Test
    void multipartRealConfirmaExibeDetalheERetornaBytesComMimeDoServidor() throws Exception {
        var navegador = autenticado();
        String formulario = navegador.get("/ocorrencias/novo").body();
        assertThat(formulario).contains("Área RF006", "Descrição");
        String envio = UUID.randomUUID().toString();
        byte[] png = imagem("png"), jpeg = imagem("jpeg"), heic = heic(), heif = heif();
        var resposta = navegador.multipart(campos(envio), List.of(png, jpeg, heic, heif), csrf(formulario), "application/octet-stream", "evidencia.bin");
        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.headers().firstValue("Content-Type").orElseThrow()).contains("application/json");
        String protocolo = json(resposta.body(), "protocolo");
        assertThat(UUID.fromString(protocolo)).isNotEqualTo(UUID.fromString(envio));
        String detalheUrl = json(resposta.body(), "detalheUrl");
        assertThat(detalheUrl).isEqualTo("/ocorrencias/" + protocolo);
        assertThat(resposta.body()).doesNotContain("conteudo", "iVBOR", "Descrição fictícia");
        var detalhe = navegador.get(detalheUrl);
        assertThat(detalhe.statusCode()).isEqualTo(200);
        assertThat(detalhe.body()).contains(protocolo, "Em verificação", "Descrição fictícia", "Ponte fictícia", representante.getNomeCompleto(), "Área RF006");
        for (int i = 0; i < 4; i++) {
            var foto = navegador.bytes(detalheUrl + "/fotos/" + (i + 1));
            assertThat(foto.statusCode()).isEqualTo(200);
            assertThat(foto.body()).containsExactly(List.of(png, jpeg, heic, heif).get(i));
            String mime = foto.headers().firstValue("Content-Type").orElseThrow();
            assertThat(mime).isEqualTo(service.foto(UUID.fromString(protocolo), i + 1).getTipoMidia());
            if (i < 3) assertThat(mime).isEqualTo(List.of("image/png", "image/jpeg", "image/heic").get(i));
            else assertThat(mime).isIn("image/heic", "image/heif");
            assertThat(foto.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
            assertThat(foto.headers().firstValue("X-Content-Type-Options").orElseThrow()).isEqualTo("nosniff");
            assertThat(foto.headers().firstValue("Content-Disposition").orElseThrow()).doesNotContain("evidencia.bin");
        }
        var replay = navegador.multipart(campos(envio), List.of(png, jpeg, heic, heif), csrf(formulario), "image/png", "foto.png");
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(json(replay.body(), "protocolo")).isEqualTo(protocolo);
        assertThat(quantidadeOcorrencias()).isEqualTo(1);
        assertThat(quantidadeFotos()).isEqualTo(4);
        assertThat(acoes()).containsExactly("REGISTRAR_OCORRENCIA #" + protocolo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"envioId", "tipo", "descricao", "localizacao", "areaId", "especificacaoOutros"})
    void erroHttpApontaCampoSemPersistencia(String campo) throws Exception {
        var navegador = autenticado();
        var campos = campos(UUID.randomUUID().toString());
        campos.put("tipo", "OUTROS");
        campos.put("especificacaoOutros", "Outro evento");
        campos.put(campo, "");
        erro(navegador.multipart(campos, List.of(imagem("png")), csrf(navegador.get("/ocorrencias/novo").body()), "image/png", "foto.png"), campo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tipo", "envioId", "areaId"})
    void erroDeConversaoHttpTemJsonDeCampo(String campo) throws Exception {
        var navegador = autenticado();
        var campos = campos(UUID.randomUUID().toString());
        campos.put(campo, "valor-invalido");
        erro(navegador.multipart(campos, List.of(imagem("png")), csrf(navegador.get("/ocorrencias/novo").body()), "image/png", "foto.png"), campo);
    }

    @Test
    void mimeENomeForjadosNaoTransformamTextoEmImagem() throws Exception {
        var navegador = autenticado();
        String token = csrf(navegador.get("/ocorrencias/novo").body());
        erro(navegador.multipart(campos(UUID.randomUUID().toString()), List.of("nao e imagem".getBytes(StandardCharsets.UTF_8)), token, "image/png", "foto.png"), "fotos");
        erro(navegador.multipart(campos(UUID.randomUUID().toString()), List.of(), token, "image/png", "foto.png"), "fotos");
        erro(navegador.multipart(campos(UUID.randomUUID().toString()), Collections.nCopies(5, imagem("png")), token, "image/png", "foto.png"), "fotos");
        erro(navegador.multipart(campos(UUID.randomUUID().toString()), List.of(pngComTamanho(LIMITE + 1)), token, "image/png", "foto.png"), "fotos");
    }

    @Test
    void nenhumCampoDoClienteDefineAtorProtocoloInstanteOuSituacao() throws Exception {
        var navegador = autenticado();
        String token = csrf(navegador.get("/ocorrencias/novo").body());
        var campos = campos(UUID.randomUUID().toString());
        var outro = novaConta(true);
        String forjado = UUID.randomUUID().toString();
        campos.put("representanteId", outro.getId().toString());
        campos.put("protocolo", forjado);
        campos.put("registradaEm", "2000-01-01T00:00:00Z");
        campos.put("situacao", "CONFIRMADA");
        var resposta = navegador.multipart(campos, List.of(imagem("png")), token, "image/png", "foto.png");
        assertThat(resposta.statusCode()).isEqualTo(200);
        var salvo = service.buscar(UUID.fromString(json(resposta.body(), "protocolo")));
        assertThat(salvo.getProtocolo().toString()).isNotEqualTo(forjado);
        assertThat(salvo.getRepresentanteId()).isEqualTo(representante.getId());
        assertThat(salvo.getRegistradaEm()).isAfter(java.time.Instant.parse("2000-01-01T00:00:00Z"));
        assertThat(salvo.getSituacao()).isEqualTo("Em verificação");
    }

    @Test
    void csrfAusenteOuInvalidoNaoRegistra() throws Exception {
        var navegador = autenticado();
        assertThat(navegador.multipart(campos(UUID.randomUUID().toString()), List.of(imagem("png")), null, "image/png", "foto.png").statusCode()).isEqualTo(403);
        assertThat(navegador.multipart(campos(UUID.randomUUID().toString()), List.of(imagem("png")), "forjado", "image/png", "foto.png").statusCode()).isEqualTo(403);
        assertThat(quantidadeOcorrencias()).isZero();
        assertThat(acoes()).isEmpty();
    }

    @Test
    void todasRotasNegamAnonimoAdministradorTemporariaESessaoRevogada() throws Exception {
        var salvo = service.registrar(dados(UUID.randomUUID()));
        var antes = acoes();
        var anonimo = new Navegador();
        negarRotas(anonimo, salvo.getProtocolo(), csrf(anonimo.get("/entrar").body()), true);
        var temporario = novaConta(false);
        var primeiroAcesso = new Navegador();
        primeiroAcesso.entrar("/entrar", temporario.getLogin(), TEMPORARIA);
        negarRotas(primeiroAcesso, salvo.getProtocolo(), csrf(primeiroAcesso.get("/trocar-senha").body()), false);
        administrador();
        var admin = new Navegador();
        admin.entrar("/admin/entrar", loginAdmin, DEFINITIVA);
        negarRotas(admin, salvo.getProtocolo(), csrf(admin.get("/admin/representantes").body()), false);
        var revogado = autenticado();
        String token = csrf(revogado.get("/ocorrencias/novo").body());
        representantes.desativar(representante.getId());
        negarRotas(revogado, salvo.getProtocolo(), token, true);
        assertThat(quantidadeOcorrencias()).isEqualTo(1);
        assertThat(quantidadeFotos()).isEqualTo(1);
        assertThat(acoes()).containsExactlyElementsOf(antes);
    }

    @Test
    void rotasAusentesRetornam404EMetodosDeMutacaoNaoAlteramNemExcluem() throws Exception {
        var navegador = autenticado();
        String token = csrf(navegador.get("/ocorrencias/novo").body());
        var salvo = service.registrar(dados(UUID.randomUUID()));
        String detalhe = "/ocorrencias/" + salvo.getProtocolo();
        assertThat(navegador.get("/ocorrencias/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
        assertThat(navegador.get(detalhe + "/fotos/2").statusCode()).isEqualTo(404);
        assertThat(navegador.get("/ocorrencias/" + UUID.randomUUID() + "/fotos/1").statusCode()).isEqualTo(404);
        byte[] antes = service.foto(salvo.getProtocolo(), 1).getConteudo();
        for (String metodo : List.of("PATCH", "PUT", "DELETE")) {
            for (String rota : List.of(detalhe, detalhe + "/fotos/1"))
                assertThat(navegador.form(metodo, rota, Map.of("_csrf", token, "descricao", "alterada")).statusCode()).isIn(403, 404, 405);
        }
        for (String rota : List.of(detalhe + "/excluir", detalhe + "/editar", detalhe + "/fotos/1/excluir"))
            assertThat(navegador.form("POST", rota, Map.of("_csrf", token)).statusCode()).isIn(403, 404, 405);
        assertThat(service.buscar(salvo.getProtocolo()).getDescricao()).isEqualTo("Descrição fictícia");
        assertThat(service.foto(salvo.getProtocolo(), 1).getConteudo()).containsExactly(antes);
        assertThat(quantidadeOcorrencias()).isEqualTo(1);
        assertThat(acoes()).hasSize(1);
    }

    private void negarRotas(Navegador navegador, UUID protocolo, String token, boolean permiteRedirecionar) throws Exception {
        for (String rota : List.of("/ocorrencias/novo", "/ocorrencias/" + protocolo, "/ocorrencias/" + protocolo + "/fotos/1")) {
            int status = navegador.get(rota).statusCode();
            if (permiteRedirecionar) assertThat(status).isIn(302, 303, 403);
            else assertThat(status).isEqualTo(403);
        }
        assertThat(navegador.multipart(campos(UUID.randomUUID().toString()), List.of(imagem("png")), token, "image/png", "foto.png").statusCode()).isEqualTo(403);
    }

    private void erro(HttpResponse<String> resposta, String campo) {
        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.headers().firstValue("Content-Type").orElseThrow()).contains("application/json");
        assertThat(json(resposta.body(), "campo")).isEqualTo(campo);
        assertThat(json(resposta.body(), "erro")).isNotBlank();
        assertThat(quantidadeOcorrencias()).isZero();
        assertThat(quantidadeFotos()).isZero();
        assertThat(acoes()).isEmpty();
    }

    private Map<String, String> campos(String envio) {
        return new LinkedHashMap<>(Map.of("envioId", envio, "tipo", "DESASTRE", "descricao", "Descrição fictícia", "localizacao", "Ponte fictícia", "areaId", Long.toString(area)));
    }

    private Navegador autenticado() throws Exception {
        var navegador = new Navegador();
        assertThat(navegador.entrar("/entrar", representante.getLogin(), DEFINITIVA).statusCode()).isIn(302, 303);
        assertThat(navegador.get("/ocorrencias/novo").statusCode()).isEqualTo(200);
        return navegador;
    }

    private static String json(String corpo, String campo) {
        var matcher = Pattern.compile("\"" + campo + "\"\\s*:\\s*\"([^\"]+)\"").matcher(corpo);
        assertThat(matcher.find()).as("JSON deve conter %s: %s", campo, corpo).isTrue();
        return matcher.group(1);
    }

    private static String csrf(String html) {
        var inputs = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        while (inputs.find()) {
            String tag = inputs.group();
            if ("_csrf".equals(atributo(tag, "name"))) {
                String valor = atributo(tag, "value");
                assertThat(valor).isNotBlank();
                return valor;
            }
        }
        throw new AssertionError("Formulário não contém CSRF");
    }

    private static String atributo(String tag, String nome) {
        var atributo = Pattern.compile("\\b" + nome + "\\s*=\\s*([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE).matcher(tag);
        return atributo.find() ? atributo.group(2) : null;
    }

    private class Navegador {
        final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest.Builder request(String path) { return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(25)); }
        HttpResponse<String> get(String path) throws Exception { return client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString()); }
        HttpResponse<byte[]> bytes(String path) throws Exception { return client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofByteArray()); }
        HttpResponse<String> entrar(String path, String login, String senha) throws Exception {
            return form("POST", path, Map.of("login", login, "senha", senha, "_csrf", csrf(get(path).body())));
        }
        HttpResponse<String> form(String metodo, String path, Map<String, String> campos) throws Exception {
            String body = campos.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
            return client.send(request(path).header("Content-Type", "application/x-www-form-urlencoded").method(metodo, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> multipart(Map<String, String> campos, List<byte[]> fotos, String token, String mime, String nome) throws Exception {
            String boundary = "rf006" + UUID.randomUUID();
            var body = new ByteArrayOutputStream();
            for (var campo : campos.entrySet()) {
                body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + campo.getKey() + "\"\r\n\r\n" + campo.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
            for (byte[] foto : fotos) {
                body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"fotos\"; filename=\"" + nome + "\"\r\nContent-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                body.write(foto);
                body.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            var request = request("/ocorrencias").header("Content-Type", "multipart/form-data; boundary=" + boundary);
            if (token != null) request.header("X-CSRF-TOKEN", token);
            return client.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
