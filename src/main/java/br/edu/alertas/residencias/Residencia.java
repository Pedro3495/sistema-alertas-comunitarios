package br.edu.alertas.residencias;

import br.edu.alertas.areas.AreaRisco;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "residencias")
public class Residencia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "rua_logradouro", nullable = false, columnDefinition = "text")
    private String ruaLogradouro;
    @Column(nullable = false, columnDefinition = "text")
    private String numero;
    @Column(nullable = false, columnDefinition = "text")
    private String bairro;
    @Column(nullable = false, columnDefinition = "text")
    private String municipio;
    @Column(columnDefinition = "text")
    private String complemento;
    @Column(name = "ponto_referencia", columnDefinition = "text")
    private String pontoReferencia;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private AreaRisco area;

    protected Residencia() { }

    public Residencia(DadosResidencia dados, AreaRisco area) {
        alterar(dados, area);
    }

    public void alterar(DadosResidencia dados, AreaRisco area) {
        if (dados == null) { throw new IllegalArgumentException("Informe os dados da residência."); }
        exigir(dados.ruaLogradouro(), "Informe a rua ou logradouro.");
        exigir(dados.numero(), "Informe o número ou indique sem número.");
        exigir(dados.bairro(), "Informe o bairro.");
        exigir(dados.municipio(), "Informe o município.");
        if (area == null) { throw new IllegalArgumentException("Selecione uma área de risco cadastrada."); }
        ruaLogradouro = dados.ruaLogradouro();
        numero = dados.numero();
        bairro = dados.bairro();
        municipio = dados.municipio();
        complemento = dados.complemento();
        pontoReferencia = dados.pontoReferencia();
        this.area = area;
    }

    private static void exigir(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) { throw new IllegalArgumentException(mensagem); }
    }

    public Long getId() { return id; }
    public String getRuaLogradouro() { return ruaLogradouro; }
    public String getNumero() { return numero; }
    public String getBairro() { return bairro; }
    public String getMunicipio() { return municipio; }
    public String getComplemento() { return complemento; }
    public String getPontoReferencia() { return pontoReferencia; }
    public Long getAreaId() { return area.getId(); }
    public String getAreaNome() { return area.getNome(); }
}
