package br.edu.alertas.moradores;

/** Porta de envio pelo canal único de credenciais: senha temporária (RF005 regra 2) e código de recuperação (RF007 regra 1). */
public interface EnvioCredenciais {
    /** Retorna true se a mensagem foi aceita; false informa falha e permite reenvio manual (RF005 regra 6). */
    boolean enviarSenhaTemporaria(CanalCredenciais canal, String celular, String senhaTemporaria);

    /** Mesma semântica: false informa a falha e permite reenvio manual pelo mesmo canal (RF007 regra 6). */
    boolean enviarCodigoRecuperacao(CanalCredenciais canal, String celular, String codigo);
}
