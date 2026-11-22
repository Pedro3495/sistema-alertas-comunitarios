package br.edu.alertas.moradores;

import br.edu.alertas.acesso.BloqueioLogin;
import br.edu.alertas.residencias.Residencia;
import jakarta.persistence.*;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    @Column(name = "troca_senha_obrigatoria", nullable = false)
    private boolean trocaSenhaObrigatoria = true;
    @Embedded
    private BloqueioLogin bloqueio = new BloqueioLogin();
    @Column(name = "versao_credencial", nullable = false)
    private long versaoCredencial;

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
        if (this.cpf != null && !this.cpf.equals(cpf)) { versaoCredencial++; } // sessão aberta com o CPF antigo deixa de valer
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
        trocaSenhaObrigatoria = true;
        versaoCredencial++;
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

    // Conta sem senha emitida (base anterior à V10) nunca autentica.
    boolean conferirSenha(String senha, PasswordEncoder encoder) { return senhaHash != null && encoder.matches(senha, senhaHash); }
    boolean loginBloqueado(Instant agora) { return bloqueio.bloqueado(agora); }
    void registrarFalhaLogin(Instant agora) { bloqueio.registrarFalha(agora); }
    void registrarLoginCorreto() { bloqueio.zerar(); }
    boolean temporariaValida(Instant agora) { return !trocaSenhaObrigatoria || agora.isBefore(senhaTemporariaExpiraEm); }
    AcessoMorador acesso() { return new AcessoMorador(id, versaoCredencial, trocaSenhaObrigatoria); }
    boolean validarSessao(AcessoMorador acesso, Instant agora) {
        return ativa && senhaHash != null && id.equals(acesso.id()) && versaoCredencial == acesso.versaoCredencial()
                && trocaSenhaObrigatoria == acesso.trocaSenhaObrigatoria() && temporariaValida(agora);
    }
    void definirSenhaDefinitiva(String hash) {
        senhaHash = hash;
        trocaSenhaObrigatoria = false;
        versaoCredencial++;
    }

    void desativar() { if (ativa) { versaoCredencial++; } ativa = false; }
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
    public boolean isTrocaSenhaObrigatoria() { return trocaSenhaObrigatoria; }
    public boolean isCredenciaisPendentes() { return credenciaisPendentes; }
    public Instant getSenhaTemporariaExpiraEm() { return senhaTemporariaExpiraEm; }
}
