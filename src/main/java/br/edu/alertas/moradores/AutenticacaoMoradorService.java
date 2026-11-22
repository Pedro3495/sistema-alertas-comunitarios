package br.edu.alertas.moradores;

import br.edu.alertas.representantes.RepresentanteService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** RF005 para o morador: CPF e senha, temporária de 24 h com troca obrigatória, bloqueio após 5 falhas. */
@Service
@Transactional(readOnly = true)
public class AutenticacaoMoradorService {
    private final MoradorRepository repository;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String hashContaInexistente;

    public AutenticacaoMoradorService(MoradorRepository repository, PasswordEncoder encoder, Clock clock) {
        this.repository = repository;
        this.encoder = encoder;
        this.clock = clock;
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

    @Transactional
    public void trocarSenha(AcessoMorador acesso, String novaSenha) {
        Morador morador = acesso == null || acesso.id() == null ? null
                : repository.buscarComBloqueio(acesso.id()).orElse(null);
        if (morador == null || !acesso.trocaSenhaObrigatoria() || !morador.validarSessao(acesso, clock.instant())) {
            throw new IllegalStateException("Entre novamente para definir sua senha.");
        }
        RepresentanteService.validarSenha(novaSenha);
        morador.definirSenhaDefinitiva(encoder.encode(novaSenha));
    }
}
