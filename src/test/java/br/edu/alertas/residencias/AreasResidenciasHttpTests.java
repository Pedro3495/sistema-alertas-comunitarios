package br.edu.alertas.residencias;

import br.edu.alertas.administracao.AdministradorService;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AreasResidenciasHttpTests {
    private static final Instant AGORA = Instant.parse("2026-11-22T12:00:00Z");
    private static final String TEMPORARIA = "temporaria-residencias-ficticia";
    private static final String DEFINITIVA = "definitiva-residencias-ficticia";
    private static final Pattern INPUT = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    @LocalServerPort int port;
    @Autowired RepresentanteService representantes;
    @Autowired AutenticacaoRepresentanteService autenticacao;
    @Autowired AdministradorService administradores;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean Clock clock;

    private final List<Long> representantesCriados = new ArrayList<>();
    private final List<String> nomesAreas = new ArrayList<>();
    private final List<String> ruasCriadas = new ArrayList<>();
    private Representante ator;
    private String loginAdminCriado;

    @BeforeEach
    void preparaRepresentanteComSenhaDefinitivaCommitada() {
        when(clock.instant()).thenReturn(AGORA);
        ator = representante(true);
    }

    @AfterEach
    void removeApenasFixturesDesteTeste() {
        representantesCriados.forEach(id -> jdbc.update("DELETE FROM registros_auditoria WHERE responsavel_tipo = 'REPRESENTANTE' AND responsavel_id = ?", id));
        ruasCriadas.forEach(rua -> jdbc.update("DELETE FROM residencias WHERE rua_logradouro = ?", rua));
        nomesAreas.forEach(nome -> jdbc.update("DELETE FROM areas_risco WHERE nome = ?", nome));
        representantesCriados.forEach(id -> jdbc.update("DELETE FROM representantes WHERE id = ?", id));
        if (loginAdminCriado != null) {
            jdbc.update("DELETE FROM administradores WHERE id = 1 AND login = ?", loginAdminCriado);
        }
    }

    @Test
    void percorreCadastroPesquisaCorrecaoTransferenciaManualEExclusaoComAuditoria() throws Exception {
        Navegador navegador = autenticado(ator);
        long origem = area(navegador, "Área Origem");
        long destino = area(navegador, "Área Destino");
        String rua = rua("Rua Sem Número");
        long id = residencia(navegador, dados(rua, "s/n", "Bairro A", "Município A", "Casa dos fundos", "Ao lado da praça", origem));
        assertThat(jdbc.queryForObject("SELECT numero FROM residencias WHERE id = ?", String.class, id)).isEqualTo("s/n");
        var edicao = navegador.get(editarResidencia(id));
        assertThat(edicao.statusCode()).isEqualTo(200);
        assertThat(edicao.body()).contains(rua, "Casa dos fundos", "Ao lado da praça");

        caminho(navegador.postComCsrf(editarArea(origem), navegador.get(editarArea(origem)).body(),
                Map.of("nome", nomeDaArea(origem), "descricaoLocalizacaoLimites", "Novos limites revisados manualmente")), "/areas");
        assertThat(areaDaResidencia(id)).isEqualTo(origem);
        caminho(navegador.postComCsrf(editarResidencia(id), navegador.get(editarResidencia(id)).body(),
                dados(rua, "20-A", "Bairro Revisto", "Município A", "", "", destino)), "/residencias");
        assertThat(areaDaResidencia(id)).isEqualTo(destino);
        assertThat(jdbc.queryForObject("SELECT numero FROM residencias WHERE id = ?", String.class, id)).isEqualTo("20-A");
        assertThat(navegador.get("/residencias?" + query(Map.of("rua", rua, "numero", "20-A", "bairro", "revisto", "municipio", "Município A", "areaId", Long.toString(destino)))).body()).contains(editarResidencia(id));

        int antes = acoes().size();
        var semConfirmar = navegador.postComCsrf(excluirResidencia(id), navegador.get(editarResidencia(id)).body(), Map.of());
        assertThat(semConfirmar.statusCode()).isEqualTo(200);
        assertThat(existeResidencia(id)).isTrue();
        assertThat(acoes()).hasSize(antes);
        caminho(navegador.postComCsrf(excluirResidencia(id), semConfirmar.body(), Map.of("confirmacao", "true")), "/residencias");
        assertThat(existeResidencia(id)).isFalse();
        caminho(navegador.postComCsrf(excluirArea(origem), navegador.get(editarArea(origem)).body(), Map.of("confirmacao", "true")), "/areas");
        assertThat(existeArea(origem)).isFalse();
        for (String acao : List.of("CADASTRAR_AREA", "ALTERAR_AREA", "EXCLUIR_AREA", "CADASTRAR_RESIDENCIA", "ALTERAR_RESIDENCIA", "EXCLUIR_RESIDENCIA")) {
            assertThat(acoes()).anySatisfy(registro -> assertThat(registro).contains(acao));
        }
        assertThat(jdbc.queryForList("SELECT DISTINCT responsavel_id FROM registros_auditoria WHERE responsavel_tipo = 'REPRESENTANTE' AND responsavel_id = ?", Long.class, ator.getId())).containsExactly(ator.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ruaLogradouro", "numero", "bairro", "municipio"})
    void camposObrigatoriosRejeitamVazioPreservandoDemaisDadosSemAuditar(String campo) throws Exception {
        Navegador navegador = autenticado(ator);
        long area = area(navegador, "Área Obrigatórios");
        Map<String, String> formulario = new LinkedHashMap<>(dados(rua("Rua Preservada"), "s/n", "Bairro Preservado", "Município Preservado", "Complemento Preservado", "Referência Preservada", area));
        formulario.put(campo, " ");
        int antes = acoes().size();
        var erro = navegador.postComCsrf("/residencias/novo", navegador.get("/residencias/novo").body(), formulario);
        assertThat(erro.statusCode()).isEqualTo(200);
        assertThat(erro.body()).contains("Complemento Preservado", "Referência Preservada", "role=\"alert\"");
        assertThat(acoes()).hasSize(antes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nome", "descricaoLocalizacaoLimites"})
    void obrigatoriosDaAreaPreservamOutroCampoSemAuditar(String campo) throws Exception {
        Navegador navegador = autenticado(ator);
        String nome = "Área Obrigatória " + UUID.randomUUID();
        nomesAreas.add(nome);
        Map<String, String> formulario = new LinkedHashMap<>(Map.of("nome", nome,
                "descricaoLocalizacaoLimites", "Localização preservada"));
        formulario.put(campo, " ");
        List<String> antes = acoes();
        var erro = navegador.postComCsrf("/areas/novo", navegador.get("/areas/novo").body(), formulario);
        assertThat(erro.statusCode()).isEqualTo(200);
        assertThat(erro.body()).contains("role=\"alert\"");
        assertThat(erro.body()).contains(campo.equals("nome") ? "Localização preservada" : nome);
        assertThat(acoes()).containsExactlyElementsOf(antes);
    }

    @Test
    void formularioSemAreasOfereceCadastroSemInserirUmaAreaFicticia() throws Exception {
        Navegador navegador = autenticado(ator);
        int antes = jdbc.queryForObject("SELECT count(*) FROM areas_risco", Integer.class);
        assertThat(antes).isZero();
        var resposta = navegador.get("/residencias/novo");
        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).contains("/areas/novo");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM areas_risco", Integer.class)).isEqualTo(antes);
        assertThat(acoes()).isEmpty();
    }

    @Test
    void areaObrigatoriaDesaparecidaENumerosDeRegistrosInexistentesSaoTratados() throws Exception {
        Navegador navegador = autenticado(ator);
        long area = area(navegador, "Área Que Será Removida");
        String formulario = navegador.get("/residencias/novo").body();
        caminho(navegador.postComCsrf(excluirArea(area), navegador.get(editarArea(area)).body(), Map.of("confirmacao", "true")), "/areas");
        int antes = acoes().size();
        var erro = navegador.postComCsrf("/residencias/novo", formulario,
                dados(rua("Rua De Área Ausente"), "1", "Bairro", "Município", "Dados preservados", "", area));
        assertThat(erro.statusCode()).isEqualTo(200);
        assertThat(erro.body()).contains("Dados preservados");
        assertThat(acoes()).hasSize(antes);
        assertThat(navegador.get(editarArea(Long.MAX_VALUE)).statusCode()).isEqualTo(404);
        assertThat(navegador.get(editarResidencia(Long.MAX_VALUE)).statusCode()).isEqualTo(404);
    }

    @Test
    void filtrosDeResidenciaUsamAndETratamPercentualSublinhadoEBarraComoLiterais() throws Exception {
        Navegador navegador = autenticado(ator);
        long area = area(navegador, "Área Filtros");
        String literal = rua("Rua 50%_\\Setor");
        String parecida = rua("Rua 50XXSetor");
        long idLiteral = residencia(navegador, dados(literal, "1A", "Bairro Literal", "Cidade Literal", "", "", area));
        residencia(navegador, dados(parecida, "1A", "Bairro Literal", "Cidade Literal", "", "", area));
        String busca = navegador.get("/residencias?" + query(Map.of("rua", "50%_\\", "numero", "1a", "bairro", "LITERAL", "municipio", "cidade", "areaId", Long.toString(area)))).body();
        assertThat(busca).contains(literal).doesNotContain(parecida);
        assertThat(navegador.get("/residencias?" + query(Map.of("rua", literal, "numero", "outro"))).body()).doesNotContain(editarResidencia(idLiteral));
        assertThat(navegador.get("/areas?" + query(Map.of("nome", nomeDaArea(area)))).body()).contains(editarArea(area));
    }

    @Test
    void exclusaoDeAreaVinculadaFalhaSemPerderResidenciaNemCriarAuditoriaDeExclusao() throws Exception {
        Navegador navegador = autenticado(ator);
        long area = area(navegador, "Área Vinculada");
        long residencia = residencia(navegador, dados(rua("Rua Vinculada"), "2", "Bairro", "Município", "", "", area));
        List<String> antes = acoes();
        var resposta = navegador.postComCsrf(excluirArea(area), navegador.get(editarArea(area)).body(), Map.of("confirmacao", "true"));
        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(existeArea(area)).isTrue();
        assertThat(areaDaResidencia(residencia)).isEqualTo(area);
        assertThat(acoes()).containsExactlyElementsOf(antes);
    }

    @Test
    void csrfConfirmacaoEPostExclusivoProtegemMutacoesDeAmbosOsCadastros() throws Exception {
        Navegador navegador = autenticado(ator);
        long area = area(navegador, "Área CSRF");
        long livre = area(navegador, "Área Sem Vínculo Para Confirmação");
        long residencia = residencia(navegador, dados(rua("Rua CSRF"), "3", "Bairro", "Cidade", "", "", area));
        List<String> antes = acoes();
        assertThat(navegador.post("/areas/novo", Map.of("nome", "Sem CSRF", "descricaoLocalizacaoLimites", "Sem CSRF")).statusCode()).isEqualTo(403);
        assertThat(navegador.post("/residencias/novo", dados(rua("Rua Sem CSRF"), "1", "Bairro", "Cidade", "", "", area)).statusCode()).isEqualTo(403);
        assertThat(navegador.get(excluirArea(area)).statusCode()).isIn(403, 404, 405);
        assertThat(navegador.get(excluirResidencia(residencia)).statusCode()).isIn(403, 404, 405);
        assertThat(navegador.post(excluirResidencia(residencia), Map.of("confirmacao", "true")).statusCode()).isEqualTo(403);
        var semConfirmar = navegador.postComCsrf(excluirArea(livre), navegador.get(editarArea(livre)).body(), Map.of());
        assertThat(semConfirmar.statusCode()).isEqualTo(200);
        assertThat(acoes()).containsExactlyElementsOf(antes);
        assertThat(existeResidencia(residencia)).isTrue();
        assertThat(existeArea(area)).isTrue();
        assertThat(existeArea(livre)).isTrue();
    }

    @Test
    void anonimatoAdministradorETemporariaNaoDaoAcessoAosCadastros() throws Exception {
        Navegador anonimo = new Navegador();
        for (String path : List.of("/areas", "/areas/novo", "/residencias", "/residencias/novo")) {
            assertThat(anonimo.get(path).statusCode()).isIn(302, 303, 403);
        }
        Representante temporario = representante(false);
        Navegador primeiroAcesso = new Navegador();
        caminho(primeiroAcesso.entrar("/entrar", temporario.getLogin(), TEMPORARIA), "/trocar-senha");
        for (String path : List.of("/areas", "/residencias")) assertThat(primeiroAcesso.get(path).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM administradores", Integer.class)).isZero();
        loginAdminCriado = "admin-residencias-" + UUID.randomUUID();
        administradores.criarPrimeiro(loginAdminCriado, "admin-ficticio-residencias");
        Navegador admin = new Navegador();
        caminho(admin.entrar("/admin/entrar", loginAdminCriado, "admin-ficticio-residencias"), "/admin/representantes");
        for (String path : List.of("/areas", "/residencias")) assertThat(admin.get(path).statusCode()).isEqualTo(403);
        assertThat(acoes()).isEmpty();
    }

    @Test
    void correcaoDeLoginPreservaAtorDaAuditoriaEPatchNaoAlteraRegistro() throws Exception {
        Navegador navegador = autenticado(ator);
        long area = area(navegador, "Área Histórico");
        List<String> antes = acoes();
        representantes.alterar(ator.getId(), ator.getNomeCompleto(), ator.getEntidadePublica(), ator.getLogin() + "-corrigido");
        Navegador renovado = new Navegador();
        caminho(renovado.entrar("/entrar", ator.getLogin() + "-corrigido", DEFINITIVA), "/representante");
        String token = renovado.get(editarArea(area)).body();
        Long registro = jdbc.queryForObject("SELECT min(id) FROM registros_auditoria WHERE responsavel_tipo = 'REPRESENTANTE' AND responsavel_id = ?", Long.class, ator.getId());
        assertThat(renovado.enviar("PATCH", "/auditoria/" + registro, comCsrf(token, Map.of("acao", "forjada"))).statusCode()).isIn(403, 404, 405);
        assertThat(acoes()).containsExactlyElementsOf(antes);
        assertThat(jdbc.queryForObject("SELECT responsavel_id FROM registros_auditoria WHERE id = ?", Long.class, registro)).isEqualTo(ator.getId());
    }

    @Test
    void criacaoConcorrenteComExclusaoDeAreaNuncaConfirmaDoisResultadosIncompativeis() throws Exception {
        Navegador criador = autenticado(ator);
        Navegador excluidor = autenticado(ator);
        long area = area(criador, "Área Concorrência Cadastro");
        String rua = rua("Rua Concorrente Cadastro");
        String formCriar = criador.get("/residencias/novo").body();
        String formExcluir = excluidor.get(editarArea(area)).body();
        List<HttpResponse<String>> respostas = concorrer(
                () -> criador.postComCsrf("/residencias/novo", formCriar, dados(rua, "1", "Bairro", "Cidade", "", "", area)),
                () -> excluidor.postComCsrf(excluirArea(area), formExcluir, Map.of("confirmacao", "true")));
        validaCorrida(respostas);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM residencias r LEFT JOIN areas_risco a ON a.id = r.area_id WHERE a.id IS NULL", Integer.class)).isZero();
        boolean cadastrou = jdbc.queryForObject("SELECT count(*) FROM residencias WHERE rua_logradouro = ?", Integer.class, rua) == 1;
        assertThat(cadastrou && !existeArea(area)).isFalse();
        assertThat(acoes().stream().filter(a -> a.startsWith("EXCLUIR_AREA")).count()).isEqualTo(existeArea(area) ? 0 : 1);
        assertThat(acoes().stream().filter(a -> a.startsWith("CADASTRAR_RESIDENCIA")).count()).isEqualTo(cadastrou ? 1 : 0);
    }

    @Test
    void transferenciaConcorrenteComExclusaoDaAreaDestinoNaoDeixaResidenciaOrfa() throws Exception {
        Navegador transferidor = autenticado(ator);
        Navegador excluidor = autenticado(ator);
        long origem = area(transferidor, "Área Corrida Origem");
        long destino = area(transferidor, "Área Corrida Destino");
        String rua = rua("Rua Corrida Transferência");
        long id = residencia(transferidor, dados(rua, "7", "Bairro", "Cidade", "", "", origem));
        String formAlterar = transferidor.get(editarResidencia(id)).body();
        String formExcluir = excluidor.get(editarArea(destino)).body();
        List<HttpResponse<String>> respostas = concorrer(
                () -> transferidor.postComCsrf(editarResidencia(id), formAlterar, dados(rua, "7", "Bairro", "Cidade", "", "", destino)),
                () -> excluidor.postComCsrf(excluirArea(destino), formExcluir, Map.of("confirmacao", "true")));
        validaCorrida(respostas);
        long areaFinal = areaDaResidencia(id);
        assertThat(areaFinal).isIn(origem, destino);
        assertThat(existeArea(areaFinal)).isTrue();
        assertThat(acoes().stream().filter(a -> a.startsWith("EXCLUIR_AREA")).count()).isEqualTo(existeArea(destino) ? 0 : 1);
        assertThat(acoes().stream().filter(a -> a.startsWith("ALTERAR_RESIDENCIA")).count()).isEqualTo(areaFinal == destino ? 1 : 0);
    }

    private List<HttpResponse<String>> concorrer(Callable<HttpResponse<String>> primeira, Callable<HttpResponse<String>> segunda) throws Exception {
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { prontas.countDown(); assertThat(iniciar.await(10, TimeUnit.SECONDS)).isTrue(); return primeira.call(); });
            var b = executor.submit(() -> { prontas.countDown(); assertThat(iniciar.await(10, TimeUnit.SECONDS)).isTrue(); return segunda.call(); });
            assertThat(prontas.await(10, TimeUnit.SECONDS)).isTrue();
            iniciar.countDown();
            return List.of(a.get(25, TimeUnit.SECONDS), b.get(25, TimeUnit.SECONDS));
        }
    }

    private static void validaCorrida(List<HttpResponse<String>> respostas) {
        respostas.forEach(resposta -> assertThat(resposta.statusCode()).isIn(200, 302, 303));
        assertThat(respostas.stream().filter(r -> r.statusCode() == 302 || r.statusCode() == 303).count()).isEqualTo(1);
    }

    private Representante representante(boolean definitiva) {
        Representante conta = representantes.cadastrar("Pessoa Fictícia Residências", "Entidade de Teste", "residencias-rep-" + UUID.randomUUID(), TEMPORARIA);
        representantesCriados.add(conta.getId());
        if (definitiva) autenticacao.trocarSenha(autenticacao.autenticar(conta.getLogin(), TEMPORARIA).orElseThrow(), DEFINITIVA);
        return conta;
    }

    private Navegador autenticado(Representante conta) throws Exception {
        Navegador navegador = new Navegador();
        caminho(navegador.entrar("/entrar", conta.getLogin(), DEFINITIVA), "/representante");
        return navegador;
    }

    private long area(Navegador navegador, String prefixo) throws Exception {
        String nome = prefixo + " " + UUID.randomUUID();
        nomesAreas.add(nome);
        caminho(navegador.postComCsrf("/areas/novo", navegador.get("/areas/novo").body(), Map.of("nome", nome, "descricaoLocalizacaoLimites", "Descrição da localização e limites fictícios")), "/areas");
        return jdbc.queryForObject("SELECT id FROM areas_risco WHERE nome = ?", Long.class, nome);
    }

    private long residencia(Navegador navegador, Map<String, String> dados) throws Exception {
        caminho(navegador.postComCsrf("/residencias/novo", navegador.get("/residencias/novo").body(), dados), "/residencias");
        return jdbc.queryForObject("SELECT id FROM residencias WHERE rua_logradouro = ?", Long.class, dados.get("ruaLogradouro"));
    }

    private String rua(String prefixo) { String rua = prefixo + " " + UUID.randomUUID(); ruasCriadas.add(rua); return rua; }
    private String nomeDaArea(long id) { return jdbc.queryForObject("SELECT nome FROM areas_risco WHERE id = ?", String.class, id); }
    private long areaDaResidencia(long id) { return jdbc.queryForObject("SELECT area_id FROM residencias WHERE id = ?", Long.class, id); }
    private boolean existeResidencia(long id) { return jdbc.queryForObject("SELECT count(*) FROM residencias WHERE id = ?", Integer.class, id) == 1; }
    private boolean existeArea(long id) { return jdbc.queryForObject("SELECT count(*) FROM areas_risco WHERE id = ?", Integer.class, id) == 1; }
    private List<String> acoes() { return jdbc.queryForList("SELECT acao FROM registros_auditoria WHERE responsavel_tipo = 'REPRESENTANTE' AND responsavel_id = ? ORDER BY id", String.class, ator.getId()); }
    private static String editarArea(long id) { return "/areas/" + id + "/editar"; }
    private static String excluirArea(long id) { return "/areas/" + id + "/excluir"; }
    private static String editarResidencia(long id) { return "/residencias/" + id + "/editar"; }
    private static String excluirResidencia(long id) { return "/residencias/" + id + "/excluir"; }

    private static Map<String, String> dados(String rua, String numero, String bairro, String municipio, String complemento, String ponto, long area) {
        return Map.of("ruaLogradouro", rua, "numero", numero, "bairro", bairro, "municipio", municipio, "complemento", complemento, "pontoReferencia", ponto, "areaId", Long.toString(area));
    }

    private static void caminho(HttpResponse<String> resposta, String esperado) { assertThat(resposta.statusCode()).isIn(302, 303); assertThat(URI.create(resposta.headers().firstValue("location").orElseThrow()).getPath()).isEqualTo(esperado); }
    private static String query(Map<String, String> campos) { return campos.entrySet().stream().map(c -> URLEncoder.encode(c.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(c.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&")); }
    private static Map<String, String> comCsrf(String html, Map<String, String> campos) { Map<String, String> formulario = new LinkedHashMap<>(campos); formulario.put("_csrf", csrf(html)); return formulario; }
    private static String csrf(String html) {
        Matcher inputs = INPUT.matcher(html);
        while (inputs.find()) { String input = inputs.group(); if ("_csrf".equals(atributo(input, "name"))) { String token = atributo(input, "value"); assertThat(token).isNotBlank(); return token; } }
        throw new AssertionError("Formulário não contém input CSRF.");
    }
    private static String atributo(String tag, String nome) { Matcher atributo = Pattern.compile("\\b" + nome + "\\s*=\\s*([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE).matcher(tag); return atributo.find() ? atributo.group(2) : null; }

    private class Navegador {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();
        HttpResponse<String> get(String caminho) throws Exception { return client.send(HttpRequest.newBuilder(uri(caminho)).timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString()); }
        HttpResponse<String> entrar(String endpoint, String login, String senha) throws Exception { return postComCsrf(endpoint, get(endpoint).body(), Map.of("login", login, "senha", senha)); }
        HttpResponse<String> postComCsrf(String caminho, String html, Map<String, String> campos) throws Exception { return post(caminho, comCsrf(html, campos)); }
        HttpResponse<String> post(String caminho, Map<String, String> campos) throws Exception { return enviar("POST", caminho, campos); }
        HttpResponse<String> enviar(String metodo, String caminho, Map<String, String> campos) throws Exception { return client.send(HttpRequest.newBuilder(uri(caminho)).timeout(Duration.ofSeconds(20)).header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8").method(metodo, HttpRequest.BodyPublishers.ofString(query(campos))).build(), HttpResponse.BodyHandlers.ofString()); }
        private URI uri(String caminho) { return URI.create("http://localhost:" + port + caminho); }
    }
}
