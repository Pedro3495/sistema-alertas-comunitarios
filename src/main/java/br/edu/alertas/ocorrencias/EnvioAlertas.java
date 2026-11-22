package br.edu.alertas.ocorrencias;

import br.edu.alertas.moradores.CanalCredenciais;

/** Porta de envio dos alertas e avisos (RF010 regra 3), separada das credenciais: lá não há "ambos" nem alternativa (regra 5). */
public interface EnvioAlertas {
    /** true se o canal aceitou a mensagem; false é falha e leva à tentativa pelo outro canal. */
    boolean enviar(CanalCredenciais canal, String celular, String texto);
}
