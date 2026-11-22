package br.edu.alertas.ocorrencias;

import br.edu.alertas.areas.AreaRiscoService;
import br.edu.alertas.auditoria.AuditoriaService;
import br.edu.alertas.representantes.AcessoRepresentante;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@SpringBootTest
class OcorrenciaServiceTests extends OcorrenciasFixtures {
    @Autowired AreaRiscoService areas;
    @Autowired PlatformTransactionManager transacoes;
    @MockitoSpyBean AuditoriaService auditoria;

    @AfterEach
    void restaurarAuditoria() { reset(auditoria); }

    @ParameterizedTest
    @EnumSource(TipoOcorrencia.class)
    void registraTiposComProtocoloInstanteSituacaoEAutorDoServidor(TipoOcorrencia tipo) {
        UUID envio = UUID.randomUUID();
        Instant antes = Instant.now();
        byte[] png = imagem("png"), jpeg = imagem("jpeg"), heic = heic(), heif = heif();
        var salvo = service.registrar(new DadosOcorrencia(envio, tipo, tipo == TipoOcorrencia.OUTROS ? "Outro evento" : null,
                "Descrição fictícia", "Ponto de referência", area, List.of(png, jpeg, heic, heif)));
        Instant depois = Instant.now();
        assertThat(salvo.getProtocolo()).isNotNull().isNotEqualTo(envio);
        assertThat(salvo.getRegistradaEm()).isBetween(antes, depois);
        assertThat(salvo.getRepresentanteId()).isEqualTo(representante.getId());
        assertThat(salvo.getEnvioId()).isEqualTo(envio);
        assertThat(salvo.getAreaId()).isEqualTo(area);
        assertThat(salvo.getSituacao()).isEqualTo("Em verificação");
        var lido = service.buscar(salvo.getProtocolo());
        assertThat(lido.getTipo()).isEqualTo(tipo);
        assertThat(lido.getDescricao()).isEqualTo("Descrição fictícia");
        assertThat(lido.getLocalizacao()).isEqualTo("Ponto de referência");
        assertThat(lido.getAreaNome()).startsWith("Área RF006");
        assertThat(lido.getRepresentanteNome()).isEqualTo(representante.getNomeCompleto());
        if (tipo == TipoOcorrencia.OUTROS) assertThat(lido.getEspecificacaoOutros()).isEqualTo("Outro evento");
        assertThat(lido.getFotos()).hasSize(4);
        for (int i = 0; i < 4; i++) {
            var foto = service.foto(salvo.getProtocolo(), i + 1);
            assertThat(foto.getOrdem()).isEqualTo(i + 1);
            if (i < 3) assertThat(foto.getTipoMidia()).isEqualTo(List.of("image/png", "image/jpeg", "image/heic").get(i));
            // O contêiner HEIF com brand mif1 conserva o codec e a compatibilidade HEIC originais.
            else assertThat(foto.getTipoMidia()).isIn("image/heic", "image/heif");
            assertThat(foto.getConteudo()).containsExactly(List.of(png, jpeg, heic, heif).get(i));
            byte[] copia = foto.getConteudo();
            copia[0] ^= 1;
            assertThat(foto.getConteudo()).containsExactly(List.of(png, jpeg, heic, heif).get(i));
            assertThat(service.foto(salvo.getProtocolo(), i + 1).getConteudo()).containsExactly(List.of(png, jpeg, heic, heif).get(i));
        }
        assertThat(quantidadeOcorrencias()).isEqualTo(1);
        assertThat(quantidadeFotos()).isEqualTo(4);
        assertThat(acoes()).containsExactly("REGISTRAR_OCORRENCIA #" + salvo.getProtocolo());
        assertThat(jdbc.queryForObject("SELECT ocorrido_em FROM registros_auditoria WHERE responsavel_tipo = 'REPRESENTANTE' AND responsavel_id = ?",
                java.sql.Timestamp.class, representante.getId()).toInstant()).isBetween(antes, depois);
    }

