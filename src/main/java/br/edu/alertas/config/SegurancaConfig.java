package br.edu.alertas.config;

import br.edu.alertas.acesso.AcessoController;
import br.edu.alertas.acesso.RepresentanteAuthenticationProvider;
import br.edu.alertas.acesso.AcessoMoradorController;
import br.edu.alertas.acesso.MoradorAuthenticationProvider;
import br.edu.alertas.acesso.SessaoValidadaFilter;
import br.edu.alertas.moradores.AcessoMorador;
import br.edu.alertas.moradores.AutenticacaoMoradorService;
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


    // RF005 regra 1: o morador entra por CPF e senha em rotas próprias, separadas do login de representantes.
    @Bean
    @org.springframework.core.annotation.Order(2)
    SecurityFilterChain moradorSecurityFilterChain(HttpSecurity http, MoradorAuthenticationProvider provider,
            AutenticacaoMoradorService acessoService) throws Exception {
        return http.securityMatcher("/morador", "/morador/**")
                .authenticationManager(new org.springframework.security.authentication.ProviderManager(provider))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers("/morador/entrar", "/morador/recuperar", "/morador/recuperar/codigo").permitAll()
                        .requestMatchers("/morador/trocar-senha").hasAuthority("MORADOR_TROCAR_SENHA")
                        .requestMatchers("/morador", "/morador/ocorrencias", "/morador/ocorrencias/**", "/morador/alertas", "/morador/preferencias").hasAuthority("MORADOR")
                        .anyRequest().denyAll())
                .formLogin(form -> form.loginPage("/morador/entrar").loginProcessingUrl("/morador/entrar")
                        .usernameParameter("cpf").passwordParameter("senha")
                        .successHandler((request, response, authentication) -> {
                            request.getSession().removeAttribute(AcessoMoradorController.CPF_TENTADO);
                            var acesso = (AcessoMorador) authentication.getPrincipal();
                            response.sendRedirect(request.getContextPath()
                                    + (acesso.trocaSenhaObrigatoria() ? "/morador/trocar-senha" : "/morador"));
                        })
                        .failureHandler((request, response, exception) -> {
                            request.getSession().setAttribute(AcessoMoradorController.CPF_TENTADO,
                                    request.getParameter("cpf"));
                            response.sendRedirect(request.getContextPath() + "/morador/entrar?erro");
                        }))
                .logout(logout -> logout.logoutUrl("/morador/sair").logoutSuccessUrl("/morador/entrar?saiu"))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    String path = request.getServletPath();
                    if (path.equals("/morador") || path.equals("/morador/trocar-senha")) {
                        response.sendRedirect(request.getContextPath() + "/morador/entrar");
                    } else {
                        response.setStatus(403);
                    }
                }))
                .addFilterBefore(new SessaoValidadaFilter<>(AcessoMorador.class, acessoService::validarSessao,
                        "/morador/entrar"), AuthorizationFilter.class)
                .build();
    }

    @Bean
    @org.springframework.core.annotation.Order(3)
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            RepresentanteAuthenticationProvider provider, AutenticacaoRepresentanteService acessoService)
            throws Exception {
        return http.authenticationManager(new org.springframework.security.authentication.ProviderManager(provider))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers("/", "/css/**", "/js/ocorrencias.js", "/actuator/health", "/entrar").permitAll()
                        .requestMatchers("/trocar-senha").hasAuthority("TROCAR_SENHA")
                        .requestMatchers("/representante", "/auditoria", "/areas", "/areas/**", "/residencias", "/residencias/**", "/moradores", "/moradores/**", "/ocorrencias", "/ocorrencias/**").hasAuthority("REPRESENTANTE")
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
                .addFilterBefore(new SessaoValidadaFilter<>(AcessoRepresentante.class, acessoService::validarSessao,
                        "/entrar"), AuthorizationFilter.class)
                .build();
    }
}
