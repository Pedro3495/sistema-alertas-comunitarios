package br.edu.alertas.moradores;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Simulação do provedor de SMS/WhatsApp: não faz chamada de rede, não grava nem registra a senha.
 * Decisão de Pedro (22/11/2026): nenhum provedor real nesta entrega. Não conta como envio real.
 */
@Component
class EnvioCredenciaisSimulado implements EnvioCredenciais {
    private final boolean simularFalha;

    EnvioCredenciaisSimulado(@Value("${alertas.envio-simulado.falhar:false}") boolean simularFalha) {
        this.simularFalha = simularFalha;
    }

    @Override
    public boolean enviarSenhaTemporaria(CanalCredenciais canal, String celular, String senhaTemporaria) {
        return !simularFalha;
    }
}
