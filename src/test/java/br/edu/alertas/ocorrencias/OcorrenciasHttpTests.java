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

    // RF006 regras 1, 4, 5 e 9 para o morador: login real por CPF, área da residência e rascunho por conta.
    @Test
    void moradorRegistraPeloNavegadorNaAreaDaResidenciaEVeSoOProprioRegistro() throws Exception {
        String cpf = novoMorador(area);
        long outraArea = novaArea();
        var morador = moradorAutenticado(cpf);
        String formulario = morador.get("/morador/ocorrencias/novo").body();
        assertThat(formulario).contains("Área RF006", "action=\"/morador/ocorrencias\"", "data-rascunho-chave=\"MORADOR:")
                .doesNotContain("Outra área RF006", "Selecione uma área cadastrada");
        var campos = campos(UUID.randomUUID().toString());
        campos.put("areaId", Long.toString(outraArea)); // forjado: deve ser ignorado
        var resposta = morador.multipart(campos, List.of(imagem("png")), csrf(formulario), "image/png", "foto.png");
        assertThat(resposta.statusCode()).isEqualTo(200);
        String protocolo = json(resposta.body(), "protocolo");
        assertThat(json(resposta.body(), "detalheUrl")).isEqualTo("/morador/ocorrencias/" + protocolo);
        var salvo = jdbc.queryForMap("SELECT area_id, morador_id, representante_id FROM ocorrencias WHERE protocolo = ?::uuid", protocolo);
        assertThat(salvo.get("area_id")).isEqualTo(area);
        assertThat(salvo.get("representante_id")).isNull();
        long moradorId = ((Number) salvo.get("morador_id")).longValue();
        assertThat(acoesDoMorador(moradorId)).containsExactly("REGISTRAR_OCORRENCIA #" + protocolo);

        var detalhe = morador.get("/morador/ocorrencias/" + protocolo);
        assertThat(detalhe.statusCode()).isEqualTo(200);
        assertThat(detalhe.body()).contains(protocolo, "Em verificação", "Área RF006", "data-rascunho-chave=\"MORADOR:" + moradorId,
                "/morador/ocorrencias/" + protocolo + "/fotos/1").doesNotContain("Representante responsável");
        assertThat(morador.bytes("/morador/ocorrencias/" + protocolo + "/fotos/1").body()).containsExactly(imagem("png"));

        // O representante consulta pelo protocolo sem quebrar por falta de representante autor.
        var representanteNavegador = autenticado();
        assertThat(representanteNavegador.get("/ocorrencias/" + protocolo).statusCode()).isEqualTo(200);
        assertThat(representanteNavegador.get("/morador/ocorrencias/novo").statusCode()).isNotEqualTo(200);

        var outro = moradorAutenticado(novoMorador(novaArea()));
        assertThat(outro.get("/morador/ocorrencias/" + protocolo).statusCode()).isEqualTo(404);
        assertThat(outro.bytes("/morador/ocorrencias/" + protocolo + "/fotos/1").statusCode()).isEqualTo(404);
    }

    // RF008 regras 1, 2 e 4 e pesquisa: vizinho vê o relato sem autor; representante vê nome e celular, sem CPF.
    @Test
    void consultaRegionalOcultaAutorParaMoradorEMostraNomeECelularAoRepresentante() throws Exception {
        String cpf = novoMorador(area);
        var autor = moradorAutenticado(cpf);
        var resposta = autor.multipart(campos(UUID.randomUUID().toString()), List.of(imagem("png")), csrf(autor.get("/morador/ocorrencias/novo").body()), "image/png", "foto.png");
        String protocolo = json(resposta.body(), "protocolo");
        var dadosAutor = jdbc.queryForMap("SELECT nome_completo, celular FROM moradores WHERE cpf = ?", cpf);
        String celular = (String) dadosAutor.get("celular");
        var deOutraArea = service.registrar(new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.OBRA, null, "D", "Outro local", novaArea(), List.of(imagem("png"))));

        var vizinho = moradorAutenticado(novoMorador(area));
        var lista = vizinho.get("/morador/ocorrencias");
        assertThat(lista.statusCode()).isEqualTo(200);
        assertThat(lista.body()).contains(protocolo, "Em verificação", "Ponte fictícia", "Todas as situações", "Não confirmada", "Encerrada")
                .doesNotContain(deOutraArea.getProtocolo().toString(), "name=\"areaId\"", cpf, celular);
        assertThat(vizinho.get("/morador/ocorrencias?tipo=OBRA").body()).doesNotContain(protocolo);
        assertThat(vizinho.get("/morador/ocorrencias?protocolo=" + protocolo).body()).contains("/morador/ocorrencias/" + protocolo);
        assertThat(vizinho.get("/morador/ocorrencias?protocolo=nao-e-protocolo").body()).contains("Protocolo inválido", "Nenhuma ocorrência encontrada.");
        var detalhe = vizinho.get("/morador/ocorrencias/" + protocolo);
        assertThat(detalhe.statusCode()).isEqualTo(200);
        assertThat(detalhe.body()).contains("Descrição fictícia", "Ponte fictícia", "Em verificação", "/fotos/1")
                .doesNotContain((String) dadosAutor.get("nome_completo"), cpf, celular, "data-rascunho-chave", "Ocorrência registrada");
        assertThat(vizinho.get("/morador/ocorrencias/" + deOutraArea.getProtocolo()).statusCode()).isEqualTo(404);

        var representanteNavegador = autenticado();
        var listaRep = representanteNavegador.get("/ocorrencias?areaId=" + area);
        assertThat(listaRep.body()).contains(protocolo, "name=\"areaId\"").doesNotContain(deOutraArea.getProtocolo().toString());
        assertThat(representanteNavegador.get("/ocorrencias?situacao=CONFIRMADA&areaId=" + area).body()).doesNotContain(protocolo);
        var detalheRep = representanteNavegador.get("/ocorrencias/" + protocolo).body();
        assertThat(detalheRep).contains("Autor do relato", (String) dadosAutor.get("nome_completo"), "Celular do autor", celular).doesNotContain(cpf);
        assertThat(representanteNavegador.get("/morador/ocorrencias").statusCode()).isNotEqualTo(200);
        assertThat(vizinho.get("/ocorrencias").statusCode()).isIn(302, 403);
    }

    @Test
    void moradorNaoUsaRotasDoRepresentanteENemEnviaSemCsrf() throws Exception {
        var morador = moradorAutenticado(novoMorador(area));
        var salvo = service.registrar(dados(UUID.randomUUID()));
        assertThat(morador.get("/ocorrencias/novo").statusCode()).isIn(302, 403);
        assertThat(morador.get("/ocorrencias/" + salvo.getProtocolo()).statusCode()).isIn(302, 403);
        assertThat(morador.multipart(campos(UUID.randomUUID().toString()), List.of(imagem("png")), null, "image/png", "foto.png").statusCode()).isEqualTo(403);
        var anonimo = new Navegador();
        anonimo.destino = "/morador/ocorrencias";
        assertThat(anonimo.get("/morador/ocorrencias/novo").statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ocorrencias WHERE morador_id IS NOT NULL AND area_id = ?", Long.class, area)).isZero();
    }

    // RF009: formulário real com CSRF; justificativa só na tela do representante; filtros do RF008 passam a achar Confirmada.
    @Test
    void representanteVerificaPeloFormularioEMoradorVeSoASituacao() throws Exception {
        String cpf = novoMorador(area);
        var autor = moradorAutenticado(cpf);
        String protocolo = json(autor.multipart(campos(UUID.randomUUID().toString()), List.of(imagem("png")), csrf(autor.get("/morador/ocorrencias/novo").body()), "image/png", "foto.png").body(), "protocolo");
        String rota = "/ocorrencias/" + protocolo + "/verificacao";

        var rep = autenticado();
        String detalhe = rep.get("/ocorrencias/" + protocolo).body();
        assertThat(detalhe).contains("Verificar ocorrência", "Justificativa interna", "Confirmar não envia alerta", "Nenhuma verificação registrada.");
        String token = csrf(detalhe);
        assertThat(rep.form("POST", rota, Map.of("situacao", "CONFIRMADA", "justificativa", "Sem CSRF")).statusCode()).isEqualTo(403);
        var vazia = rep.form("POST", rota, Map.of("_csrf", token, "situacao", "CONFIRMADA", "justificativa", "  "));
        assertThat(vazia.statusCode()).isEqualTo(400);
        assertThat(vazia.body()).contains("Informe a justificativa da verificação.");
        assertThat(rep.form("POST", rota, Map.of("_csrf", token, "situacao", "ENCERRADA", "justificativa", "x")).statusCode()).isEqualTo(400);
        assertThat(rep.form("POST", "/ocorrencias/" + UUID.randomUUID() + "/verificacao", Map.of("_csrf", token, "situacao", "CONFIRMADA", "justificativa", "x")).statusCode()).isEqualTo(404);

        var ok = rep.form("POST", rota, Map.of("_csrf", token, "situacao", "CONFIRMADA", "justificativa", "Vistoria fictícia interna"));
        assertThat(ok.statusCode()).isEqualTo(302);
        assertThat(ok.headers().firstValue("Location").orElseThrow()).endsWith("/ocorrencias/" + protocolo);
        assertThat(rep.get("/ocorrencias/" + protocolo).body()).contains("Verificação salva.", "Confirmada", "Vistoria fictícia interna", representante.getNomeCompleto());
        assertThat(rep.get("/ocorrencias?situacao=CONFIRMADA&areaId=" + area).body()).contains(protocolo);
        assertThat(acoes()).containsExactly("VERIFICAR_OCORRENCIA #" + protocolo + ": Em verificação -> Confirmada");

        // Morador (autor ou vizinho) acompanha a situação, sem justificativa nem formulário, e não consegue verificar.
        for (var morador : List.of(autor, moradorAutenticado(novoMorador(area)))) {
            var visto = morador.get("/morador/ocorrencias/" + protocolo).body();
            assertThat(visto).contains("Confirmada").doesNotContain("Vistoria fictícia interna", "Verificar ocorrência", "verificacao");
            int status = morador.form("POST", rota, Map.of("_csrf", csrf(morador.get("/morador/ocorrencias/novo").body()), "situacao", "NAO_CONFIRMADA", "justificativa", "Morador")).statusCode();
            assertThat(status).isIn(302, 403);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM verificacoes_ocorrencia v JOIN ocorrencias o ON o.id = v.ocorrencia_id WHERE o.protocolo = ?::uuid", Long.class, protocolo)).isEqualTo(1);
    }

    // RF010: emissão por formulário real com CSRF, morador da área vê o alerta e o aviso de alteração (RF009 regra 4).
    @Test
    void representanteEmiteAlertaEMoradorDaAreaVeAlertaEAvisoDeAlteracao() throws Exception {
        String cpf = novoMorador(area);
        var p = service.registrar(dados(UUID.randomUUID())).getProtocolo();
        String rota = "/ocorrencias/" + p + "/alertas";
        var rep = autenticado();
        assertThat(rep.get("/ocorrencias/" + p).body()).contains("A emissão de alerta exige ocorrência Confirmada.", "Envio simulado").doesNotContain("Emitir alerta");
        String token = csrf(rep.get("/ocorrencias/" + p).body());
        assertThat(rep.form("POST", rota, Map.of("_csrf", token, "titulo", "T", "mensagem", "M", "orientacoes", "O")).statusCode()).isEqualTo(400);
        service.verificar(p, SituacaoOcorrencia.CONFIRMADA, "Vistoria fictícia");

        assertThat(rep.get("/ocorrencias/" + p).body()).contains("Emitir alerta", "Orientações aos moradores *", "Nenhum alerta emitido.");
        assertThat(rep.form("POST", rota, Map.of("titulo", "T", "mensagem", "M", "orientacoes", "O")).statusCode()).isEqualTo(403);
        var vazio = rep.form("POST", rota, Map.of("_csrf", token, "titulo", "Título fictício", "mensagem", "Mensagem fictícia", "orientacoes", " "));
        assertThat(vazio.statusCode()).isEqualTo(400);
        assertThat(vazio.body()).contains("Informe as orientações aos moradores.", "Título fictício");
        assertThat(rep.form("POST", "/ocorrencias/" + UUID.randomUUID() + "/alertas", Map.of("_csrf", token, "titulo", "T", "mensagem", "M", "orientacoes", "O")).statusCode()).isEqualTo(404);

        var ok = rep.form("POST", rota, Map.of("_csrf", token, "titulo", "Alerta fictício", "mensagem", "Água subindo", "orientacoes", "Saia pela rua alta"));
        assertThat(ok.statusCode()).isEqualTo(302);
        assertThat(rep.get("/ocorrencias/" + p).body()).contains("Alerta enviado a todos os destinatários.", "Alerta: Alerta fictício",
                "1 destinatário(s), 0 com falha nos dois canais.").doesNotContain("Reenviar para quem falhou");
        assertThat(rep.form("POST", rota + "/999999999/reenvio", Map.of("_csrf", token)).statusCode()).isEqualTo(404);
        assertThat(acoes()).contains("EMITIR_ALERTA #" + p);

        var vizinho = moradorAutenticado(cpf);
        assertThat(vizinho.get("/morador/alertas").body()).contains("Alerta: Alerta fictício", "Água subindo", "Orientações: Saia pela rua alta", p.toString());
        assertThat(vizinho.form("POST", rota, Map.of("_csrf", csrf(vizinho.get("/morador/ocorrencias/novo").body()), "titulo", "X", "mensagem", "X", "orientacoes", "X")).statusCode()).isIn(302, 403);
        assertThat(moradorAutenticado(novoMorador(novaArea())).get("/morador/alertas").body()).contains("Nenhum alerta para você.").doesNotContain("Alerta fictício");
        assertThat(new Navegador().get("/morador/alertas").statusCode()).isIn(302, 403);

        var revisao = rep.form("POST", "/ocorrencias/" + p + "/verificacao", Map.of("_csrf", token, "situacao", "NAO_CONFIRMADA",
                "justificativa", "Justificativa interna fictícia", "orientacoes", "Pode voltar para casa"));
        assertThat(revisao.statusCode()).isEqualTo(302);
        assertThat(vizinho.get("/morador/alertas").body()).contains("Aviso de alteração: Alteração da ocorrência " + p,
                "Situação alterada de Confirmada para Não confirmada.", "Pode voltar para casa").doesNotContain("Justificativa interna fictícia");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comunicados_ocorrencia c JOIN ocorrencias o ON o.id = c.ocorrencia_id WHERE o.protocolo = ?", Long.class, p)).isEqualTo(2);
    }

    // RF011: alteração por formulário real com CSRF; aviso ao morador que recebeu o alerta, sem a justificativa interna.
    @Test
    void representanteAlteraCamposPermitidosEMoradorRecebeAvisoSemJustificativa() throws Exception {
        String cpf = novoMorador(area);
        var p = service.registrar(dados(UUID.randomUUID())).getProtocolo();
        String rota = "/ocorrencias/" + p + "/alteracao";
        var rep = autenticado();
        var tela = rep.get("/ocorrencias/" + p).body();
        assertThat(tela).contains("Alterar ocorrência", "Nenhuma alteração registrada.", "Ponte fictícia");
        String token = csrf(tela);
        Map<String, String> campos = new java.util.HashMap<>(Map.of("_csrf", token, "tipo", "DESASTRE", "descricao", "Descrição fictícia",
                "localizacao", "Rua corrigida", "areaId", String.valueOf(area), "justificativa", "Justificativa interna fictícia",
                "orientacoes", "Use a rua alta"));

        var semCsrf = new java.util.HashMap<>(campos);
        semCsrf.remove("_csrf");
        assertThat(rep.form("POST", rota, semCsrf).statusCode()).isEqualTo(403);
        var semJustificativa = new java.util.HashMap<>(campos);
        semJustificativa.put("justificativa", " ");
        var erro = rep.form("POST", rota, semJustificativa);
        assertThat(erro.statusCode()).isEqualTo(400);
        assertThat(erro.body()).contains("Informe a justificativa da alteração.", "Rua corrigida");
        assertThat(rep.form("POST", "/ocorrencias/" + UUID.randomUUID() + "/alteracao", campos).statusCode()).isEqualTo(404);

        service.verificar(p, SituacaoOcorrencia.CONFIRMADA, "Vistoria fictícia");
        rep.form("POST", "/ocorrencias/" + p + "/alertas", Map.of("_csrf", token, "titulo", "Alerta fictício", "mensagem", "M", "orientacoes", "O"));
        assertThat(rep.get("/ocorrencias/" + p).body()).contains("Alerta enviado a todos os destinatários."); // consome o aviso da emissão
        var ok = rep.form("POST", rota, campos);
        assertThat(ok.statusCode()).isEqualTo(302);
        assertThat(rep.get("/ocorrencias/" + p).body()).contains("Alteração salva.", "Localização: de &quot;Ponte fictícia&quot; para &quot;Rua corrigida&quot;.",
                "Justificativa: Justificativa interna fictícia");
        assertThat(acoes()).contains("ALTERAR_OCORRENCIA #" + p + ": Localização: de \"Ponte fictícia\" para \"Rua corrigida\".");

        var vizinho = moradorAutenticado(cpf);
        assertThat(vizinho.get("/morador/alertas").body()).contains("Aviso de alteração: Alteração da ocorrência " + p,
                "Rua corrigida", "Use a rua alta").doesNotContain("Justificativa interna fictícia");
        var detalhe = vizinho.get("/morador/ocorrencias/" + p).body();
        assertThat(detalhe).contains("Rua corrigida").doesNotContain("Alterar ocorrência", "Justificativa interna fictícia");
        var tentativa = new java.util.HashMap<>(campos);
        tentativa.put("_csrf", csrf(vizinho.get("/morador/ocorrencias/novo").body()));
        tentativa.put("localizacao", "Morador tentou");
        assertThat(vizinho.form("POST", rota, tentativa).statusCode()).isIn(302, 403);
        assertThat(service.buscar(p).getLocalizacao()).isEqualTo("Rua corrigida");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fotos_ocorrencia f JOIN ocorrencias o ON o.id = f.ocorrencia_id WHERE o.protocolo = ?", Long.class, p)).isEqualTo(1);
    }

    // RF012: encerramento por formulário real com CSRF; aviso ao morador que recebeu o alerta, sem a justificativa interna.
    @Test
    void representanteEncerraEMoradorRecebeAvisoSemJustificativaESemNovasAlteracoes() throws Exception {
        String cpf = novoMorador(area);
        var p = service.registrar(dados(UUID.randomUUID())).getProtocolo();
        String rota = "/ocorrencias/" + p + "/encerramento";
        var rep = autenticado();
        String token = csrf(rep.get("/ocorrencias/" + p).body());
        service.verificar(p, SituacaoOcorrencia.CONFIRMADA, "Vistoria fictícia");
        rep.form("POST", "/ocorrencias/" + p + "/alertas", Map.of("_csrf", token, "titulo", "Alerta fictício", "mensagem", "M", "orientacoes", "O"));
        assertThat(rep.get("/ocorrencias/" + p).body()).contains("Encerrar ocorrência", "Encerrar e notificar");
        Map<String, String> campos = new java.util.HashMap<>(Map.of("_csrf", token, "justificativa", "Justificativa interna fictícia",
                "resumo", "O trecho já não apresenta risco.", "orientacoes", "Volte para casa"));

        var semCsrf = new java.util.HashMap<>(campos);
        semCsrf.remove("_csrf");
        assertThat(rep.form("POST", rota, semCsrf).statusCode()).isEqualTo(403);
        var semResumo = new java.util.HashMap<>(campos);
        semResumo.put("resumo", "");
        var erro = rep.form("POST", rota, semResumo);
        assertThat(erro.statusCode()).isEqualTo(400);
        assertThat(erro.body()).contains("Informe o resumo do motivo para o aviso de encerramento.", "Justificativa interna fictícia");
        assertThat(rep.form("POST", "/ocorrencias/" + UUID.randomUUID() + "/encerramento", campos).statusCode()).isEqualTo(404);

        var vizinho = moradorAutenticado(cpf);
        var tentativa = new java.util.HashMap<>(campos);
        tentativa.put("_csrf", csrf(vizinho.get("/morador/ocorrencias/novo").body()));
        assertThat(vizinho.form("POST", rota, tentativa).statusCode()).isIn(302, 403);
        assertThat(service.buscar(p).getSituacao()).isEqualTo("Confirmada");

        assertThat(rep.form("POST", rota, campos).statusCode()).isEqualTo(302);
        var tela = rep.get("/ocorrencias/" + p).body();
        assertThat(tela).contains("Ocorrência encerrada.", "Encerrada · somente consulta", "Justificativa: Justificativa interna fictícia",
                "Aviso de encerramento: Encerramento da ocorrência " + p).doesNotContain("Encerrar e notificar", "Salvar alteração", "Salvar verificação");
        assertThat(acoes()).contains("ENCERRAR_OCORRENCIA #" + p + ": Confirmada -> Encerrada");
        assertThat(rep.form("POST", rota, campos).statusCode()).isEqualTo(400); // sem reabertura nem novo encerramento

        assertThat(vizinho.get("/morador/alertas").body()).contains("Aviso de encerramento: Encerramento da ocorrência " + p,
                "O trecho já não apresenta risco.", "Volte para casa").doesNotContain("Justificativa interna fictícia");
        assertThat(vizinho.get("/morador/ocorrencias/" + p).body()).contains("Encerrada").doesNotContain("Encerrar ocorrência", "Justificativa interna fictícia");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fotos_ocorrencia f JOIN ocorrencias o ON o.id = f.ocorrencia_id WHERE o.protocolo = ?", Long.class, p)).isEqualTo(1);
    }

    private Navegador moradorAutenticado(String cpf) throws Exception {
        var navegador = new Navegador();
        navegador.destino = "/morador/ocorrencias";
        var entrada = navegador.form("POST", "/morador/entrar", Map.of("cpf", cpf, "senha", DEFINITIVA, "_csrf", csrf(navegador.get("/morador/entrar").body())));
        assertThat(entrada.headers().firstValue("Location").orElseThrow()).endsWith("/morador");
        assertThat(navegador.get("/morador/ocorrencias/novo").statusCode()).isEqualTo(200);
        return navegador;
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
        String destino = "/ocorrencias";
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
            var request = request(destino).header("Content-Type", "multipart/form-data; boundary=" + boundary);
            if (token != null) request.header("X-CSRF-TOKEN", token);
            return client.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
