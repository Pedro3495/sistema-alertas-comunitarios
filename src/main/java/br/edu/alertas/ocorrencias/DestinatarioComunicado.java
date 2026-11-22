package br.edu.alertas.ocorrencias;

import br.edu.alertas.moradores.Morador;
import jakarta.persistence.*;
import static br.edu.alertas.moradores.CanalCredenciais.SMS;
import static br.edu.alertas.moradores.CanalCredenciais.WHATSAPP;

@Entity
@Table(name = "destinatarios_comunicado")
public class DestinatarioComunicado {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "comunicado_id", nullable = false) private Comunicado comunicado;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "morador_id", nullable = false) private Morador morador;
    @Column(nullable = false) private boolean entregue;

    protected DestinatarioComunicado() { }

    DestinatarioComunicado(Comunicado comunicado, Morador morador) { this.comunicado = comunicado; this.morador = morador; }

    /** RF010 regra 3: canais da preferência; se o escolhido falhar, tenta o outro. "Ambos" tenta os dois (| não curto-circuita). */
    void tentar(EnvioAlertas envio, String texto) {
        String celular = morador.getCelular();
        entregue = switch (morador.getPreferenciaAlertas()) {
            case SMS -> envio.enviar(SMS, celular, texto) || envio.enviar(WHATSAPP, celular, texto);
            case WHATSAPP -> envio.enviar(WHATSAPP, celular, texto) || envio.enviar(SMS, celular, texto);
            case AMBOS -> envio.enviar(SMS, celular, texto) | envio.enviar(WHATSAPP, celular, texto);
        };
    }

    // RF001: conta desativada não recebe mais alertas nem avisos, inclusive em reenvio.
    boolean pendente() { return !entregue && morador.isAtiva(); }
    boolean isEntregue() { return entregue; }
}
