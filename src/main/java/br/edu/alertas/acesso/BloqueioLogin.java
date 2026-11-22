package br.edu.alertas.acesso;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Duration;
import java.time.Instant;

/** RF005 regra 5 / RS001: cinco falhas consecutivas por conta bloqueiam o login por 15 minutos. */
@Embeddable
public class BloqueioLogin {
    @Column(name = "falhas_login", nullable = false)
    private int falhasLogin;

    @Column(name = "bloqueado_ate")
    private Instant bloqueadoAte;

    /** No limite exato de 15 minutos a conta volta a aceitar tentativas, com contador zerado. */
    public boolean bloqueado(Instant agora) {
        if (bloqueadoAte != null && !agora.isBefore(bloqueadoAte)) {
            zerar();
        }
        return bloqueadoAte != null;
    }

    public void registrarFalha(Instant agora) {
        falhasLogin++;
        if (falhasLogin == 5) {
            bloqueadoAte = agora.plus(Duration.ofMinutes(15));
        }
    }

    public void zerar() {
        falhasLogin = 0;
        bloqueadoAte = null;
    }
}
