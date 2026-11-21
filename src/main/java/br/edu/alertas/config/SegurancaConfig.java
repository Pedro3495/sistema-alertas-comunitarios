package br.edu.alertas.config;

import br.edu.alertas.acesso.AcessoController;
import br.edu.alertas.acesso.RepresentanteAuthenticationProvider;
import br.edu.alertas.acesso.SessaoRepresentanteFilter;
import br.edu.alertas.representantes.AcessoRepresentante;
import br.edu.alertas.representantes.AutenticacaoRepresentanteService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
public class SegurancaConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            RepresentanteAuthenticationProvider provider, AutenticacaoRepresentanteService acessoService)
            throws Exception {
        return http.authenticationProvider(provider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/css/**", "/actuator/health", "/entrar").permitAll()
                        .requestMatchers("/trocar-senha").hasAuthority("TROCAR_SENHA")
                        .requestMatchers("/representante").hasAuthority("REPRESENTANTE")
                        .anyRequest().denyAll())
                .formLogin(form -> form.loginPage("/entrar").loginProcessingUrl("/entrar")
                        .usernameParameter("login").passwordParameter("senha")
                        // O filtro padrão remove espaços do username; o login cadastrado é literal.
                        .authenticationDetailsSource(request -> request.getParameter("login"))
                        .successHandler((request, response, authentication) -> {
                            request.getSession().removeAttribute(AcessoController.LOGIN_TENTADO);
                            var acesso = (AcessoRepresentante) authentication.getPrincipal();
                            response.sendRedirect(request.getContextPath()
                                    + (acesso.trocaSenhaObrigatoria() ? "/trocar-senha" : "/representante"));
                        })
                        .failureHandler((request, response, exception) -> {
                            request.getSession().setAttribute(AcessoController.LOGIN_TENTADO,
                                    request.getParameter("login"));
                            response.sendRedirect(request.getContextPath() + "/entrar?erro");
                        }))
                .logout(logout -> logout.logoutUrl("/sair").logoutSuccessUrl("/entrar?saiu"))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    String path = request.getServletPath();
                    if (path.equals("/representante") || path.equals("/trocar-senha")) {
                        response.sendRedirect(request.getContextPath() + "/entrar");
                    } else {
                        response.setStatus(403);
                    }
                }))
                .addFilterBefore(new SessaoRepresentanteFilter(acessoService), AuthorizationFilter.class)
                .build();
    }
}
