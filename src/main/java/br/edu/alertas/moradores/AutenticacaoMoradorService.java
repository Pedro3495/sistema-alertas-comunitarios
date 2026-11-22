package br.edu.alertas.moradores;

import br.edu.alertas.auditoria.AuditoriaService;
import br.edu.alertas.representantes.RepresentanteService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** RF005 para o morador: CPF e senha, temporária de 24 h com troca obrigatória, bloqueio após 5 falhas. */
@Service
@Transactional(readOnly = true)
public class AutenticacaoMoradorService {
    @PersistenceContext private EntityManager em;
    private final MoradorRepository repository;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String hashContaInexistente;
    private final AuditoriaService auditoria;

    // @Lazy: AuditoriaService depende de UsuarioAtual, que depende deste serviço.
    public AutenticacaoMoradorService(MoradorRepository repository, PasswordEncoder encoder, Clock clock,
            @Lazy AuditoriaService auditoria) {
        this.repository = repository;
        this.encoder = encoder;
        this.clock = clock;
        this.auditoria = auditoria;
        hashContaInexistente = encoder.encode(UUID.randomUUID().toString());
    }

    // Mesma ordem do acesso de representantes; a falha confirma a própria transação antes da resposta.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AcessoMorador> autenticar(String cpf, String senha) {
        Morador morador = cpf == null ? null : repository.buscarPorCpfComBloqueio(cpf).orElse(null);
        String senhaRecebida = senha == null ? "" : senha;
        if (morador == null) {
            encoder.matches(senhaRecebida, hashContaInexistente); // custo igual ao de uma conta existente
            return Optional.empty();
        }
        boolean senhaCorreta = morador.conferirSenha(senhaRecebida, encoder);
        Instant agora = clock.instant();
        if (!morador.isAtiva() || morador.loginBloqueado(agora)) {
            return Optional.empty();
        }
        if (!senhaCorreta) {
            morador.registrarFalhaLogin(agora);
            return Optional.empty();
        }
        if (!morador.temporariaValida(agora)) {
            return Optional.empty();
        }
        morador.registrarLoginCorreto();
        return Optional.of(morador.acesso());
    }

    public boolean validarSessao(AcessoMorador acesso) {
        return acesso != null && acesso.id() != null && repository.findById(acesso.id())
                .map(morador -> morador.validarSessao(acesso, clock.instant()))
                .orElse(false);
    }

    /** Trava a conta do autor até o fim da transação do registro e revalida a sessão sobre o estado atual. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Morador bloquearAutor(AcessoMorador acesso) {
        Morador morador = acesso == null || acesso.id() == null ? null : repository.buscarComBloqueio(acesso.id()).orElse(null);
        if (morador != null) { em.refresh(morador); } // a cópia da sessão pode ser anterior a desativação ou troca de residência
        if (morador == null || acesso.trocaSenhaObrigatoria() || !morador.validarSessao(acesso, clock.instant())) {
            throw new AccessDeniedException("Entre novamente na sua conta.");
        }
        return morador;
    }

    @Transactional
    public void trocarSenha(AcessoMorador acesso, String novaSenha) {
        Morador morador = acesso == null || acesso.id() == null ? null
                : repository.buscarComBloqueio(acesso.id()).orElse(null);
        if (morador == null || !acesso.trocaSenhaObrigatoria() || !morador.validarSessao(acesso, clock.instant())) {
            throw new IllegalStateException("Entre novamente para definir sua senha.");
        }
        RepresentanteService.validarSenha(novaSenha);
        morador.definirSenhaDefinitiva(encoder.encode(novaSenha));
        auditoria.registrarMorador("DEFINIR_SENHA_DEFINITIVA_MORADOR #" + morador.getId(), morador.getId());
    }
}
