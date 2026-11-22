package br.edu.alertas.moradores;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;

/** RF007 regras 2 e 5 / RS003: só o código mais recente vale, por 10 minutos, uso único, invalidado no 5º erro. */
@Embeddable
class CodigoRecuperacao {
    @Column(name = "codigo_recuperacao_hash", columnDefinition = "text")
    private String hash;
    @Column(name = "codigo_recuperacao_expira_em")
    private Instant expiraEm;
    @Column(name = "falhas_codigo_recuperacao", nullable = false)
    private int falhas;

    /** Nova emissão substitui a anterior (RF007 regras 5 e 6). */
    void emitir(String hash, Instant agora) {
        this.hash = hash;
        expiraEm = agora.plus(Duration.ofMinutes(10));
        falhas = 0;
    }

    /** No limite exato de 10 minutos o código já não vale; o erro conta e o quinto invalida. */
    boolean conferir(String codigo, Instant agora, PasswordEncoder encoder) {
        if (hash == null || !agora.isBefore(expiraEm)) {
            invalidar();
            return false;
        }
        if (encoder.matches(codigo, hash)) { return true; }
        if (++falhas == 5) { invalidar(); }
        return false;
    }

    void invalidar() {
        hash = null;
        expiraEm = null;
        falhas = 0;
    }
}
