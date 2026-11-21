package br.edu.alertas.acesso;

import br.edu.alertas.representantes.AutenticacaoRepresentanteService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class RepresentanteAuthenticationProvider implements AuthenticationProvider {
    private final AutenticacaoRepresentanteService acessoService;

    public RepresentanteAuthenticationProvider(AutenticacaoRepresentanteService acessoService) {
        this.acessoService = acessoService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String senha = authentication.getCredentials() instanceof String valor ? valor : null;
        String login = authentication.getDetails() instanceof String literal ? literal : authentication.getName();
        var acesso = acessoService.autenticar(login, senha)
                .orElseThrow(() -> new BadCredentialsException("Não foi possível entrar."));
        String permissao = acesso.trocaSenhaObrigatoria() ? "TROCAR_SENHA" : "REPRESENTANTE";
        return UsernamePasswordAuthenticationToken.authenticated(acesso, null,
                List.of(new SimpleGrantedAuthority(permissao)));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
