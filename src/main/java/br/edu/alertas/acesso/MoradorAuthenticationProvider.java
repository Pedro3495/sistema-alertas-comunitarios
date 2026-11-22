package br.edu.alertas.acesso;

import br.edu.alertas.moradores.AutenticacaoMoradorService;
import java.util.List;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class MoradorAuthenticationProvider implements AuthenticationProvider {
    private final AutenticacaoMoradorService acessoService;

    public MoradorAuthenticationProvider(AutenticacaoMoradorService acessoService) {
        this.acessoService = acessoService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String senha = authentication.getCredentials() instanceof String valor ? valor : null;
        // Aceita o CPF digitado com pontos e traço; o cadastro guarda só os 11 dígitos.
        String cpf = authentication.getName() == null ? null : authentication.getName().replaceAll("[.\\-\\s]", "");
        var acesso = acessoService.autenticar(cpf, senha)
                .orElseThrow(() -> new BadCredentialsException("Não foi possível entrar."));
        String permissao = acesso.trocaSenhaObrigatoria() ? "MORADOR_TROCAR_SENHA" : "MORADOR";
        return UsernamePasswordAuthenticationToken.authenticated(acesso, null,
                List.of(new SimpleGrantedAuthority(permissao)));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
