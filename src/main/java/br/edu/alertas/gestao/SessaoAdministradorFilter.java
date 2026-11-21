package br.edu.alertas.gestao;

import br.edu.alertas.administracao.AcessoAdministrador;
import br.edu.alertas.administracao.AdministradorService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class SessaoAdministradorFilter extends OncePerRequestFilter {
    private final AdministradorService administradores;
    public SessaoAdministradorFilter(AdministradorService administradores) { this.administradores = administradores; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AcessoAdministrador acesso && !administradores.validarSessao(acesso)) {
            SecurityContextHolder.clearContext();
            var session = request.getSession(false); if (session != null) { session.invalidate(); }
            response.sendRedirect(request.getContextPath() + "/admin/entrar?expirado");
            return;
        }
        chain.doFilter(request, response);
    }
}
