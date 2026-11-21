package br.edu.alertas.acesso;

import br.edu.alertas.representantes.AcessoRepresentante;
import br.edu.alertas.representantes.AutenticacaoRepresentanteService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class SessaoRepresentanteFilter extends OncePerRequestFilter {
    private final AutenticacaoRepresentanteService acessoService;

    public SessaoRepresentanteFilter(AutenticacaoRepresentanteService acessoService) {
        this.acessoService = acessoService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AcessoRepresentante acesso
                && !acessoService.validarSessao(acesso)) {
            SecurityContextHolder.clearContext();
            var session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            response.sendRedirect(request.getContextPath() + "/entrar?expirado");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
