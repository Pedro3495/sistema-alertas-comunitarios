package br.edu.alertas.areas;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AreaRiscoService {
    private final AreaRiscoRepository repository;

    public AreaRiscoService(AreaRiscoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AreaRisco cadastrar(String nome, String descricaoLocalizacaoLimites) {
        return repository.save(new AreaRisco(nome, descricaoLocalizacaoLimites));
    }

    @Transactional
    public AreaRisco alterar(Long id, String nome, String descricaoLocalizacaoLimites) {
        AreaRisco area = repository.buscarComBloqueio(id)
                .orElseThrow(() -> new EntityNotFoundException("Área de risco não encontrada."));
        area.alterarDefinicao(nome, descricaoLocalizacaoLimites);
        return area;
    }

    public AreaRisco buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Área de risco não encontrada."));
    }

    public List<AreaRisco> listar() {
        return repository.findAll(org.springframework.data.domain.Sort.by("id"));
    }

    @Transactional
    public void excluir(Long id) {
        AreaRisco area = repository.buscarComBloqueio(id)
                .orElseThrow(() -> new EntityNotFoundException("Área de risco não encontrada."));
        repository.delete(area);
        repository.flush();
    }

    public List<AreaRisco> pesquisarPorNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Informe o nome da área de risco para pesquisar.");
        }
        return repository.findByNomeContainingIgnoreCase(nome);
    }
}
