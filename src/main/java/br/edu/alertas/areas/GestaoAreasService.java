package br.edu.alertas.areas;

import br.edu.alertas.auditoria.AuditoriaService;
import br.edu.alertas.auditoria.UsuarioAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class GestaoAreasService {
    private final AreaRiscoService areas;
    private final UsuarioAtual usuarios;
    private final AuditoriaService auditoria;
    public GestaoAreasService(AreaRiscoService areas, UsuarioAtual usuarios, AuditoriaService auditoria) {
        this.areas = areas; this.usuarios = usuarios; this.auditoria = auditoria;
    }
    public List<AreaRisco> pesquisar(String nome) {
        usuarios.exigirRepresentante();
        return nome == null || nome.isBlank() ? areas.listar() : areas.pesquisarPorNome(nome);
    }
    public AreaRisco buscar(Long id) { usuarios.exigirRepresentante(); return areas.buscar(id); }
    @Transactional
    public void cadastrar(String nome, String descricao) {
        usuarios.exigirRepresentante(); var area = areas.cadastrar(nome, descricao);
        auditoria.registrar("CADASTRAR_AREA #" + area.getId());
    }
    @Transactional
    public void alterar(Long id, String nome, String descricao) {
        usuarios.exigirRepresentante(); areas.alterar(id, nome, descricao);
        auditoria.registrar("ALTERAR_AREA #" + id);
    }
    @Transactional
    public void excluir(Long id) {
        usuarios.exigirRepresentante(); areas.excluir(id);
        auditoria.registrar("EXCLUIR_AREA #" + id);
    }
}
