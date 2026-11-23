package br.edu.alertas.ocorrencias;

import br.edu.alertas.areas.AreaRisco;
import br.edu.alertas.moradores.Morador;
import br.edu.alertas.representantes.Representante;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** RF010: alerta emitido, aviso de alteração (RF011) ou de encerramento (RF012). Sem setters: o texto original é preservado (alteração e exclusão). */
@Entity
@Table(name = "comunicados_ocorrencia")
public class Comunicado {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "ocorrencia_id", nullable = false) private Ocorrencia ocorrencia;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text") private TipoComunicado tipo;
    @Column(nullable = false, columnDefinition = "text") private String titulo;
    @Column(nullable = false, columnDefinition = "text") private String mensagem;
    @Column(columnDefinition = "text") private String orientacoes;
    @ManyToOne(optional = false) @JoinColumn(name = "area_id", nullable = false) private AreaRisco area;
    @ManyToOne(optional = false) @JoinColumn(name = "representante_id", nullable = false) private Representante representante;
    @Column(name = "emitido_em", nullable = false) private Instant emitidoEm;
    @OneToMany(mappedBy = "comunicado", cascade = CascadeType.PERSIST)
    @OrderBy("id") private List<DestinatarioComunicado> destinatarios = new ArrayList<>();

    protected Comunicado() { }

    private Comunicado(TipoComunicado tipo, Ocorrencia ocorrencia, String titulo, String mensagem, String orientacoes,
            Collection<Morador> moradores, Representante representante, Instant agora) {
        this.tipo = tipo; this.ocorrencia = ocorrencia; this.titulo = titulo.strip(); this.mensagem = mensagem.strip();
        this.orientacoes = orientacoes == null || orientacoes.isBlank() ? null : orientacoes.strip();
        this.area = ocorrencia.getArea(); this.representante = representante; this.emitidoEm = agora;
        for (var morador : moradores) { destinatarios.add(new DestinatarioComunicado(this, morador)); }
    }

    /** RF010 regras 1 e 6 / RS008: só de ocorrência Confirmada, por ação explícita, com os três textos; usa a área atual. */
    static Comunicado alerta(Ocorrencia ocorrencia, String titulo, String mensagem, String orientacoes,
            Collection<Morador> moradoresDaArea, Representante representante, Instant agora) {
        if (ocorrencia.getSituacaoAtual() != SituacaoOcorrencia.CONFIRMADA) {
            throw new ErroOcorrencia("alerta", "Só é possível emitir alerta de ocorrência Confirmada.");
        }
        exigir(titulo, "titulo", "Informe o título do alerta.");
        exigir(mensagem, "mensagem", "Descreva a situação na mensagem do alerta.");
        exigir(orientacoes, "orientacoes", "Informe as orientações aos moradores.");
        return new Comunicado(TipoComunicado.ALERTA, ocorrencia, titulo, mensagem, orientacoes, moradoresDaArea, representante, agora);
    }

    /** RF011 regra 4 (usado pelo RF009 regra 4): protocolo, indicação de alteração, resumo e orientações quando houver. */
    static Comunicado alteracao(Ocorrencia ocorrencia, String resumo, String orientacoes,
            Collection<Morador> destinatariosAnteriores, Representante representante, Instant agora) {
        exigir(resumo, "resumo", "Informe o resumo da mudança para o aviso de alteração.");
        return new Comunicado(TipoComunicado.ALTERACAO, ocorrencia, "Alteração da ocorrência " + ocorrencia.getProtocolo(),
                resumo, orientacoes, destinatariosAnteriores, representante, agora);
    }

    /** RF012 regra 3: protocolo, indicação de encerramento, resumo do motivo e orientações quando necessárias. */
    static Comunicado encerramento(Ocorrencia ocorrencia, String resumo, String orientacoes,
            Collection<Morador> destinatariosAnteriores, Representante representante, Instant agora) {
        exigir(resumo, "resumo", "Informe o resumo do motivo para o aviso de encerramento.");
        return new Comunicado(TipoComunicado.ENCERRAMENTO, ocorrencia, "Encerramento da ocorrência " + ocorrencia.getProtocolo(),
                resumo, orientacoes, destinatariosAnteriores, representante, agora);
    }

    /** RF010 regras 3 e 4: envia a quem ainda não recebeu por nenhum canal; quem já recebeu não é repetido. */
    void distribuir(EnvioAlertas envio) {
        String texto = titulo + "\n" + mensagem + (orientacoes == null ? "" : "\nOrientações: " + orientacoes)
                + "\nProtocolo: " + ocorrencia.getProtocolo();
        destinatarios.stream().filter(DestinatarioComunicado::pendente).forEach(d -> d.tentar(envio, texto));
    }

    private static void exigir(String valor, String campo, String mensagem) {
        if (valor == null || valor.isBlank()) { throw new ErroOcorrencia(campo, mensagem); }
    }

    public Long getId() { return id; }
    public TipoComunicado getTipo() { return tipo; }
    public String getTitulo() { return titulo; }
    public String getMensagem() { return mensagem; }
    public String getOrientacoes() { return orientacoes; }
    public String getAreaNome() { return area.getNome(); }
    public String getRepresentanteNome() { return representante.getNomeCompleto(); }
    public Instant getEmitidoEm() { return emitidoEm; }
    public int getDestinatarios() { return destinatarios.size(); }
    public long getFalhas() { return destinatarios.stream().filter(d -> !d.isEntregue()).count(); }
    /** RF010 regra 4: reenvio manual só de alerta e só para quem falhou nos dois canais e continua ativo. */
    public boolean isReenviavel() { return tipo == TipoComunicado.ALERTA && destinatarios.stream().anyMatch(DestinatarioComunicado::pendente); }
}
