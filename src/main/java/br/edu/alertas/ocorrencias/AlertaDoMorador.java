package br.edu.alertas.ocorrencias;

import java.time.Instant;
import java.util.UUID;

// Projeção da tela do morador: só o texto do comunicado, sem destinatários nem situação de entrega.
public record AlertaDoMorador(UUID protocolo, TipoComunicado tipo, String titulo, String mensagem, String orientacoes, Instant emitidoEm) { }
