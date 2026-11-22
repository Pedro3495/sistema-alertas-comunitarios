package br.edu.alertas.representantes;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RepresentanteService {
    private final RepresentanteRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public RepresentanteService(RepresentanteRepository repository,
            PasswordEncoder passwordEncoder, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public Representante cadastrar(String nomeCompleto, String entidadePublica,
            String login, String senhaTemporaria) {
        validarSenha(senhaTemporaria);
        exigirLoginDisponivel(login, null);
        return repository.save(new Representante(nomeCompleto, entidadePublica, login,
                passwordEncoder.encode(senhaTemporaria), clock.instant().plus(Duration.ofHours(24))));
    }

    @Transactional
    public Representante alterar(Long id, String nomeCompleto, String entidadePublica, String login) {
        Representante representante = buscar(id);
        exigirLoginDisponivel(login, id);
        representante.alterarCadastro(nomeCompleto, entidadePublica, login);
        return representante;
    }

    @Transactional
    public Representante desativar(Long id) {
        Representante representante = buscar(id);
        representante.desativar();
        return representante;
    }

    @Transactional
    public Representante reativar(Long id) {
        Representante representante = buscar(id);
        representante.reativar();
        return representante;
    }

    @Transactional
    public Representante redefinirSenhaTemporaria(Long id, String senha) {
        validarSenha(senha);
        Representante representante = buscar(id);
        representante.redefinirSenhaTemporaria(passwordEncoder.encode(senha),
                clock.instant().plus(Duration.ofHours(24)));
        return representante;
    }

    public List<Representante> pesquisar(String termoNomeOuLogin, String entidadePublica, Boolean ativa) {
        Specification<Representante> filtros = (root, query, builder) -> {
            List<Predicate> criterios = new ArrayList<>();
            if (termoNomeOuLogin != null && !termoNomeOuLogin.isBlank()) {
                String trecho = "%" + escaparLiteral(termoNomeOuLogin.toLowerCase(Locale.ROOT)) + "%";
                criterios.add(builder.or(
                        builder.like(builder.lower(root.get("nomeCompleto")), trecho, '\\'),
                        builder.like(builder.lower(root.get("login")), trecho, '\\')));
            }
            if (entidadePublica != null && !entidadePublica.isBlank()) {
                criterios.add(builder.equal(builder.lower(root.get("entidadePublica")),
                        entidadePublica.toLowerCase(Locale.ROOT)));
            }
            if (ativa != null) {
                criterios.add(builder.equal(root.get("ativa"), ativa));
            }
            return builder.and(criterios.toArray(Predicate[]::new));
        };
        return repository.findAll(filtros, Sort.by("id"));
    }

    private Representante buscar(Long id) {
        return repository.buscarPorIdComBloqueio(id)
                .orElseThrow(() -> new EntityNotFoundException("Representante não encontrado."));
    }

    private void exigirLoginDisponivel(String login, Long id) {
        boolean ocupado = id == null ? repository.existsByLogin(login)
                : repository.existsByLoginAndIdNot(login, id);
        if (ocupado) {
            throw new IllegalArgumentException("O login já pertence a outra conta.");
        }
    }

    public static void validarSenha(String senha) {
        if (senha == null || senha.codePointCount(0, senha.length()) < 8) {
            throw new IllegalArgumentException("A senha deve ter no mínimo 8 caracteres.");
        }
    }

    private static String escaparLiteral(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
