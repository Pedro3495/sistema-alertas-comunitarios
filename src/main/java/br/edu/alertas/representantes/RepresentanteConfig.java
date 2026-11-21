package br.edu.alertas.representantes;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class RepresentanteConfig {
    @Bean
    PasswordEncoder passwordEncoderRepresentantes() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    Clock relogioRepresentantes() {
        return Clock.systemUTC();
    }
}
