package br.edu.alertas.auditoria;

import br.edu.alertas.administracao.AcessoAdministrador;
import br.edu.alertas.administracao.AdministradorService;
import br.edu.alertas.representantes.AcessoRepresentante;
import br.edu.alertas.representantes.AutenticacaoRepresentanteService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class UsuarioAtual {
    public record Responsavel(String tipo, Long id) { }
    private final AdministradorService administradores;
    private final AutenticacaoRepresentanteService representantes;
    public UsuarioAtual(AdministradorService administradores, AutenticacaoRepresentanteService representantes) {
        this.administradores = administradores;
        this.representantes = representantes;
    }
    public Responsavel exigirAdministrador() {
        var usuario = exigirConsultaAuditoria();
        if (!usuario.tipo().equals("ADMINISTRADOR")) { throw negado(); }
        return usuario;
    }
    public Responsavel exigirConsultaAuditoria() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            if (auth.getPrincipal() instanceof AcessoAdministrador acesso && administradores.validarSessao(acesso)
                    && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMINISTRADOR"))) {
                return new Responsavel("ADMINISTRADOR", acesso.id());
            }
            if (auth.getPrincipal() instanceof AcessoRepresentante acesso && !acesso.trocaSenhaObrigatoria()
                    && representantes.validarSessao(acesso)
                    && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("REPRESENTANTE"))) {
                return new Responsavel("REPRESENTANTE", acesso.id());
            }
        }
        throw negado();
    }
    private AccessDeniedException negado() { return new AccessDeniedException("Acesso não permitido."); }
}
