package br.edu.alertas.auditoria;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class AuditoriaService {
    public record OpcaoResponsavel(String chave, String nome) { }
    @PersistenceContext private EntityManager em;
    private final UsuarioAtual usuarios;
    private final Clock clock;
    private final JdbcTemplate jdbc;
    public AuditoriaService(UsuarioAtual usuarios, Clock clock, JdbcTemplate jdbc) {
        this.usuarios = usuarios; this.clock = clock; this.jdbc = jdbc;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(String acao) {
        var ator = usuarios.exigirResponsavel();
        em.persist(new RegistroAuditoria(acao, ator.tipo(), ator.id(), clock.instant()));
    }
    /** RF007: a recuperação é anônima; o responsável é o morador localizado pelo telefone. Nunca recebe código ou senha. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarMorador(String acao, Long moradorId) {
        registrarConta(acao, "MORADOR", moradorId);
    }
    /** RF005/RF013 (decisão de Pedro, 22/11/2026): a primeira troca ocorre com sessão temporária, sem responsável em UsuarioAtual. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarConta(String acao, String tipo, Long id) {
        em.persist(new RegistroAuditoria(acao, tipo, id, clock.instant()));
    }
    public long contarMorador(String acao, Long moradorId, Instant depoisDe) {
        return em.createQuery("""
                SELECT COUNT(r) FROM RegistroAuditoria r WHERE r.acao = :acao AND r.responsavelTipo = 'MORADOR'
                AND r.responsavelId = :id AND r.ocorridoEm > :depoisDe""", Long.class)
                .setParameter("acao", acao).setParameter("id", moradorId).setParameter("depoisDe", depoisDe)
                .getSingleResult();
    }
    public List<RegistroAuditoria> pesquisar(String responsavel, String acao, Instant inicio, Instant fim) {
        usuarios.exigirConsultaAuditoria();
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw new IllegalArgumentException("O início deve ser anterior ou igual ao fim do período.");
        }
        var builder = em.getCriteriaBuilder();
        var query = builder.createQuery(RegistroAuditoria.class);
        var root = query.from(RegistroAuditoria.class);
        List<Predicate> filtros = new ArrayList<>();
        if (responsavel != null && !responsavel.isBlank()) {
            var opcao = responsaveis().stream().filter(o -> o.chave().equals(responsavel)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Selecione um responsável da lista."));
            String[] partes = opcao.chave().split(":");
            filtros.add(builder.equal(root.get("responsavelTipo"), partes[0]));
            filtros.add(builder.equal(root.get("responsavelId"), Long.valueOf(partes[1])));
        }
        if (acao != null && !acao.isBlank()) {
            String literal = acao.toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                    .replace("%", "\\%").replace("_", "\\_");
            filtros.add(builder.like(builder.lower(root.get("acao")), "%" + literal + "%", '\\'));
        }
        if (inicio != null) { filtros.add(builder.greaterThanOrEqualTo(root.get("ocorridoEm"), inicio)); }
        if (fim != null) { filtros.add(builder.lessThanOrEqualTo(root.get("ocorridoEm"), fim)); }
        query.where(filtros.toArray(Predicate[]::new));
        query.orderBy(builder.desc(root.get("ocorridoEm")), builder.desc(root.get("id")));
        return em.createQuery(query).getResultList();
    }
    public List<OpcaoResponsavel> responsaveis() {
        usuarios.exigirConsultaAuditoria();
        return jdbc.query("""
                SELECT 'ADMINISTRADOR:' || id AS chave, 'Administrador: ' || login AS nome FROM administradores
                UNION ALL
                SELECT 'REPRESENTANTE:' || id, 'Representante: ' || login FROM representantes
                UNION ALL
                SELECT 'MORADOR:' || id, 'Morador #' || id FROM moradores
                ORDER BY nome
                """, (rs, row) -> new OpcaoResponsavel(rs.getString("chave"), rs.getString("nome")));
    }
}
