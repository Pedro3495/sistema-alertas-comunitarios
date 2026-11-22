package br.edu.alertas.moradores;

import br.edu.alertas.representantes.RepresentanteService;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF007 / RS003: recuperação do morador por código enviado ao telefone cadastrado, sem entrada direta. */
@Service
public class RecuperacaoAcessoService {
    public enum ResultadoEnvio { SOLICITADO, FALHA_ENVIO }

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private final MoradorRepository repository;
    private final EnvioCredenciais envio;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String hashInexistente;

    public RecuperacaoAcessoService(MoradorRepository repository, EnvioCredenciais envio, PasswordEncoder encoder, Clock clock) {
        this.repository = repository;
        this.envio = envio;
        this.encoder = encoder;
        this.clock = clock;
        hashInexistente = encoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Envia somente ao telefone já cadastrado, pelo canal de credenciais da pessoa (regra 1). Número desconhecido
     * recebe a mesma resposta de sucesso: a recuperação não é pesquisa pública de pessoas (Pesquisa).
     */
    @Transactional
    public ResultadoEnvio solicitarCodigo(String celular) {
        String codigo = "%06d".formatted(ALEATORIO.nextInt(1_000_000));
        String hash = encoder.encode(codigo); // custo igual com ou sem conta
        Morador morador = celular == null ? null
                : repository.buscarPorCelularComBloqueio(celular).filter(Morador::isAtiva).orElse(null);
        if (morador == null) { return ResultadoEnvio.SOLICITADO; }
        morador.emitirCodigoRecuperacao(hash, clock.instant());
        boolean aceito;
        try {
            aceito = envio.enviarCodigoRecuperacao(morador.getCanalCredenciais(), morador.getCelular(), codigo);
        } catch (RuntimeException falha) {
            aceito = false;
        }
        // shortcut: envio dentro da transação; com provedor real, enviar após o commit.
        return aceito ? ResultadoEnvio.SOLICITADO : ResultadoEnvio.FALHA_ENVIO;
    }

    /** Valida o código e grava a nova senha; o erro de código é confirmado junto com a contagem (regras 2, 3 e 5). */
    @Transactional
    public boolean redefinirSenha(String celular, String codigo, String novaSenha) {
        RepresentanteService.validarSenha(novaSenha);
        String novoHash = encoder.encode(novaSenha); // antes da busca: custo igual com ou sem conta
        Morador morador = celular == null ? null : repository.buscarPorCelularComBloqueio(celular).orElse(null);
        String recebido = codigo == null ? "" : codigo.strip();
        if (morador == null) {
            encoder.matches(recebido, hashInexistente);
            return false;
        }
        return morador.redefinirSenhaComCodigo(recebido, novoHash, clock.instant(), encoder);
    }
}
