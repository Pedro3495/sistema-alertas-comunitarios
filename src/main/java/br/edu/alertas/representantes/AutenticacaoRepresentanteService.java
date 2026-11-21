package br.edu.alertas.representantes;

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
public class AutenticacaoRepresentanteService {
    private final RepresentanteRepository repository;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String hashContaInexistente;

    public AutenticacaoRepresentanteService(RepresentanteRepository repository,
            PasswordEncoder encoder, Clock clock) {
        this.repository = repository;
        this.encoder = encoder;
        this.clock = clock;
        hashContaInexistente = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AcessoRepresentante> autenticar(String login, String senha) {
        Representante representante = repository.buscarPorLoginComBloqueio(login).orElse(null);
        String senhaRecebida = senha == null ? "" : senha;
        if (representante == null) {
            // Mantém o custo de conferir uma senha mesmo quando o login não existe.
            encoder.matches(senhaRecebida, hashContaInexistente);
            return Optional.empty();
        }
        boolean senhaCorreta = representante.conferirSenha(senhaRecebida, encoder);
        Instant agora = clock.instant();
        if (!representante.isAtiva() || representante.loginBloqueado(agora)) {
            return Optional.empty();
        }
        if (!senhaCorreta) {
            representante.registrarFalhaLogin(agora);
            return Optional.empty();
        }
        if (!representante.temporariaValida(agora)) {
            return Optional.empty();
        }
        representante.registrarLoginCorreto();
        return Optional.of(representante.acesso());
    }

    public boolean validarSessao(AcessoRepresentante acesso) {
        return acesso != null && acesso.id() != null && repository.findById(acesso.id())
                .map(representante -> representante.validarSessao(acesso, clock.instant()))
                .orElse(false);
    }

    @Transactional
    public void trocarSenha(AcessoRepresentante acesso, String novaSenha) {
        if (acesso == null || acesso.id() == null) {
            throw new IllegalStateException("Entre novamente para definir sua senha.");
        }
        Representante representante = repository.buscarPorIdComBloqueio(acesso.id())
                .orElseThrow(() -> new IllegalStateException("Entre novamente para definir sua senha."));
        if (!acesso.trocaSenhaObrigatoria() || !representante.validarSessao(acesso, clock.instant())) {
            throw new IllegalStateException("Entre novamente para definir sua senha.");
        }
        RepresentanteService.validarSenha(novaSenha);
        representante.definirSenhaDefinitiva(encoder.encode(novaSenha));
    }
}
