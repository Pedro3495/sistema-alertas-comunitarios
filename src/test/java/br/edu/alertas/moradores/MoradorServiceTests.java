package br.edu.alertas.moradores;

import br.edu.alertas.administracao.AdministradorService;
import br.edu.alertas.auditoria.AuditoriaService;
import br.edu.alertas.representantes.*;
import br.edu.alertas.residencias.*;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@SpringBootTest
class MoradorServiceTests {
    @Autowired MoradorService service;
    @Autowired ResidenciaService residencias;
    @Autowired RepresentanteService representantes;
    @Autowired AutenticacaoRepresentanteService acesso;
    @Autowired AdministradorService administradores;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean AuditoriaService auditoria;
    private AcessoRepresentante ator;
    private Long representanteId;
    private long area;
    private long residencia;

    @BeforeEach
    void preparar() {
        var representante = representantes.cadastrar("Representante fictício", "Entidade fictícia", UUID.randomUUID().toString(), "temporaria-ficticia");
        representanteId = representante.getId();
        var temporaria = acesso.autenticar(representante.getLogin(), "temporaria-ficticia").orElseThrow();
        acesso.trocarSenha(temporaria, "definitiva-ficticia");
        ator = acesso.autenticar(representante.getLogin(), "definitiva-ficticia").orElseThrow();
        autenticar(ator, "REPRESENTANTE");
        area = jdbc.queryForObject("INSERT INTO areas_risco(nome, descricao_localizacao_limites) VALUES ('Área fictícia', 'Limites') RETURNING id", Long.class);
        residencia = novaResidencia();
    }

    @AfterEach
    void limparSomenteFixtures() {
        reset(auditoria);
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM moradores WHERE residencia_id IN (SELECT id FROM residencias WHERE area_id = ?)", area);
        jdbc.update("DELETE FROM residencias WHERE area_id = ?", area);
        jdbc.update("DELETE FROM areas_risco WHERE id = ?", area);
        jdbc.update("DELETE FROM registros_auditoria WHERE responsavel_tipo = 'REPRESENTANTE' AND responsavel_id = ?", representanteId);
        jdbc.update("DELETE FROM representantes WHERE id = ?", representanteId);
        jdbc.update("DELETE FROM administradores WHERE login = 'admin-ficticio-rf001'");
    }

