package br.edu.alertas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SegurancaConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Somente a página inicial e o diagnóstico da infraestrutura estão prontos.
        // As permissões de negócio serão implementadas nos RF004/RF005.
        return http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/css/**", "/actuator/health").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
