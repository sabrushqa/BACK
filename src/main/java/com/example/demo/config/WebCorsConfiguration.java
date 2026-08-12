package com.example.demo.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebCorsConfiguration implements WebMvcConfigurer {

    private static final List<String> ALLOWED_ORIGINS = List.of(
        "http://localhost:*",
        "http://127.0.0.1:*",
        "https://localhost:*",
        "https://127.0.0.1:*"
    );

    private static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS");
    private static final List<String> ALLOWED_HEADERS = List.of("Authorization", "Content-Type", "Accept");

    /**
     * Bean lu directement par Spring Security (cors(Customizer.withDefaults())),
     * évite le cache-miss du HandlerMappingIntrospector.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(ALLOWED_ORIGINS);
        config.setAllowedMethods(ALLOWED_METHODS);
        config.setAllowedHeaders(ALLOWED_HEADERS);
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns(ALLOWED_ORIGINS.toArray(String[]::new))
            .allowedMethods(ALLOWED_METHODS.toArray(String[]::new))
            .allowedHeaders(ALLOWED_HEADERS.toArray(String[]::new))
            .allowCredentials(true)
            .maxAge(3600);
    }
}
