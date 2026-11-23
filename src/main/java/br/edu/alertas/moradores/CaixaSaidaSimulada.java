package br.edu.alertas.moradores;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Caixa de saída do envio simulado, só para demonstração local (alertas.envio-simulado.exibir=true; padrão false).
 * Mantém em memória as últimas mensagens para o administrador ver; nada vai para log, banco ou auditoria.
 */
@Component
public class CaixaSaidaSimulada {
    static final int LIMITE = 20;

    public record Mensagem(Instant quando, CanalCredenciais canal, String destino, String conteudo) { }

    private final boolean ativa;
    private final Deque<Mensagem> mensagens = new ArrayDeque<>();

    CaixaSaidaSimulada(@Value("${alertas.envio-simulado.exibir:false}") boolean ativa) {
        this.ativa = ativa;
    }

    public boolean ativa() { return ativa; }

    synchronized void registrar(CanalCredenciais canal, String celular, String conteudo) {
        if (!ativa) { return; }
        String destino = celular == null || celular.length() < 4 ? "***" : "***" + celular.substring(celular.length() - 4);
        mensagens.addFirst(new Mensagem(Instant.now(), canal, destino, conteudo));
        while (mensagens.size() > LIMITE) { mensagens.removeLast(); }
    }

    /** Mais recentes primeiro. */
    public synchronized List<Mensagem> mensagens() { return List.copyOf(mensagens); }
}
