package br.edu.alertas.moradores;

import br.edu.alertas.auditoria.AuditoriaService;
import br.edu.alertas.representantes.RepresentanteService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF007 / RS003: recuperação do morador por código enviado ao telefone cadastrado, sem entrada direta. */
@Service
public class RecuperacaoAcessoService {
    static final String PEDIDO = "SOLICITAR_CODIGO_RECUPERACAO";
    static final String RECUSA_LIMITE = "RECUSAR_CODIGO_RECUPERACAO_LIMITE";
    static final String FALHA_ENVIO = "FALHA_ENVIO_CODIGO_RECUPERACAO";
    static final String REDEFINICAO = "REDEFINIR_SENHA_RECUPERACAO";
    private static final int PEDIDOS_POR_HORA = 3; // decisão de Pedro, 22/11/2026
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private final MoradorRepository repository;
    private final EnvioCredenciais envio;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final AuditoriaService auditoria;
    private final String hashInexistente;

    public RecuperacaoAcessoService(MoradorRepository repository, EnvioCredenciais envio, PasswordEncoder encoder, Clock clock,
            AuditoriaService auditoria) {
        this.repository = repository;
        this.envio = envio;
        this.encoder = encoder;
        this.clock = clock;
        this.auditoria = auditoria;
        hashInexistente = encoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Envia somente ao telefone já cadastrado, pelo canal de credenciais da pessoa (regra 1). Decisão de Pedro (23/11/2026),
     * RS003 sobre a regra 6: sem retorno ao chamador; conhecido, desconhecido, limite ou falha de envio têm a mesma resposta.
     * A falha fica só na auditoria e o reenvio manual é um novo pedido pelo mesmo canal.
     */
    @Transactional
    public void solicitarCodigo(String celular) {
        String codigo = "%06d".formatted(ALEATORIO.nextInt(1_000_000));
        String hash = encoder.encode(codigo); // custo igual com ou sem conta
        Morador morador = celular == null ? null
                : repository.buscarPorCelularComBloqueio(celular).filter(Morador::isAtiva).orElse(null);
        if (morador == null) { return; }
        // Janela móvel de 60 min; a recusa responde como sucesso para não revelar se o telefone tem conta.
        if (auditoria.contarMorador(PEDIDO, morador.getId(), clock.instant().minus(Duration.ofHours(1))) >= PEDIDOS_POR_HORA) {
            auditoria.registrarMorador(RECUSA_LIMITE, morador.getId());
            return;
        }
        auditoria.registrarMorador(PEDIDO, morador.getId());
        morador.emitirCodigoRecuperacao(hash, clock.instant());
        boolean aceito;
        try {
            aceito = envio.enviarCodigoRecuperacao(morador.getCanalCredenciais(), morador.getCelular(), codigo);
        } catch (RuntimeException falha) {
            aceito = false;
        }
        // shortcut: envio dentro da transação; com provedor real, enviar após o commit.
        if (!aceito) { auditoria.registrarMorador(FALHA_ENVIO, morador.getId()); }
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
        boolean redefinida = morador.redefinirSenhaComCodigo(recebido, novoHash, clock.instant(), encoder);
        if (redefinida) { auditoria.registrarMorador(REDEFINICAO, morador.getId()); }
        return redefinida;
    }
}
