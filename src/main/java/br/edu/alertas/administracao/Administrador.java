package br.edu.alertas.administracao;

import br.edu.alertas.acesso.BloqueioLogin;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    @Embedded
    private BloqueioLogin bloqueio = new BloqueioLogin();

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
        return bloqueio.bloqueado(agora);
    }

    void registrarFalhaLogin(Instant agora) {
        bloqueio.registrarFalha(agora);
    }

    void registrarLoginCorreto() {
        bloqueio.zerar();
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
