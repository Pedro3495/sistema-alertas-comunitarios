package br.edu.alertas.administracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdministradorTests {
    private static final Instant AGORA = Instant.parse("2026-11-21T12:00:00Z");

    @Test
    void loginLiteralEIdentidadeUnicaNaoExponemSenha() {
        Administrador administrador = new Administrador(" admin literal ", "hash-ficticio");
        assertThat(administrador.getId()).isEqualTo(1L);
        assertThat(administrador.getLogin()).isEqualTo(" admin literal ");
        assertThat(administrador.acesso().getName()).isEqualTo(" admin literal ");
        assertThat(administrador.toString()).doesNotContain("hash-ficticio");
        assertThat(Administrador.class.getMethods()).noneMatch(method ->
                method.getName().startsWith("get") && (method.getName().toLowerCase().contains("senha")
                        || method.getName().toLowerCase().contains("hash")));
    }

    @Test
    void quintaFalhaBloqueiaAteQuinzeMinutosEReiniciaNoLimiteExato() {
        Administrador administrador = new Administrador("admin", "hash");
        for (int falha = 0; falha < 4; falha++) {
            administrador.registrarFalhaLogin(AGORA);
            assertThat(administrador.loginBloqueado(AGORA)).isFalse();
        }
        administrador.registrarFalhaLogin(AGORA);
        assertThat(administrador.loginBloqueado(AGORA.plus(Duration.ofMinutes(15)).minusNanos(1))).isTrue();
        assertThat(administrador.loginBloqueado(AGORA.plus(Duration.ofMinutes(15)))).isFalse();
        administrador.registrarFalhaLogin(AGORA.plus(Duration.ofMinutes(15)));
        assertThat(administrador.loginBloqueado(AGORA.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void sucessoInterrompeSequenciaERecuperacaoRevogaSessaoSemLiberarBloqueio() {
        Administrador administrador = new Administrador("admin", "hash-antigo");
        for (int i = 0; i < 4; i++) administrador.registrarFalhaLogin(AGORA);
        administrador.registrarLoginCorreto();
        administrador.registrarFalhaLogin(AGORA);
        assertThat(administrador.loginBloqueado(AGORA)).isFalse();
        var acessoAntigo = administrador.acesso();
        for (int i = 0; i < 4; i++) administrador.registrarFalhaLogin(AGORA);
        administrador.redefinirSenha("hash-novo");
        assertThat(administrador.validarSessao(acessoAntigo)).isFalse();
        assertThat(administrador.validarSessao(administrador.acesso())).isTrue();
        assertThat(administrador.loginBloqueado(AGORA)).isTrue();
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches("nova-senha", "hash-novo")).thenReturn(true);
        assertThat(administrador.conferirSenha("nova-senha", encoder)).isTrue();
    }

    @Test
    void loginObrigatorioNaoCriaContaVazia() {
        assertThatThrownBy(() -> new Administrador("  ", "hash"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
