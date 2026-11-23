package br.edu.alertas.moradores;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Simulação do provedor de SMS/WhatsApp: não faz chamada de rede, não grava nem registra a senha nem o código.
 * Decisão de Pedro (22/11/2026): nenhum provedor real nesta entrega. Não conta como envio real.
 * Com alertas.envio-simulado.exibir=true, a mensagem fica visível ao administrador na {@link CaixaSaidaSimulada}.
 */
@Component
class EnvioCredenciaisSimulado implements EnvioCredenciais {
    private final boolean simularFalha;
    private final CaixaSaidaSimulada caixa;

    EnvioCredenciaisSimulado(@Value("${alertas.envio-simulado.falhar:false}") boolean simularFalha,
            CaixaSaidaSimulada caixa) {
        this.simularFalha = simularFalha;
        this.caixa = caixa;
    }

    @Override
    public boolean enviarSenhaTemporaria(CanalCredenciais canal, String celular, String senhaTemporaria) {
        if (simularFalha) { return false; }
        caixa.registrar(canal, celular, "Sua senha temporária de acesso: " + senhaTemporaria);
        return true;
    }

    @Override
    public boolean enviarCodigoRecuperacao(CanalCredenciais canal, String celular, String codigo) {
        if (simularFalha) { return false; }
        caixa.registrar(canal, celular, "Seu código de recuperação de acesso: " + codigo);
        return true;
    }
}
