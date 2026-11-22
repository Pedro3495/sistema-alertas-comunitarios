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

@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
@Configuration
public class SegurancaConfig {
    @Bean
    @org.springframework.core.annotation.Order(1)
    SecurityFilterChain administradorSecurityFilterChain(HttpSecurity http,
            br.edu.alertas.gestao.AdministradorAuthenticationProvider provider,
            br.edu.alertas.administracao.AdministradorService administradores) throws Exception {
        return http.securityMatcher("/admin/**")
                .authenticationManager(new org.springframework.security.authentication.ProviderManager(provider))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers("/admin/entrar").permitAll()
                        .requestMatchers("/admin/representantes", "/admin/representantes/**", "/admin/auditoria").hasAuthority("ADMINISTRADOR")
                        .anyRequest().denyAll())
                .formLogin(form -> form.loginPage("/admin/entrar").loginProcessingUrl("/admin/entrar")
                        .usernameParameter("login").passwordParameter("senha")
                        .authenticationDetailsSource(request -> request.getParameter("login"))
                        .defaultSuccessUrl("/admin/representantes", true)
                        .failureHandler((request, response, exception) -> {
                            request.getSession().setAttribute("LOGIN_ADMIN_TENTADO", request.getParameter("login"));
                            response.sendRedirect(request.getContextPath() + "/admin/entrar?erro");
                        }))
                .logout(logout -> logout.logoutUrl("/admin/sair").logoutSuccessUrl("/admin/entrar?saiu"))
                .requestCache(cache -> cache.disable())
                .addFilterBefore(new br.edu.alertas.gestao.SessaoAdministradorFilter(administradores), AuthorizationFilter.class)
                .build();
    }


    @Bean
    @org.springframework.core.annotation.Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            RepresentanteAuthenticationProvider provider, AutenticacaoRepresentanteService acessoService)
            throws Exception {
        return http.authenticationManager(new org.springframework.security.authentication.ProviderManager(provider))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers("/", "/css/**", "/js/ocorrencias.js", "/actuator/health", "/entrar").permitAll()
                        .requestMatchers("/trocar-senha").hasAuthority("TROCAR_SENHA")
                        .requestMatchers("/representante", "/auditoria", "/areas", "/areas/**", "/residencias", "/residencias/**", "/ocorrencias", "/ocorrencias/**").hasAuthority("REPRESENTANTE")
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
