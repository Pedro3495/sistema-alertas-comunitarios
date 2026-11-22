package br.edu.alertas.auditoria;

import br.edu.alertas.administracao.AcessoAdministrador;
import br.edu.alertas.administracao.AdministradorService;
import br.edu.alertas.moradores.AcessoMorador;
import br.edu.alertas.moradores.AutenticacaoMoradorService;
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
    private final AutenticacaoMoradorService moradores;
    public UsuarioAtual(AdministradorService administradores, AutenticacaoRepresentanteService representantes,
            AutenticacaoMoradorService moradores) {
        this.administradores = administradores;
        this.representantes = representantes;
        this.moradores = moradores;
    }
    /** Morador com senha definitiva e sessão válida; nunca habilita consulta de auditoria. */
    public AcessoMorador exigirMorador() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AcessoMorador acesso
                && !acesso.trocaSenhaObrigatoria() && moradores.validarSessao(acesso)
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("MORADOR"))) {
            return acesso;
        }
        throw negado();
    }
    /** Responsável por uma ação auditada (RF013 regra 1): administrador, representante ou morador. */
    public Responsavel exigirResponsavel() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AcessoMorador
                ? new Responsavel("MORADOR", exigirMorador().id()) : exigirConsultaAuditoria();
    }
    public Responsavel exigirRepresentante() {
        var usuario = exigirConsultaAuditoria();
        if (!usuario.tipo().equals("REPRESENTANTE")) { throw negado(); }
        return usuario;
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
