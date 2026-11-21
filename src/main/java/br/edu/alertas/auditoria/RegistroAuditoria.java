package br.edu.alertas.auditoria;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "registros_auditoria")
public class RegistroAuditoria {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, columnDefinition = "text") private String acao;
    @Column(name = "responsavel_tipo", nullable = false, columnDefinition = "text") private String responsavelTipo;
    @Column(name = "responsavel_id", nullable = false) private Long responsavelId;
    @Column(name = "ocorrido_em", nullable = false) private Instant ocorridoEm;
    protected RegistroAuditoria() { }
    RegistroAuditoria(String acao, String tipo, Long responsavelId, Instant ocorridoEm) {
        this.acao = acao;
        this.responsavelTipo = tipo;
        this.responsavelId = responsavelId;
        this.ocorridoEm = ocorridoEm;
    }
    public Long getId() { return id; }
    public String getAcao() { return acao; }
    public String getResponsavelTipo() { return responsavelTipo; }
    public Long getResponsavelId() { return responsavelId; }
    public Instant getOcorridoEm() { return ocorridoEm; }
}
