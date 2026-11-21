package br.edu.alertas.administracao;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdministradorService {
    private final AdministradorRepository repository;
    private final EntityManager entityManager;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String hashContaInexistente;

    public AdministradorService(AdministradorRepository repository, EntityManager entityManager,
            PasswordEncoder encoder, Clock clock) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.encoder = encoder;
        this.clock = clock;
        hashContaInexistente = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public Administrador criarPrimeiro(String login, String senha) {
        validarSenha(senha);
        if (repository.existsById(1L)) {
            throw new IllegalStateException("O administrador já foi configurado. Use a recuperação local se necessário.");
        }
        Administrador administrador = new Administrador(login, encoder.encode(senha));
        // Persist força INSERT: uma criação concorrente nunca sobrescreve o administrador existente.
        entityManager.persist(administrador);
        entityManager.flush();
        return administrador;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AcessoAdministrador> autenticar(String login, String senha) {
        Administrador administrador = repository.buscarComBloqueio().orElse(null);
        String recebida = senha == null ? "" : senha;
        if (administrador == null || !administrador.getLogin().equals(login)) {
            encoder.matches(recebida, hashContaInexistente);
            return Optional.empty();
        }
        boolean correta = administrador.conferirSenha(recebida, encoder);
        Instant agora = clock.instant();
        if (administrador.loginBloqueado(agora)) {
            return Optional.empty();
        }
        if (!correta) {
            administrador.registrarFalhaLogin(agora);
            return Optional.empty();
        }
        administrador.registrarLoginCorreto();
        return Optional.of(administrador.acesso());
    }

    public boolean validarSessao(AcessoAdministrador acesso) {
        return acesso != null && acesso.id() != null && repository.findById(acesso.id())
                .map(administrador -> administrador.validarSessao(acesso)).orElse(false);
    }

    @Transactional
    public void redefinirSenhaLocal(String login, String novaSenha) {
        validarSenha(novaSenha);
        Administrador administrador = repository.buscarComBloqueio()
                .filter(conta -> conta.getLogin().equals(login))
                .orElseThrow(() -> new IllegalStateException("Administrador não encontrado para recuperação local."));
        administrador.redefinirSenha(encoder.encode(novaSenha));
    }

    private static void validarSenha(String senha) {
        if (senha == null || senha.codePointCount(0, senha.length()) < 8) {
            throw new IllegalArgumentException("A senha deve ter no mínimo 8 caracteres.");
        }
    }
}