    @Test
    void aceitaQuatroFotosELimiteExatoSemImporMaximoDeTexto() {
        byte[] limite = pngComTamanho(LIMITE);
        String texto = "Texto fictício ".repeat(1000);
        var salvo = service.registrar(new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.OUTROS, texto, texto, texto,
                area, List.of(limite, imagem("jpeg"), imagem("png"), heic())));
        assertThat(service.foto(salvo.getProtocolo(), 1).getConteudo()).containsExactly(limite);
        assertThat(service.buscar(salvo.getProtocolo()).getDescricao()).isEqualTo(texto);
        assertThat(quantidadeFotos()).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"envioId", "tipo", "especificacaoOutros", "descricao", "localizacao", "areaId"})
    void obrigatoriosAusentesOuEmBrancoApontamCampoSemPersistir(String campo) {
        for (String vazio : Arrays.asList(null, "", " \t\n")) {
            var dados = new DadosOcorrencia(campo.equals("envioId") ? null : UUID.randomUUID(),
                    campo.equals("tipo") ? null : TipoOcorrencia.OUTROS,
                    campo.equals("especificacaoOutros") ? vazio : "Evento distinto", campo.equals("descricao") ? vazio : "Descrição",
                    campo.equals("localizacao") ? vazio : "Local", campo.equals("areaId") ? null : area, List.of(imagem("png")));
            erro(campo, dados);
        }
    }

    @Test
    void recusaAreaInexistenteOuRemovida() {
        UUID envio = UUID.randomUUID();
        erro("areaId", new DadosOcorrencia(envio, TipoOcorrencia.OBRA, null, "Descrição", "Local", Long.MAX_VALUE, List.of(imagem("png"))));
        areas.excluir(area);
        erro("areaId", dados(envio));
    }

    static Stream<Arguments> fotosInvalidas() {
        byte[] png = imagem("png");
        return Stream.of(
                Arguments.of((Object) null), Arguments.of(List.of()),
                Arguments.of(Arrays.asList((byte[]) null)), Arguments.of(List.of(new byte[0])),
                Arguments.of(Collections.nCopies(5, png)), Arguments.of(List.of(pngComTamanho(LIMITE + 1))),
                Arguments.of(List.of("imagem falsa".getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                Arguments.of(List.of(Arrays.copyOf(png, 20))),
                Arguments.of(List.of(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9})),
                Arguments.of(List.of(new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'})),
                Arguments.of(List.of(imagem("gif"))), Arguments.of(List.of(png, new byte[0])));
    }

    @ParameterizedTest
    @MethodSource("fotosInvalidas")
    void recusaQuantidadeTamanhoFormatosNaoAceitosEArquivosTruncados(List<byte[]> fotos) {
        erro("fotos", new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.DESASTRE, null, "Descrição", "Local", area, fotos));
    }

    @Test
    void replayConsultaChaveAntesDeValidarConteudoESemNovaAuditoria() {
        UUID envio = UUID.randomUUID();
        var salvo = service.registrar(dados(envio));
        var replay = service.registrar(new DadosOcorrencia(envio, null, null, null, null, null, null));
        assertThat(replay.getProtocolo()).isEqualTo(salvo.getProtocolo());
        assertThat(replay.getDescricao()).isEqualTo("Descrição fictícia");
        assertThat(quantidadeOcorrencias()).isEqualTo(1);
        assertThat(quantidadeFotos()).isEqualTo(1);
        assertThat(acoes()).containsExactly("REGISTRAR_OCORRENCIA #" + salvo.getProtocolo());
        var novo = service.registrar(dados(UUID.randomUUID()));
        assertThat(novo.getProtocolo()).isNotEqualTo(salvo.getProtocolo());
    }

    @Test
    void mesmaChaveDeRepresentantesDiferentesNaoCompartilhaOcorrencia() throws Exception {
        var outro = novaConta(true);
        var outroAtor = acesso.autenticar(outro.getLogin(), DEFINITIVA).orElseThrow();
        UUID envio = UUID.randomUUID();
        var resultados = concorrer(ator, () -> service.registrar(dados(envio)), outroAtor, () -> service.registrar(dados(envio)));
        assertThat(resultados.get(0).getProtocolo()).isNotEqualTo(resultados.get(1).getProtocolo());
        assertThat(resultados.get(0).getRepresentanteId()).isEqualTo(representante.getId());
        assertThat(resultados.get(1).getRepresentanteId()).isEqualTo(outro.getId());
        assertThat(quantidadeOcorrencias()).isEqualTo(2);
        assertThat(quantidadeFotos()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registros_auditoria WHERE responsavel_tipo = 'REPRESENTANTE' AND responsavel_id = ? AND acao = ?",
                Integer.class, outro.getId(), "REGISTRAR_OCORRENCIA #" + resultados.get(1).getProtocolo())).isEqualTo(1);
        autenticar(outroAtor, "REPRESENTANTE");
        assertThat(service.buscar(resultados.get(0).getProtocolo()).getAreaId()).isEqualTo(area);
    }

    @Test
    void replayConcorrenteRetornaMesmoProtocoloComUmaAuditoriaEUmaFoto() throws Exception {
        UUID envio = UUID.randomUUID();
        var resultados = concorrer(ator, () -> service.registrar(dados(envio)), ator, () -> service.registrar(dados(envio)));
        assertThat(resultados.get(0).getProtocolo()).isEqualTo(resultados.get(1).getProtocolo());
        assertThat(quantidadeOcorrencias()).isEqualTo(1);
        assertThat(quantidadeFotos()).isEqualTo(1);
        assertThat(acoes()).containsExactly("REGISTRAR_OCORRENCIA #" + resultados.get(0).getProtocolo());
    }

    @Test
    void falhaDeAuditoriaReverteOcorrenciaEFotosEPermiteTentarNovamente() {
        UUID envio = UUID.randomUUID();
        doThrow(new IllegalStateException("Falha sintética da auditoria")).when(AopTestUtils.<AuditoriaService>getUltimateTargetObject(auditoria))
                .registrar(startsWith("REGISTRAR_OCORRENCIA"));
        assertThatThrownBy(() -> service.registrar(dados(envio))).isInstanceOf(IllegalStateException.class);
        assertThat(quantidadeOcorrencias()).isZero();
        assertThat(quantidadeFotos()).isZero();
        assertThat(acoes()).isEmpty();
        reset(auditoria);
        assertThat(service.registrar(dados(envio)).getProtocolo()).isNotNull();
        assertThat(quantidadeOcorrencias()).isEqualTo(1);
        assertThat(quantidadeFotos()).isEqualTo(1);
        assertThat(acoes()).hasSize(1);
    }

    @Test
    void fksImpedemExcluirAreaRepresentanteEOcorrenciaComEvidencia() {
        var salvo = service.registrar(dados(UUID.randomUUID()));
        assertThatThrownBy(() -> areas.excluir(area)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM areas_risco WHERE id = ?", area)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM representantes WHERE id = ?", representante.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ocorrencias WHERE id = ?", salvo.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE ocorrencias SET area_id = ? WHERE id = ?", Long.MAX_VALUE, salvo.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE fotos_ocorrencia SET ocorrencia_id = ? WHERE ocorrencia_id = ?", Long.MAX_VALUE, salvo.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(service.buscar(salvo.getProtocolo()).getAreaId()).isEqualTo(area);
        assertThat(quantidadeFotos()).isEqualTo(1);
    }

    @Test
    void registroConcorrenteComExclusaoDeAreaNaoConfirmaResultadosIncompativeis() throws Exception {
        var resultados = concorrer(ator, () -> tentar(() -> service.registrar(dados(UUID.randomUUID()))),
                ator, () -> tentar(() -> { areas.excluir(area); return null; }));
        assertThat(resultados).containsExactlyInAnyOrder(true, false);
        boolean existe = jdbc.queryForObject("SELECT count(*) FROM areas_risco WHERE id = ?", Integer.class, area) == 1;
        assertThat(quantidadeOcorrencias()).isEqualTo(existe ? 1 : 0);
        assertThat(quantidadeFotos()).isEqualTo(existe ? 1 : 0);
        assertThat(acoes()).hasSize(existe ? 1 : 0);
    }

    @Test
    void autorizacaoDeTodasOperacoesIncluiReplayESessaoRevogada() {
        var salvo = service.registrar(dados(UUID.randomUUID()));
        var antes = acoes();
        SecurityContextHolder.clearContext();
        negarOperacoes(salvo);
        autenticar(administrador(), "ADMINISTRADOR");
        negarOperacoes(salvo);
        autenticar(new AcessoRepresentante(ator.id(), ator.login(), ator.versaoCredencial(), true), "TROCAR_SENHA");
        negarOperacoes(salvo);
        autenticar("principal-forjado", "REPRESENTANTE");
        negarOperacoes(salvo);
        autenticar(ator, "REPRESENTANTE");
        representantes.desativar(representante.getId());
        negarOperacoes(salvo);
        assertThat(acoes()).containsExactlyElementsOf(antes);
        assertThat(quantidadeOcorrencias()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void revalidaSessaoAposEsperarLockDeDesativacaoOuRedefinicao(boolean desativar) throws Exception {
        String marcador = "rf006_lock_" + UUID.randomUUID().toString().replace("-", "");
        var executor = Executors.newSingleThreadExecutor();
        var futuro = new java.util.concurrent.atomic.AtomicReference<Future<Throwable>>();
        try {
            new TransactionTemplate(transacoes).executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT id FROM representantes WHERE id = ? FOR UPDATE", Long.class, representante.getId());
                futuro.set(executor.submit(() -> {
                    autenticar(ator, "REPRESENTANTE");
                    try {
                        new TransactionTemplate(transacoes).executeWithoutResult(espera -> {
                            jdbc.queryForObject("SELECT set_config('application_name', ?, true)", String.class, marcador);
                            jdbc.execute("SET LOCAL lock_timeout = '15s'");
                            service.registrar(dados(UUID.randomUUID()));
                        });
                        return null;
                    } catch (RuntimeException e) { return e; }
                    finally { SecurityContextHolder.clearContext(); }
                }));
                // Só revoga depois de observar espera real no PostgreSQL, após a pré-validação.
                await().pollInterval(java.time.Duration.ofMillis(50)).atMost(java.time.Duration.ofSeconds(10)).until(() ->
                        Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE application_name = ? AND wait_event_type = 'Lock')", Boolean.class, marcador)));
                if (desativar) representantes.desativar(representante.getId());
                else representantes.redefinirSenhaTemporaria(representante.getId(), "nova-temporaria-ficticia");
            });
            assertThat(futuro.get().get(20, TimeUnit.SECONDS)).isInstanceOf(AccessDeniedException.class);
            assertThat(quantidadeOcorrencias()).isZero();
            assertThat(quantidadeFotos()).isZero();
            assertThat(acoes()).isEmpty();
        } finally { executor.shutdownNow(); }
    }

    @Test
    void consultaAusenteNaoMutaENaoExpoeOperacoesDeAlteracaoOuExclusao() {
        var salvo = service.registrar(dados(UUID.randomUUID()));
        assertThatThrownBy(() -> service.buscar(UUID.randomUUID())).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.foto(UUID.randomUUID(), 1)).isInstanceOf(EntityNotFoundException.class);
        for (int ordem : List.of(0, 2, 5)) assertThatThrownBy(() -> service.foto(salvo.getProtocolo(), ordem)).isInstanceOf(EntityNotFoundException.class);
        assertThat(Arrays.stream(OcorrenciaService.class.getMethods()).map(m -> m.getName()))
                .noneMatch(nome -> nome.matches("(?i)(excluir|remover|deletar|alterar|substituir).*"));
        assertThat(quantidadeFotos()).isEqualTo(1);
        assertThat(acoes()).hasSize(1);
    }

    private void negarOperacoes(Ocorrencia salvo) {
        for (Runnable operacao : List.<Runnable>of(() -> service.registrar(dados(UUID.randomUUID())),
                () -> service.registrar(dados(salvo.getEnvioId())), () -> service.buscar(salvo.getProtocolo()), () -> service.foto(salvo.getProtocolo(), 1)))
            assertThatThrownBy(operacao::run).isInstanceOf(AccessDeniedException.class);
    }

    private void erro(String campo, DadosOcorrencia dados) {
        assertThatThrownBy(() -> service.registrar(dados)).isInstanceOfSatisfying(ErroOcorrencia.class, erro -> {
            assertThat(erro.getCampo()).isEqualTo(campo);
            assertThat(erro.getMessage()).isNotBlank();
        });
        assertThat(quantidadeOcorrencias()).isZero();
        assertThat(quantidadeFotos()).isZero();
        assertThat(acoes()).isEmpty();
    }

    private boolean tentar(Supplier<?> operacao) {
        try { operacao.get(); return true; }
        catch (IllegalArgumentException | DataIntegrityViolationException esperado) { return false; }
    }

    private <T> List<T> concorrer(AcessoRepresentante primeiroAtor, Supplier<T> primeira,
                                 AcessoRepresentante segundoAtor, Supplier<T> segunda) throws Exception {
        var prontas = new CountDownLatch(2);
        var largada = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> executar(primeiroAtor, primeira, prontas, largada));
            var b = executor.submit(() -> executar(segundoAtor, segunda, prontas, largada));
            assertThat(prontas.await(10, TimeUnit.SECONDS)).isTrue();
            largada.countDown();
            return List.of(a.get(25, TimeUnit.SECONDS), b.get(25, TimeUnit.SECONDS));
        } finally { largada.countDown(); executor.shutdownNow(); }
    }

    private <T> T executar(AcessoRepresentante acesso, Supplier<T> operacao, CountDownLatch prontas, CountDownLatch largada) throws Exception {
        autenticar(acesso, "REPRESENTANTE");
        try {
            prontas.countDown();
            assertThat(largada.await(10, TimeUnit.SECONDS)).isTrue();
            return operacao.get();
        } finally { SecurityContextHolder.clearContext(); }
    }
}
