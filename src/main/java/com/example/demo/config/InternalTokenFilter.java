package com.example.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtre de sécurité pour les appels internes chatbot → backoffice.
 * Valide le header X-Internal-Token avant que la requête atteigne le contrôleur.
 * Couche défense-en-profondeur : le contrôleur valide aussi, mais ici on bloque
 * au niveau filtre, avant tout traitement Spring (binding, validation, JPA).
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    @Value("${app.chatbot.internal-token}")
    private String internalToken;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/chatbot/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest  request,
            HttpServletResponse response,
            FilterChain         chain
    ) throws ServletException, IOException {

        String token = request.getHeader("X-Internal-Token");

        if (token == null || token.isBlank() || !isTokenValid(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Token interne manquant ou invalide\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isTokenValid(String candidate) {
        return MessageDigest.isEqual(
            candidate.getBytes(StandardCharsets.UTF_8),
            internalToken.getBytes(StandardCharsets.UTF_8)
        );
    }
}
