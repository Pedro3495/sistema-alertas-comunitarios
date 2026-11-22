package br.edu.alertas.ocorrencias;

import br.edu.alertas.areas.AreaRisco;
import br.edu.alertas.moradores.Morador;
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
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text") private SituacaoOcorrencia situacao = SituacaoOcorrencia.EM_VERIFICACAO;
    @ManyToOne(optional = false) @JoinColumn(name = "area_id", nullable = false) private AreaRisco area;
    @ManyToOne @JoinColumn(name = "representante_id") private Representante representante;
    @ManyToOne @JoinColumn(name = "morador_id") private Morador morador;
    @OneToMany(mappedBy = "ocorrencia", cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    @OrderBy("ordem") private List<FotoOcorrencia> fotos = new ArrayList<>();

    protected Ocorrencia() { }

    // Exatamente um autor: representante ou morador (V13).
    Ocorrencia(DadosOcorrencia dados, AreaRisco area, Representante representante, Morador morador, Instant agora) {
        if (dados.envioId() == null) { throw new ErroOcorrencia("envioId", "Reabra o formulário para preparar o envio."); }
        validar(dados.tipo(), dados.especificacaoOutros(), dados.descricao(), dados.localizacao(), area);
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
        this.morador = morador;
        for (int i = 0; i < dados.fotos().size(); i++) { fotos.add(new FotoOcorrencia(this, i + 1, dados.fotos().get(i))); }
    }

    /** RF009 regras 2 e 4: Confirmada ou Não confirmada, com justificativa; revisão só entre as duas e antes do encerramento. */
    VerificacaoOcorrencia verificar(SituacaoOcorrencia decisao, String justificativa, Representante responsavel, Instant agora) {
        if (situacao == SituacaoOcorrencia.ENCERRADA) { throw new ErroOcorrencia("situacao", "Ocorrência encerrada fica disponível apenas para consulta."); }
        if (decisao != SituacaoOcorrencia.CONFIRMADA && decisao != SituacaoOcorrencia.NAO_CONFIRMADA) {
            throw new ErroOcorrencia("situacao", "Selecione Confirmada ou Não confirmada.");
        }
        if (decisao == situacao) { throw new ErroOcorrencia("situacao", "A ocorrência já está " + decisao.getRotulo() + "."); }
        exigir(justificativa, "justificativa", "Informe a justificativa da verificação.");
        situacao = decisao;
        return new VerificacaoOcorrencia(this, decisao, justificativa.strip(), responsavel, agora);
    }

    /**
     * RF011 regras 1, 5 e 6: só tipo, especificação de Outros, descrição, localização e área, com as validações do registro,
     * justificativa obrigatória e antes do encerramento. Fotos, autor e protocolo não mudam. Retorna o resumo por campo alterado.
     */
    AlteracaoOcorrencia alterar(TipoOcorrencia novoTipo, String novaEspecificacao, String novaDescricao, String novaLocalizacao,
            AreaRisco novaArea, String justificativa, Representante responsavel, Instant agora) {
        if (situacao == SituacaoOcorrencia.ENCERRADA) { throw new ErroOcorrencia("situacao", "Ocorrência encerrada fica disponível apenas para consulta."); }
        validar(novoTipo, novaEspecificacao, novaDescricao, novaLocalizacao, novaArea);
        exigir(justificativa, "justificativa", "Informe a justificativa da alteração.");
        String especificacao = novoTipo == TipoOcorrencia.OUTROS ? novaEspecificacao : null;
        List<String> mudancas = new ArrayList<>();
        mudar(mudancas, "Tipo", tipo.getRotulo(), novoTipo.getRotulo());
        mudar(mudancas, "Especificação de Outros", especificacaoOutros, especificacao);
        mudar(mudancas, "Descrição", descricao, novaDescricao);
        mudar(mudancas, "Localização", localizacao, novaLocalizacao);
        if (!area.getId().equals(novaArea.getId())) { mudar(mudancas, "Área de risco", area.getNome(), novaArea.getNome()); }
        if (mudancas.isEmpty()) { throw new ErroOcorrencia("alteracao", "Altere ao menos um dos campos permitidos."); }
        tipo = novoTipo; especificacaoOutros = especificacao; descricao = novaDescricao; localizacao = novaLocalizacao; area = novaArea;
        return new AlteracaoOcorrencia(this, justificativa.strip(), String.join(" ", mudancas), responsavel, agora);
    }

    /** RF012 regras 1 e 4 / alteração: qualquer representante, justificativa obrigatória, sem reabertura nem novo encerramento. */
    EncerramentoOcorrencia encerrar(String justificativa, Representante responsavel, Instant agora) {
        if (situacao == SituacaoOcorrencia.ENCERRADA) { throw new ErroOcorrencia("situacao", "Ocorrência encerrada fica disponível apenas para consulta."); }
        exigir(justificativa, "justificativa", "Informe a justificativa do encerramento.");
        situacao = SituacaoOcorrencia.ENCERRADA;
        return new EncerramentoOcorrencia(this, justificativa.strip(), responsavel, agora);
    }

    private static void mudar(List<String> mudancas, String campo, String anterior, String novo) {
        if (java.util.Objects.equals(anterior, novo)) { return; }
        mudancas.add(campo + ": de \"" + (anterior == null ? "" : anterior) + "\" para \"" + (novo == null ? "" : novo) + "\".");
    }

    private static void validar(TipoOcorrencia tipo, String especificacaoOutros, String descricao, String localizacao, AreaRisco area) {
        if (tipo == null) { throw new ErroOcorrencia("tipo", "Selecione o tipo de ocorrência."); }
        if (tipo == TipoOcorrencia.OUTROS) { exigir(especificacaoOutros, "especificacaoOutros", "Especifique o tipo Outros."); }
        exigir(descricao, "descricao", "Descreva o que ocorreu.");
        exigir(localizacao, "localizacao", "Informe o endereço ou ponto de referência do fato.");
        if (area == null) { throw new ErroOcorrencia("areaId", "Selecione uma área de risco cadastrada."); }
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
    public String getSituacao() { return situacao.getRotulo(); }
    public SituacaoOcorrencia getSituacaoAtual() { return situacao; }
    AreaRisco getArea() { return area; }
    public Long getAreaId() { return area.getId(); }
    public String getAreaNome() { return area.getNome(); }
    public Long getRepresentanteId() { return representante == null ? null : representante.getId(); }
    public String getRepresentanteNome() { return representante == null ? null : representante.getNomeCompleto(); }
    public Long getMoradorId() { return morador == null ? null : morador.getId(); }
    // RF008 regra 4: só para a tela do representante. Representante não tem celular cadastrado (RF004).
    public String getAutorNome() { return morador != null ? morador.getNomeCompleto() : representante.getNomeCompleto(); }
    public String getAutorCelular() { return morador == null ? null : morador.getCelular(); }
    public List<FotoOcorrencia> getFotos() { return List.copyOf(fotos); }
}
