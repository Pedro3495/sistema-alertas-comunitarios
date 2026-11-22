package br.edu.alertas.representantes;

import br.edu.alertas.acesso.BloqueioLogin;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;

@Entity
@Table(name = "representantes")
public class Representante {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_completo", nullable = false, columnDefinition = "text")
    private String nomeCompleto;

    @Column(name = "entidade_publica", nullable = false, columnDefinition = "text")
    private String entidadePublica;

    @Column(nullable = false, columnDefinition = "text")
    private String login;

    @Column(name = "senha_hash", nullable = false, columnDefinition = "text")
    private String senhaHash;

    @Column(nullable = false)
    private boolean ativa;

    @Column(name = "troca_senha_obrigatoria", nullable = false)
    private boolean trocaSenhaObrigatoria;

    @Column(name = "senha_temporaria_expira_em", nullable = false)
    private Instant senhaTemporariaExpiraEm;

    @Embedded
    private BloqueioLogin bloqueio = new BloqueioLogin();

    @Column(name = "versao_credencial", nullable = false)
    private long versaoCredencial;

    protected Representante() {
        // Construtor usado pelo JPA para reconstruir a conta persistida.
    }

    Representante(String nomeCompleto, String entidadePublica, String login,
            String senhaHash, Instant senhaTemporariaExpiraEm) {
        alterarCadastro(nomeCompleto, entidadePublica, login);
        redefinirSenhaTemporaria(senhaHash, senhaTemporariaExpiraEm);
        ativa = true;
    }

    public void alterarCadastro(String nomeCompleto, String entidadePublica, String login) {
        exigirPreenchimento(nomeCompleto, "Informe o nome completo do representante.");
        exigirPreenchimento(entidadePublica, "Informe a entidade pública do representante.");
        exigirPreenchimento(login, "Informe o login do representante.");
        if (this.login != null && !this.login.equals(login)) {
            versaoCredencial++;
        }
        this.nomeCompleto = nomeCompleto;
        this.entidadePublica = entidadePublica;
        this.login = login;
    }

    public void desativar() {
        if (ativa) {
            versaoCredencial++;
        }
        ativa = false;
    }

    public void reativar() {
        ativa = true;
    }

    void redefinirSenhaTemporaria(String senhaHash, Instant expiraEm) {
        exigirPreenchimento(senhaHash, "Informe o hash da senha temporária.");
        Objects.requireNonNull(expiraEm, "Informe a validade da senha temporária.");
        this.senhaHash = senhaHash;
        senhaTemporariaExpiraEm = expiraEm;
        trocaSenhaObrigatoria = true;
        versaoCredencial++;
    }

    boolean conferirSenha(String senha, PasswordEncoder encoder) {
        return encoder.matches(senha, senhaHash);
    }

    boolean loginBloqueado(Instant agora) {
        return bloqueio.bloqueado(agora);
    }

    boolean temporariaValida(Instant agora) {
        return !trocaSenhaObrigatoria || agora.isBefore(senhaTemporariaExpiraEm);
    }

    void registrarFalhaLogin(Instant agora) {
        bloqueio.registrarFalha(agora);
    }

    void registrarLoginCorreto() {
        bloqueio.zerar();
    }

    AcessoRepresentante acesso() {
        return new AcessoRepresentante(id, login, versaoCredencial, trocaSenhaObrigatoria);
    }

    boolean validarSessao(AcessoRepresentante acesso, Instant agora) {
        return ativa && id.equals(acesso.id()) && versaoCredencial == acesso.versaoCredencial()
                && trocaSenhaObrigatoria == acesso.trocaSenhaObrigatoria() && temporariaValida(agora);
    }

    void definirSenhaDefinitiva(String senhaHash) {
        exigirPreenchimento(senhaHash, "Informe o hash da nova senha.");
        this.senhaHash = senhaHash;
        trocaSenhaObrigatoria = false;
        versaoCredencial++;
    }

    private static void exigirPreenchimento(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
    }

    public Long getId() {
        return id;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    public String getEntidadePublica() {
        return entidadePublica;
    }

    public String getLogin() {
        return login;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public boolean isTrocaSenhaObrigatoria() {
        return trocaSenhaObrigatoria;
    }

    public Instant getSenhaTemporariaExpiraEm() {
        return senhaTemporariaExpiraEm;
    }
}
