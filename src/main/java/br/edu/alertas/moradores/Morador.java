package br.edu.alertas.moradores;

import br.edu.alertas.residencias.Residencia;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "moradores")
public class Morador {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "nome_completo", nullable = false, columnDefinition = "text")
    private String nomeCompleto;
    @Column(nullable = false, columnDefinition = "text")
    private String cpf;
    @Column(nullable = false, columnDefinition = "text")
    private String celular;
    @ManyToOne(optional = false)
    @JoinColumn(name = "residencia_id", nullable = false)
    private Residencia residencia;
    @Enumerated(EnumType.STRING)
    @Column(name = "canal_credenciais", nullable = false, columnDefinition = "text")
    private CanalCredenciais canalCredenciais;
    @Enumerated(EnumType.STRING)
    @Column(name = "preferencia_alertas", nullable = false, columnDefinition = "text")
    private PreferenciaAlertas preferenciaAlertas;
    @Column(nullable = false)
    private boolean ativa = true;
    @Column(name = "senha_hash", columnDefinition = "text")
    private String senhaHash;
    @Column(name = "senha_temporaria_expira_em")
    private Instant senhaTemporariaExpiraEm;
    @Column(name = "credenciais_pendentes", nullable = false)
    private boolean credenciaisPendentes = true;

    protected Morador() { }

    Morador(DadosMorador dados, Residencia residencia) {
        if (dados == null || dados.canalCredenciais() == null || dados.preferenciaAlertas() == null) {
            throw new IllegalArgumentException("Escolha separadamente o canal de credenciais e a preferência de alertas.");
        }
        alterar(dados.nomeCompleto(), dados.cpf(), dados.celular(), residencia);
        canalCredenciais = dados.canalCredenciais();
        preferenciaAlertas = dados.preferenciaAlertas();
    }

    void alterar(String nome, String cpf, String celular, Residencia residencia) {
        if (nome == null || nome.isBlank()) { throw new IllegalArgumentException("Informe o nome completo."); }
        if (!cpfValido(cpf)) { throw new IllegalArgumentException("Informe um CPF de 11 dígitos com verificadores válidos."); }
        if (celular == null || !celular.matches("[0-9]{11}")) {
            throw new IllegalArgumentException("Informe o celular com DDD e 11 dígitos.");
        }
        if (residencia == null) { throw new IllegalArgumentException("Selecione uma residência cadastrada."); }
        nomeCompleto = nome;
        this.cpf = cpf;
        this.celular = celular;
        this.residencia = residencia;
    }

    // Cada emissão substitui a anterior; a original nunca fica legível (RS002).
    void emitirSenhaTemporaria(String hash, Instant expiraEm) {
        senhaHash = hash;
        senhaTemporariaExpiraEm = expiraEm;
        credenciaisPendentes = true;
    }

    void registrarEnvio(boolean aceito) { credenciaisPendentes = !aceito; }

    static boolean cpfValido(String cpf) {
        if (cpf == null || !cpf.matches("[0-9]{11}") || cpf.chars().distinct().count() == 1) { return false; }
        for (int tamanho = 9; tamanho <= 10; tamanho++) {
            int soma = 0;
            for (int i = 0; i < tamanho; i++) { soma += (cpf.charAt(i) - '0') * (tamanho + 1 - i); }
            int resto = soma % 11;
            int digito = resto < 2 ? 0 : 11 - resto;
            if (digito != cpf.charAt(tamanho) - '0') { return false; }
        }
        return true;
    }

    void desativar() { ativa = false; }
    void reativar() { ativa = true; }
    public Long getId() { return id; }
    public String getNomeCompleto() { return nomeCompleto; }
    public String getCpf() { return cpf; }
    public String getCelular() { return celular; }
    public Long getResidenciaId() { return residencia.getId(); }
    public Long getAreaId() { return residencia.getAreaId(); }
    public CanalCredenciais getCanalCredenciais() { return canalCredenciais; }
    public PreferenciaAlertas getPreferenciaAlertas() { return preferenciaAlertas; }
    public boolean isAtiva() { return ativa; }
    public boolean isCredenciaisPendentes() { return credenciaisPendentes; }
    public Instant getSenhaTemporariaExpiraEm() { return senhaTemporariaExpiraEm; }
}
