package br.edu.alertas.gestao;

import br.edu.alertas.administracao.AdministradorService;
import br.edu.alertas.auditoria.AuditoriaService;
import br.edu.alertas.representantes.RepresentanteService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.test.util.AopTestUtils;

@SpringBootTest
class GestaoRepresentantesServiceTests {
    @Autowired GestaoRepresentantesService gestao;
    @Autowired AdministradorService administradores;
    @Autowired RepresentanteService representantes;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean AuditoriaService auditoria;
    private Long representanteId;
    private String loginAdmin;
    @BeforeEach void preparar() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM administradores", Integer.class))
                .as("Não executar testes contra banco com administrador real").isZero();
        loginAdmin = "admin-fachada-" + UUID.randomUUID();
        administradores.criarPrimeiro(loginAdmin, "senha-ficticia-admin");
        var acesso = administradores.autenticar(loginAdmin, "senha-ficticia-admin").orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                acesso, null, List.of(new SimpleGrantedAuthority("ADMINISTRADOR"))));
        representanteId = representantes.cadastrar("Pessoa Original", "Entidade", "fachada-" + UUID.randomUUID(), "senha-ficticia-rep").getId();
    }
    @AfterEach void limpar() {
        SecurityContextHolder.clearContext();
        if (loginAdmin != null) {
            jdbc.update("DELETE FROM registros_auditoria WHERE responsavel_tipo = 'ADMINISTRADOR' AND responsavel_id IN (SELECT id FROM administradores WHERE login = ?)", loginAdmin);
            jdbc.update("DELETE FROM administradores WHERE id = 1 AND login = ?", loginAdmin);
        }
        if (representanteId != null) jdbc.update("DELETE FROM representantes WHERE id = ?", representanteId);
    }
    @Test void falhaDeAuditoriaReverteCorrecaoConfirmadaPeloDominio() {
        doThrow(new IllegalStateException("falha fictícia de persistência"))
                .when(AopTestUtils.<AuditoriaService>getUltimateTargetObject(auditoria)).registrar(anyString());
        assertThatThrownBy(() -> gestao.alterar(representanteId, "Nome Novo", "Outra", "login-novo-ficticio"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT nome_completo FROM representantes WHERE id = ?", String.class, representanteId))
                .isEqualTo("Pessoa Original");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registros_auditoria", Integer.class)).isZero();
    }
    @Test void fachadaNaoPermiteAtorForjadoSemPrincipalValido() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> gestao.desativar(representanteId)).isInstanceOf(AccessDeniedException.class);
        assertThat(jdbc.queryForObject("SELECT ativa FROM representantes WHERE id = ?", Boolean.class, representanteId)).isTrue();
    }
    @Test void segundoProvisionamentoNaoSobrescreveContaExistente() {
        assertThatThrownBy(() -> administradores.criarPrimeiro("outro", "senha-ficticia-nova"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(administradores.autenticar(loginAdmin, "senha-ficticia-admin")).isPresent();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM administradores", Integer.class)).isEqualTo(1);
    }
}
