package br.edu.alertas.moradores;

/** Porta de envio da senha temporária pelo canal único de credenciais (RF005 regra 2). */
public interface EnvioCredenciais {
    /** Retorna true se a mensagem foi aceita; false informa falha e permite reenvio manual (RF005 regra 6). */
    boolean enviarSenhaTemporaria(CanalCredenciais canal, String celular, String senhaTemporaria);
}
