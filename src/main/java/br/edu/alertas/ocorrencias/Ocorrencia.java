package br.edu.alertas.ocorrencias;

import br.edu.alertas.areas.AreaRisco;
import br.edu.alertas.representantes.Representante;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ocorrencias")
public class Ocorrencia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private UUID protocolo;
    @Column(name = "envio_id", nullable = false) private UUID envioId;
    @Column(name = "registrada_em", nullable = false) private Instant registradaEm;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text") private TipoOcorrencia tipo;
    @Column(name = "especificacao_outros", columnDefinition = "text") private String especificacaoOutros;
    @Column(nullable = false, columnDefinition = "text") private String descricao;
    @Column(nullable = false, columnDefinition = "text") private String localizacao;
    @Column(nullable = false, columnDefinition = "text") private String situacao = "EM_VERIFICACAO";
    @ManyToOne(optional = false) @JoinColumn(name = "area_id", nullable = false) private AreaRisco area;
    @ManyToOne(optional = false) @JoinColumn(name = "representante_id", nullable = false) private Representante representante;
    @OneToMany(mappedBy = "ocorrencia", cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    @OrderBy("ordem") private List<FotoOcorrencia> fotos = new ArrayList<>();

    protected Ocorrencia() { }

    Ocorrencia(DadosOcorrencia dados, AreaRisco area, Representante representante, Instant agora) {
        if (dados.envioId() == null) { throw new ErroOcorrencia("envioId", "Reabra o formulário para preparar o envio."); }
        if (dados.tipo() == null) { throw new ErroOcorrencia("tipo", "Selecione o tipo de ocorrência."); }
        if (dados.tipo() == TipoOcorrencia.OUTROS) { exigir(dados.especificacaoOutros(), "especificacaoOutros", "Especifique o tipo Outros."); }
        exigir(dados.descricao(), "descricao", "Descreva o que ocorreu.");
        exigir(dados.localizacao(), "localizacao", "Informe o endereço ou ponto de referência do fato.");
        if (area == null) { throw new ErroOcorrencia("areaId", "Selecione uma área de risco cadastrada."); }
        if (dados.fotos() == null || dados.fotos().isEmpty() || dados.fotos().size() > 4) {
            throw new ErroOcorrencia("fotos", "Escolha de uma a quatro fotos como evidências.");
        }
        protocolo = UUID.randomUUID();
        envioId = dados.envioId();
        registradaEm = agora;
        tipo = dados.tipo();
        especificacaoOutros = tipo == TipoOcorrencia.OUTROS ? dados.especificacaoOutros() : null;
        descricao = dados.descricao();
        localizacao = dados.localizacao();
        this.area = area;
        this.representante = representante;
        for (int i = 0; i < dados.fotos().size(); i++) { fotos.add(new FotoOcorrencia(this, i + 1, dados.fotos().get(i))); }
    }

    private static void exigir(String valor, String campo, String mensagem) {
        if (valor == null || valor.isBlank()) { throw new ErroOcorrencia(campo, mensagem); }
    }
    public Long getId() { return id; }
    public UUID getProtocolo() { return protocolo; }
    public UUID getEnvioId() { return envioId; }
    public Instant getRegistradaEm() { return registradaEm; }
    public TipoOcorrencia getTipo() { return tipo; }
    public String getEspecificacaoOutros() { return especificacaoOutros; }
    public String getDescricao() { return descricao; }
    public String getLocalizacao() { return localizacao; }
    public String getSituacao() { return "Em verificação"; }
    public Long getAreaId() { return area.getId(); }
    public String getAreaNome() { return area.getNome(); }
    public Long getRepresentanteId() { return representante.getId(); }
    public String getRepresentanteNome() { return representante.getNomeCompleto(); }
    public List<FotoOcorrencia> getFotos() { return List.copyOf(fotos); }
}
