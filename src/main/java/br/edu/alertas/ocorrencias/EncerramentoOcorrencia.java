package br.edu.alertas.ocorrencias;

import br.edu.alertas.representantes.Representante;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

// RF012: encerramento único e definitivo. A justificativa é interna aos representantes (RF008 regra 6, RF012 regra 4).
@Entity
@Immutable
@Table(name = "encerramentos_ocorrencia")
public class EncerramentoOcorrencia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ocorrencia_id", nullable = false) private Ocorrencia ocorrencia;
    @Column(nullable = false, columnDefinition = "text") private String justificativa;
    @ManyToOne(optional = false) @JoinColumn(name = "representante_id", nullable = false) private Representante representante;
    @Column(name = "encerrada_em", nullable = false) private Instant encerradaEm;

    protected EncerramentoOcorrencia() { }

    EncerramentoOcorrencia(Ocorrencia ocorrencia, String justificativa, Representante representante, Instant agora) {
        this.ocorrencia = ocorrencia; this.justificativa = justificativa; this.representante = representante; this.encerradaEm = agora;
    }

    public String getJustificativa() { return justificativa; }
    public String getRepresentanteNome() { return representante.getNomeCompleto(); }
    public Instant getEncerradaEm() { return encerradaEm; }
}
