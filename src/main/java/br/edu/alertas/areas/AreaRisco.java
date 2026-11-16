package br.edu.alertas.areas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "areas_risco")
public class AreaRisco {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "text")
    private String nome;

    @Column(name = "descricao_localizacao_limites", nullable = false, columnDefinition = "text")
    private String descricaoLocalizacaoLimites;

    protected AreaRisco() {
        // Construtor exigido pelo JPA para reconstruir os registros do banco.
    }

    public AreaRisco(String nome, String descricaoLocalizacaoLimites) {
        alterarDefinicao(nome, descricaoLocalizacaoLimites);
    }

    public void alterarDefinicao(String nome, String descricaoLocalizacaoLimites) {
        validarPreenchimento(nome, "Informe o nome da área de risco.");
        validarPreenchimento(descricaoLocalizacaoLimites,
                "Informe a descrição da localização e dos limites da área de risco.");
        // Valida ambos antes de alterar: uma entrada inválida não modifica parte do objeto.
        this.nome = nome;
        this.descricaoLocalizacaoLimites = descricaoLocalizacaoLimites;
    }

    private static void validarPreenchimento(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricaoLocalizacaoLimites() {
        return descricaoLocalizacaoLimites;
    }
}
