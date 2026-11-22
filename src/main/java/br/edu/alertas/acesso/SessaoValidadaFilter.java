package br.edu.alertas.acesso;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.Predicate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Revalida no banco, a cada requisição, a sessão do tipo de conta indicado; conta desativada ou senha redefinida encerra a sessão. */
public class SessaoValidadaFilter<T> extends OncePerRequestFilter {
    private final Class<T> tipo;
    private final Predicate<T> sessaoValida;
    private final String entrada;

    public SessaoValidadaFilter(Class<T> tipo, Predicate<T> sessaoValida, String entrada) {
        this.tipo = tipo;
        this.sessaoValida = sessaoValida;
        this.entrada = entrada;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && tipo.isInstance(authentication.getPrincipal())
                && !sessaoValida.test(tipo.cast(authentication.getPrincipal()))) {
            SecurityContextHolder.clearContext();
            var session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            response.sendRedirect(request.getContextPath() + entrada + "?expirado");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
