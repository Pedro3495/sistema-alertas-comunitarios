package br.edu.alertas.ocorrencias;

import br.edu.alertas.representantes.Representante;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

// RF009: decisão ou revisão da verificação. A justificativa é interna aos representantes (RF008 regra 6).
@Entity
@Immutable
@Table(name = "verificacoes_ocorrencia")
public class VerificacaoOcorrencia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ocorrencia_id", nullable = false) private Ocorrencia ocorrencia;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text") private SituacaoOcorrencia situacao;
    @Column(nullable = false, columnDefinition = "text") private String justificativa;
    @ManyToOne(optional = false) @JoinColumn(name = "representante_id", nullable = false) private Representante representante;
    @Column(name = "verificada_em", nullable = false) private Instant verificadaEm;

    protected VerificacaoOcorrencia() { }

    VerificacaoOcorrencia(Ocorrencia ocorrencia, SituacaoOcorrencia situacao, String justificativa, Representante representante, Instant agora) {
        this.ocorrencia = ocorrencia; this.situacao = situacao; this.justificativa = justificativa;
        this.representante = representante; this.verificadaEm = agora;
    }

    public String getSituacao() { return situacao.getRotulo(); }
    public String getJustificativa() { return justificativa; }
    public String getRepresentanteNome() { return representante.getNomeCompleto(); }
    public Instant getVerificadaEm() { return verificadaEm; }
}
