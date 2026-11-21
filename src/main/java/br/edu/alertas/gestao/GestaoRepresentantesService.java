package br.edu.alertas.gestao;

import br.edu.alertas.auditoria.AuditoriaService;
import br.edu.alertas.auditoria.UsuarioAtual;
import br.edu.alertas.representantes.Representante;
import br.edu.alertas.representantes.RepresentanteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class GestaoRepresentantesService {
    private final RepresentanteService representantes;
    private final br.edu.alertas.representantes.RepresentanteRepository repository;
    private final UsuarioAtual usuarios;
    private final AuditoriaService auditoria;
    public GestaoRepresentantesService(RepresentanteService representantes, UsuarioAtual usuarios, AuditoriaService auditoria,
            br.edu.alertas.representantes.RepresentanteRepository repository) {
        this.repository = repository;
        this.representantes = representantes; this.usuarios = usuarios; this.auditoria = auditoria;
    }
    public List<Representante> pesquisar(String termo, String entidade, Boolean ativa) {
        usuarios.exigirAdministrador();
        return representantes.pesquisar(termo, entidade, ativa);
    }
    public Representante buscar(Long id) {
        usuarios.exigirAdministrador();
        return repository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Representante não encontrado."));
    }
    @Transactional
    public Representante cadastrar(String nome, String entidade, String login, String senha) {
        usuarios.exigirAdministrador();
        var resultado = representantes.cadastrar(nome, entidade, login, senha);
        auditoria.registrar("CADASTRAR_REPRESENTANTE #" + resultado.getId());
        return resultado;
    }
    @Transactional
    public void alterar(Long id, String nome, String entidade, String login) {
        usuarios.exigirAdministrador();
        representantes.alterar(id, nome, entidade, login);
        auditoria.registrar("ALTERAR_REPRESENTANTE #" + id);
    }
    @Transactional
    public void desativar(Long id) {
        usuarios.exigirAdministrador(); representantes.desativar(id);
        auditoria.registrar("DESATIVAR_REPRESENTANTE #" + id);
    }
    @Transactional
    public void reativar(Long id) {
        usuarios.exigirAdministrador(); representantes.reativar(id);
        auditoria.registrar("REATIVAR_REPRESENTANTE #" + id);
    }
    @Transactional
    public void redefinirSenhaTemporaria(Long id, String senha) {
        usuarios.exigirAdministrador(); representantes.redefinirSenhaTemporaria(id, senha);
        auditoria.registrar("REDEFINIR_SENHA_REPRESENTANTE #" + id);
    }
}
