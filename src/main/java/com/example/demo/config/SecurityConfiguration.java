package com.example.demo.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Autowired
    private InternalTokenFilter internalTokenFilter;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            // API stateless authentifiee exclusivement par JWT (header Authorization) :
            // aucune session HTTP n'est creee ni lue pour l'authentification, donc aucun
            // cookie de session ne porte jamais de contexte de securite. Le CSRF (qui ne
            // protege que les mecanismes d'auth par cookie) est de fait hors-sujet ici.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .headers(h -> h
                .frameOptions(fo -> fo.sameOrigin())
                .contentTypeOptions(Customizer.withDefaults())
            )
            // InternalTokenFilter valide X-Internal-Token avant Spring Security
            // pour /api/chatbot/**, donc pas besoin de JWT Keycloak sur cette route
            .addFilterBefore(internalTokenFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/login/verify-otp",
                    "/api/auth/activate",
                    "/api/auth/password-reset/**",
                    "/api/auth/logout",
                    "/api/affiliations/**",
                    "/api/chatbot/**"
                ).permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
