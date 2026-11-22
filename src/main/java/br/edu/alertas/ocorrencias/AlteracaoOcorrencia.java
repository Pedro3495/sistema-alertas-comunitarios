package br.edu.alertas.ocorrencias;

import br.edu.alertas.representantes.Representante;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

// RF011: correção dos campos permitidos. A justificativa é interna aos representantes (RF008 regra 6); o resumo vai ao aviso.
@Entity
@Immutable
@Table(name = "alteracoes_ocorrencia")
public class AlteracaoOcorrencia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ocorrencia_id", nullable = false) private Ocorrencia ocorrencia;
    @Column(nullable = false, columnDefinition = "text") private String justificativa;
    @Column(nullable = false, columnDefinition = "text") private String resumo;
    @ManyToOne(optional = false) @JoinColumn(name = "representante_id", nullable = false) private Representante representante;
    @Column(name = "alterada_em", nullable = false) private Instant alteradaEm;

    protected AlteracaoOcorrencia() { }

    AlteracaoOcorrencia(Ocorrencia ocorrencia, String justificativa, String resumo, Representante representante, Instant agora) {
        this.ocorrencia = ocorrencia; this.justificativa = justificativa; this.resumo = resumo;
        this.representante = representante; this.alteradaEm = agora;
    }

    public String getJustificativa() { return justificativa; }
    public String getResumo() { return resumo; }
    public String getRepresentanteNome() { return representante.getNomeCompleto(); }
    public Instant getAlteradaEm() { return alteradaEm; }
}
