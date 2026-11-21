package br.edu.alertas.gestao;

import br.edu.alertas.administracao.AdministradorService;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class AdministradorAuthenticationProvider implements AuthenticationProvider {
    private final AdministradorService administradores;
    public AdministradorAuthenticationProvider(AdministradorService administradores) { this.administradores = administradores; }
    @Override
    public Authentication authenticate(Authentication authentication) {
        String login = authentication.getDetails() instanceof String literal ? literal : authentication.getName();
        String senha = authentication.getCredentials() instanceof String valor ? valor : null;
        var acesso = administradores.autenticar(login, senha)
                .orElseThrow(() -> new BadCredentialsException("Não foi possível entrar."));
        return UsernamePasswordAuthenticationToken.authenticated(acesso, null,
                List.of(new SimpleGrantedAuthority("ADMINISTRADOR")));
    }
    @Override public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
