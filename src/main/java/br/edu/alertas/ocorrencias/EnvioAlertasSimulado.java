package br.edu.alertas.ocorrencias;

import br.edu.alertas.moradores.CanalCredenciais;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Envio simulado de alertas: sem rede, sem log de celular ou texto. Decisão de Pedro (22/11/2026): nenhum provedor real.
 * Falha simulável por canal com alertas.envio-simulado.canais-com-falha=SMS,WHATSAPP.
 */
@Component
class EnvioAlertasSimulado implements EnvioAlertas {
    private final Set<CanalCredenciais> canaisComFalha;

    EnvioAlertasSimulado(@Value("${alertas.envio-simulado.canais-com-falha:}") Set<CanalCredenciais> canaisComFalha) {
        this.canaisComFalha = Set.copyOf(canaisComFalha);
    }

    @Override
    public boolean enviar(CanalCredenciais canal, String celular, String texto) {
        return !canaisComFalha.contains(canal);
    }
}
