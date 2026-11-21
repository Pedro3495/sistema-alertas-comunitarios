package br.edu.alertas.administracao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;

@Entity
@Table(name = "administradores")
public class Administrador {
    @Id
    private Long id;

    @Column(nullable = false, columnDefinition = "text")
    private String login;

    @Column(name = "senha_hash", nullable = false, columnDefinition = "text")
    private String senhaHash;

    @Column(name = "falhas_login", nullable = false)
    private int falhasLogin;

    @Column(name = "bloqueado_ate")
    private Instant bloqueadoAte;

    @Column(name = "versao_credencial", nullable = false)
    private long versaoCredencial;

    protected Administrador() {
        // Reconstrução pelo JPA.
    }

    Administrador(String login, String senhaHash) {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("Informe o login do administrador.");
        }
        this.id = 1L;
        this.login = login;
        this.senhaHash = Objects.requireNonNull(senhaHash);
    }

    boolean conferirSenha(String senha, PasswordEncoder encoder) {
        return encoder.matches(senha, senhaHash);
    }

    boolean loginBloqueado(Instant agora) {
        if (bloqueadoAte != null && !agora.isBefore(bloqueadoAte)) {
            registrarLoginCorreto();
        }
        return bloqueadoAte != null;
    }

    void registrarFalhaLogin(Instant agora) {
        falhasLogin++;
        if (falhasLogin == 5) {
            bloqueadoAte = agora.plus(Duration.ofMinutes(15));
        }
    }

    void registrarLoginCorreto() {
        falhasLogin = 0;
        bloqueadoAte = null;
    }

    void redefinirSenha(String senhaHash) {
        this.senhaHash = Objects.requireNonNull(senhaHash);
        versaoCredencial++;
    }

    AcessoAdministrador acesso() {
        return new AcessoAdministrador(id, login, versaoCredencial);
    }

    boolean validarSessao(AcessoAdministrador acesso) {
        return id.equals(acesso.id()) && login.equals(acesso.login())
                && versaoCredencial == acesso.versaoCredencial();
    }

    public Long getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }
}
