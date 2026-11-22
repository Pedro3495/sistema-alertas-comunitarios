package br.edu.alertas.residencias;

import br.edu.alertas.areas.AreaRisco;
import br.edu.alertas.areas.AreaRiscoRepository;
import br.edu.alertas.auditoria.AuditoriaService;
import br.edu.alertas.auditoria.UsuarioAtual;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ResidenciaService {
    private final ResidenciaRepository repository;
    private final AreaRiscoRepository areas;
    private final UsuarioAtual usuarios;
    private final AuditoriaService auditoria;

    public ResidenciaService(ResidenciaRepository repository, AreaRiscoRepository areas,
            UsuarioAtual usuarios, AuditoriaService auditoria) {
        this.repository = repository;
        this.areas = areas;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    @Transactional
    public Residencia cadastrar(DadosResidencia dados) {
        usuarios.exigirRepresentante();
        Residencia residencia = repository.saveAndFlush(new Residencia(dados, area(dados)));
        auditoria.registrar("CADASTRAR_RESIDENCIA #" + residencia.getId());
        return residencia;
    }

    @Transactional
    public Residencia alterar(Long id, DadosResidencia dados) {
        usuarios.exigirRepresentante();
        Residencia residencia = bloqueada(id);
        residencia.alterar(dados, area(dados));
        repository.flush();
        auditoria.registrar("ALTERAR_RESIDENCIA #" + residencia.getId());
        return residencia;
    }

    public Residencia buscar(Long id) {
        usuarios.exigirRepresentante();
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Residência não encontrada."));
    }

    public List<Residencia> pesquisar(String rua, String numero, String bairro, String municipio, Long areaId) {
        usuarios.exigirRepresentante();
        Specification<Residencia> filtros = (root, query, builder) -> {
            List<Predicate> predicados = new ArrayList<>();
            String[] campos = { "ruaLogradouro", "numero", "bairro", "municipio" };
            String[] valores = { rua, numero, bairro, municipio };
            for (int i = 0; i < campos.length; i++) {
                String valor = valores[i];
                if (valor != null && !valor.isBlank()) {
                    String literal = valor.toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                            .replace("%", "\\%").replace("_", "\\_");
                    predicados.add(builder.like(builder.lower(root.get(campos[i])), "%" + literal + "%", '\\'));
                }
            }
            if (areaId != null) { predicados.add(builder.equal(root.get("area").get("id"), areaId)); }
            return builder.and(predicados.toArray(Predicate[]::new));
        };
        return repository.findAll(filtros, Sort.by("id"));
    }

    @Transactional
    public void excluir(Long id) {
        usuarios.exigirRepresentante();
        Residencia residencia = bloqueada(id);
        repository.delete(residencia);
        repository.flush();
        auditoria.registrar("EXCLUIR_RESIDENCIA #" + id);
    }

    public List<AreaRisco> areasDisponiveis() {
        usuarios.exigirRepresentante();
        return areas.findAll(Sort.by("id"));
    }

    private Residencia bloqueada(Long id) {
        return repository.buscarComBloqueio(id)
                .orElseThrow(() -> new EntityNotFoundException("Residência não encontrada."));
    }

    private AreaRisco area(DadosResidencia dados) {
        if (dados == null || dados.areaId() == null) {
            throw new IllegalArgumentException("Selecione uma área de risco cadastrada.");
        }
        return areas.findById(dados.areaId())
                .orElseThrow(() -> new IllegalArgumentException("A área selecionada não está disponível. Selecione outra área."));
    }
}