    @Test
    void persisteCorrigeTransferePesquisaEDesativaSemExcluir() {
        var primeiro = cadastrar("Pessoa %_\\ fictícia", "52998224725", "81900000001");
        var segundo = cadastrar("Segunda fictícia", "11144477735", "81900000002");
        assertThat(primeiro.getResidenciaId()).isEqualTo(segundo.getResidenciaId());
        assertThat(service.pesquisar("FICTÍCIA")).hasSize(2);
        assertThat(service.pesquisar("%_\\")).extracting(Morador::getId).containsExactly(primeiro.getId());
        assertThat(service.pesquisar("529982")).extracting(Morador::getId).containsExactly(primeiro.getId());
        assertThat(service.pesquisar("81900000002")).extracting(Morador::getId).containsExactly(segundo.getId());
        assertThat(service.pesquisar("inexistente")).isEmpty();
        assertThat(service.pesquisar("")).hasSize(2);
        long destino = novaResidencia();
        service.alterar(primeiro.getId(), "Nome corrigido", "01234567890", "81900000003", destino);
        service.desativar(primeiro.getId());
        var salvo = service.buscar(primeiro.getId());
        assertThat(salvo.isAtiva()).isFalse();
        assertThat(salvo.getNomeCompleto()).isEqualTo("Nome corrigido");
        assertThat(salvo.getCpf()).isEqualTo("01234567890");
        assertThat(salvo.getCelular()).isEqualTo("81900000003");
        assertThat(salvo.getResidenciaId()).isEqualTo(destino);
        assertThat(salvo.getAreaId()).isEqualTo(area);
        assertThat(salvo.getCanalCredenciais()).isEqualTo(CanalCredenciais.SMS);
        assertThat(salvo.getPreferenciaAlertas()).isEqualTo(PreferenciaAlertas.AMBOS);
        assertThat(service.reativar(primeiro.getId()).getId()).isEqualTo(primeiro.getId());
        assertThat(service.buscar(primeiro.getId()).isAtiva()).isTrue();
        assertThat(acoes()).contains("CADASTRAR_MORADOR #" + primeiro.getId(), "ALTERAR_MORADOR #" + primeiro.getId(),
                "DESATIVAR_MORADOR #" + primeiro.getId(), "REATIVAR_MORADOR #" + primeiro.getId());
        assertThat(acoes()).allSatisfy(acao -> assertThat(acao).doesNotContain("52998224725", "81900000001"));
        assertThat(java.util.Arrays.stream(MoradorService.class.getMethods()).map(m -> m.getName()))
                .noneMatch(nome -> nome.startsWith("excluir") || nome.startsWith("remover") || nome.startsWith("delet"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void exclusividadePermaneceComContaDesativada(boolean ativa) {
        var primeiro = cadastrar("Primeiro", "52998224725", "81900000001");
        var segundo = cadastrar("Segundo", "11144477735", "81900000002");
        if (!ativa) { service.desativar(primeiro.getId()); }
        var antes = acoes();
        assertThatIllegalArgumentException().isThrownBy(() -> cadastrar("Duplicado", primeiro.getCpf(), "81900000003"));
        assertThatIllegalArgumentException().isThrownBy(() -> cadastrar("Duplicado", "01234567890", primeiro.getCelular()));
        assertThatIllegalArgumentException().isThrownBy(() -> service.alterar(segundo.getId(), "Outro nome", primeiro.getCpf(), "81900000003", residencia));
        assertThatIllegalArgumentException().isThrownBy(() -> service.alterar(segundo.getId(), "Outro nome", segundo.getCpf(), primeiro.getCelular(), residencia));
        assertThat(service.buscar(segundo.getId()).getNomeCompleto()).isEqualTo("Segundo");
        assertThat(acoes()).isEqualTo(antes);
    }

    @Test
    void recusaDadosInvalidosEResidenciaAusenteSemAuditar() {
        var morador = cadastrar("Original", "52998224725", "81900000001");
        var antes = acoes();
        assertThatIllegalArgumentException().isThrownBy(() -> service.cadastrarBase(null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.cadastrarBase(dados("Novo", "11144477735", "81900000002", null)));
        assertThatIllegalArgumentException().isThrownBy(() -> service.cadastrarBase(dados("Novo", "11144477735", "81900000002", Long.MAX_VALUE)));
        assertThatIllegalArgumentException().isThrownBy(() -> service.alterar(morador.getId(), "Novo", "11144477735", "x", residencia));
        assertThatIllegalArgumentException().isThrownBy(() -> service.alterar(morador.getId(), "Novo", "11144477735", "81900000002", Long.MAX_VALUE));
        assertThat(service.buscar(morador.getId()).getNomeCompleto()).isEqualTo("Original");
        assertThat(acoes()).isEqualTo(antes);
        assertThatThrownBy(() -> service.buscar(Long.MAX_VALUE)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.desativar(Long.MAX_VALUE)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void fkBloqueiaExclusaoMesmoComMoradorDesativadoSemAuditoriaFalsa() {
        var morador = cadastrar("Pessoa", "52998224725", "81900000001");
        service.desativar(morador.getId());
        var antes = acoes();
        assertThatThrownBy(() -> residencias.excluir(residencia)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(service.buscar(morador.getId()).getResidenciaId()).isEqualTo(residencia);
        assertThat(acoes()).isEqualTo(antes);
        long destino = novaResidencia();
        service.alterar(morador.getId(), morador.getNomeCompleto(), morador.getCpf(), morador.getCelular(), destino);
        residencias.excluir(residencia);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM residencias WHERE id = ?", Integer.class, residencia)).isZero();
    }

    @Test
    void bancoRecusaDuplicidadeDiretaDeCpfECelularEVinculoInexistente() {
        cadastrar("Pessoa", "52998224725", "81900000001");
        for (var par : List.of(List.of("52998224725", "81900000002"), List.of("11144477735", "81900000001"))) {
            assertThatThrownBy(() -> inserirDireto(par.get(0), par.get(1), residencia)).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> inserirDireto("11144477735", "81900000002", Long.MAX_VALUE)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void falhaNaAuditoriaReverteCadastroCorrecaoESituacao() {
        doThrow(new IllegalStateException("Falha de teste")).when(AopTestUtils.<AuditoriaService>getUltimateTargetObject(auditoria)).registrar(startsWith("CADASTRAR_MORADOR"));
        assertThatThrownBy(() -> cadastrar("Pessoa", "52998224725", "81900000001")).isInstanceOf(IllegalStateException.class);
        assertThat(service.pesquisar(null)).isEmpty();
        reset(auditoria);
        var morador = cadastrar("Original", "52998224725", "81900000001");
        var antes = acoes();
        doThrow(new IllegalStateException("Falha de teste")).when(AopTestUtils.<AuditoriaService>getUltimateTargetObject(auditoria)).registrar(startsWith("ALTERAR_MORADOR"));
        assertThatThrownBy(() -> service.alterar(morador.getId(), "Novo", "11144477735", "81900000002", residencia)).isInstanceOf(IllegalStateException.class);
        doThrow(new IllegalStateException("Falha de teste")).when(AopTestUtils.<AuditoriaService>getUltimateTargetObject(auditoria)).registrar(startsWith("DESATIVAR_MORADOR"));
        assertThatThrownBy(() -> service.desativar(morador.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(service.buscar(morador.getId()).getNomeCompleto()).isEqualTo("Original");
        assertThat(service.buscar(morador.getId()).isAtiva()).isTrue();
        assertThat(acoes()).isEqualTo(antes);
    }

    @Test
    void todasOperacoesNegamAnonimoAdministradorTemporariaESessaoRevogada() {
        var morador = cadastrar("Pessoa", "52998224725", "81900000001");
        SecurityContextHolder.clearContext();
        verificarNegacoes(morador.getId());
        administradores.criarPrimeiro("admin-ficticio-rf001", "senha-ficticia-admin");
        var admin = administradores.autenticar("admin-ficticio-rf001", "senha-ficticia-admin").orElseThrow();
        autenticar(admin, "ADMINISTRADOR");
        verificarNegacoes(morador.getId());
        autenticar(new AcessoRepresentante(ator.id(), ator.login(), ator.versaoCredencial(), true), "TROCAR_SENHA");
        verificarNegacoes(morador.getId());
        autenticar(ator, "REPRESENTANTE");
        representantes.desativar(representanteId);
        verificarNegacoes(morador.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"cpf", "celular"})
    void cadastroConcorrenteMantemIdentidadeUnica(String campo) throws Exception {
        var resultados = disputar(() -> cadastrar("Pessoa A", "52998224725", "81900000001"),
                () -> cadastrar("Pessoa B", campo.equals("cpf") ? "52998224725" : "11144477735",
                        campo.equals("celular") ? "81900000001" : "81900000002"));
        assertThat(resultados).containsExactlyInAnyOrder(true, false);
        assertThat(service.pesquisar(null)).hasSize(1);
        assertThat(acoes()).filteredOn(acao -> acao.startsWith("CADASTRAR_MORADOR")).hasSize(1);
    }

    @Test
    void cadastroConcorrenteComExclusaoNaoDeixaOrfao() throws Exception {
        var resultados = disputar(() -> cadastrar("Pessoa", "52998224725", "81900000001"),
                () -> { residencias.excluir(residencia); return null; });
        assertThat(resultados).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM moradores m LEFT JOIN residencias r ON r.id = m.residencia_id WHERE r.id IS NULL", Integer.class)).isZero();
    }

    @Test
    void transferenciaConcorrenteComExclusaoDoDestinoPreservaVinculoValido() throws Exception {
        var morador = cadastrar("Pessoa", "52998224725", "81900000001");
        long destino = novaResidencia();
        var resultados = disputar(() -> service.alterar(morador.getId(), morador.getNomeCompleto(),
                        morador.getCpf(), morador.getCelular(), destino),
                () -> { residencias.excluir(destino); return null; });
        assertThat(resultados).containsExactlyInAnyOrder(true, false);
        long vinculo = service.buscar(morador.getId()).getResidenciaId();
        assertThat(vinculo).isEqualTo(resultados.get(0) ? destino : residencia);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM residencias WHERE id = ?", Integer.class, vinculo)).isEqualTo(1);
    }

    private void verificarNegacoes(Long id) {
        for (Runnable operacao : List.<Runnable>of(() -> cadastrar("Novo", "11144477735", "81900000002"),
                () -> service.buscar(id), () -> service.pesquisar(null), () -> service.desativar(id),
                () -> service.reativar(id), () -> service.alterar(id, "Novo", "11144477735", "81900000002", residencia))) {
            assertThatThrownBy(operacao::run).isInstanceOf(AccessDeniedException.class);
        }
    }

    private List<Boolean> disputar(Supplier<?> primeira, Supplier<?> segunda) throws Exception {
        var largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tarefas = List.of(primeira, segunda).stream().map(operacao -> executor.submit(() -> {
                autenticar(ator, "REPRESENTANTE");
                try {
                    if (!largada.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Sem largada"); }
                    operacao.get();
                    return true;
                } catch (IllegalArgumentException | DataIntegrityViolationException esperado) {
                    return false;
                } finally { SecurityContextHolder.clearContext(); }
            })).toList();
            largada.countDown();
            return List.of(tarefas.get(0).get(20, TimeUnit.SECONDS), tarefas.get(1).get(20, TimeUnit.SECONDS));
        }
    }

    private void inserirDireto(String cpf, String celular, long residenciaId) {
        jdbc.update("INSERT INTO moradores(nome_completo, cpf, celular, residencia_id, canal_credenciais, preferencia_alertas, ativa) VALUES ('Fictício', ?, ?, ?, 'SMS', 'AMBOS', true)", cpf, celular, residenciaId);
    }

    private long novaResidencia() {
        return residencias.cadastrar(new DadosResidencia("Rua fictícia", "s/n", "Bairro", "Município", null, null, area)).getId();
    }

    private Morador cadastrar(String nome, String cpf, String celular) {
        return service.cadastrarBase(dados(nome, cpf, celular, residencia));
    }

    private DadosMorador dados(String nome, String cpf, String celular, Long residenciaId) {
        return new DadosMorador(nome, cpf, celular, residenciaId, CanalCredenciais.SMS, PreferenciaAlertas.AMBOS);
    }

    private List<String> acoes() {
        return jdbc.queryForList("SELECT acao FROM registros_auditoria WHERE responsavel_tipo = 'REPRESENTANTE' AND responsavel_id = ? ORDER BY id", String.class, representanteId);
    }

    private void autenticar(Object principal, String autoridade) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(autoridade))));
    }
}
