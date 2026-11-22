package br.edu.alertas.moradores;

import br.edu.alertas.auditoria.AuditoriaService;
import br.edu.alertas.auditoria.UsuarioAtual;
import br.edu.alertas.residencias.Residencia;
import br.edu.alertas.residencias.ResidenciaRepository;
import jakarta.persistence.EntityNotFoundException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MoradorService {
    private final MoradorRepository repository;
    private final ResidenciaRepository residencias;
    private final UsuarioAtual usuarios;
    private final AuditoriaService auditoria;
    private final EnvioCredenciais envio;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    public MoradorService(MoradorRepository repository, ResidenciaRepository residencias, UsuarioAtual usuarios,
            AuditoriaService auditoria, EnvioCredenciais envio, PasswordEncoder encoder, Clock clock) {
        this.repository = repository; this.residencias = residencias;
        this.usuarios = usuarios; this.auditoria = auditoria;
        this.envio = envio; this.encoder = encoder; this.clock = clock;
    }

    @Transactional
    public Morador cadastrar(DadosMorador dados) {
        usuarios.exigirRepresentante();
        if (dados == null) { throw new IllegalArgumentException("Informe os dados do morador."); }
        Morador morador = new Morador(dados, residencia(dados.residenciaId()));
        exigirDisponibilidade(dados.cpf(), dados.celular(), null);
        repository.saveAndFlush(morador);
        auditoria.registrar("CADASTRAR_MORADOR #" + morador.getId());
        enviarSenhaTemporaria(morador);
        return morador;
    }

    // RF005 regra 6: reenvio manual somente após falha, pelo mesmo canal escolhido.
    @Transactional
    public Morador reenviarSenhaTemporaria(Long id) {
        usuarios.exigirRepresentante();
        Morador morador = bloqueado(id);
        if (!morador.isAtiva() || !morador.isCredenciaisPendentes()) {
            throw new IllegalStateException("O reenvio só é permitido para conta ativa cujo envio falhou.");
        }
        enviarSenhaTemporaria(morador);
        return morador;
    }

    // shortcut: envio dentro da transação; com provedor real, enviar após o commit para não avisar cadastro revertido.
    private void enviarSenhaTemporaria(Morador morador) {
        StringBuilder senha = new StringBuilder();
        for (int i = 0; i < 10; i++) { senha.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length()))); }
        morador.emitirSenhaTemporaria(encoder.encode(senha), clock.instant().plus(Duration.ofHours(24)));
        boolean aceito;
        try {
            aceito = envio.enviarSenhaTemporaria(morador.getCanalCredenciais(), morador.getCelular(), senha.toString());
        } catch (RuntimeException falha) {
            aceito = false; // a falha do provedor não pode desfazer o cadastro; a entidade reenvia manualmente.
        }
        morador.registrarEnvio(aceito);
        repository.flush();
        // shortcut: rótulo fixo "SIMULADO" enquanto só existe o simulador; trocar ao integrar provedor real.
        auditoria.registrar((aceito ? "ENVIO_SIMULADO_SENHA_TEMPORARIA_" : "FALHA_ENVIO_SIMULADO_SENHA_TEMPORARIA_")
                + morador.getCanalCredenciais() + " #" + morador.getId());
    }

    @Transactional
    public Morador alterar(Long id, String nomeCompleto, String cpf, String celular, Long residenciaId) {
        usuarios.exigirRepresentante();
        Morador morador = bloqueado(id);
        exigirDisponibilidade(cpf, celular, id);
        morador.alterar(nomeCompleto, cpf, celular, residencia(residenciaId));
        repository.flush();
        auditoria.registrar("ALTERAR_MORADOR #" + id);
        return morador;
    }

    @Transactional
    public Morador desativar(Long id) { return situacao(id, false); }

    @Transactional
    public Morador reativar(Long id) { return situacao(id, true); }

    private Morador situacao(Long id, boolean ativa) {
        usuarios.exigirRepresentante();
        Morador morador = bloqueado(id);
        if (morador.isAtiva() != ativa) {
            if (ativa) { morador.reativar(); } else { morador.desativar(); }
            auditoria.registrar((ativa ? "REATIVAR_MORADOR #" : "DESATIVAR_MORADOR #") + id);
        }
        return morador;
    }

    public Morador buscar(Long id) {
        usuarios.exigirRepresentante();
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Morador não encontrado."));
    }

    public List<Morador> pesquisar(String termo) {
        usuarios.exigirRepresentante();
        Specification<Morador> filtro = (root, query, builder) -> {
            if (termo == null || termo.isBlank()) { return builder.conjunction(); }
            String literal = termo.toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                    .replace("%", "\\%").replace("_", "\\_");
            String trecho = "%" + literal + "%";
            return builder.or(builder.like(builder.lower(root.get("nomeCompleto")), trecho, '\\'),
                    builder.like(root.get("cpf"), trecho, '\\'), builder.like(root.get("celular"), trecho, '\\'));
        };
        return repository.findAll(filtro, Sort.by("id"));
    }

    private Morador bloqueado(Long id) {
        return repository.buscarComBloqueio(id).orElseThrow(() -> new EntityNotFoundException("Morador não encontrado."));
    }

    private Residencia residencia(Long id) {
        if (id == null) { throw new IllegalArgumentException("Selecione uma residência cadastrada."); }
        return residencias.findById(id).orElseThrow(() -> new IllegalArgumentException("A residência selecionada não está disponível."));
    }

    private void exigirDisponibilidade(String cpf, String celular, Long id) {
        boolean cpfOcupado = id == null ? repository.existsByCpf(cpf) : repository.existsByCpfAndIdNot(cpf, id);
        boolean celularOcupado = id == null ? repository.existsByCelular(celular) : repository.existsByCelularAndIdNot(celular, id);
        if (cpfOcupado) { throw new IllegalArgumentException("O CPF já pertence a outra conta."); }
        if (celularOcupado) { throw new IllegalArgumentException("O celular já pertence a outra conta."); }
    }
}
