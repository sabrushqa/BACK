package com.example.demo.services;

import com.example.demo.entities.utilisateur;
import com.example.demo.repositories.UtilisateurRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class JwtService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtService.class);

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<JwtDecoder> keycloakJwtDecoderProvider;
    private final UtilisateurRepository utilisateurRepository;

    public JwtService(
        ObjectProvider<JwtDecoder> keycloakJwtDecoderProvider,
        UtilisateurRepository utilisateurRepository
    ) {
        this.clock = Clock.systemUTC();
        this.objectMapper = new ObjectMapper();
        this.keycloakJwtDecoderProvider = keycloakJwtDecoderProvider;
        this.utilisateurRepository = utilisateurRepository;
    }

    public OffsetDateTime extractExpiration(String token) {
        return OffsetDateTime.ofInstant(
            Instant.ofEpochSecond(readRequiredLongClaim(readClaims(token), "exp")),
            ZoneOffset.UTC
        );
    }

    public Long extractUserId(String token) {
        Map<String, Object> claims = readClaims(token);
        Long internalUserId = readOptionalLongClaim(claims, "uid");
        if (internalUserId != null) {
            return internalUserId;
        }
        if (isKeycloakClaims(claims)) {
            return resolveKeycloakUserId(claims)
                .orElseThrow(() -> keycloakUserNotFound(claims));
        }
        return null;
    }

    /**
     * Determine si un token doit etre rejete parce que la session a ete invalidee
     * (reset de mot de passe, desactivation, etc.) depuis son emission.
     *
     * Les tokens applicatifs portent un claim "ver" comparable directement au
     * compteur stocke en base. Les tokens Keycloak ne portent aucun claim
     * applicatif : on compare plutot leur date d'emission ("iat") a la date de
     * dernier changement du compteur cote base, ce qui reste correct puisqu'un
     * token deja emis avant l'invalidation aura toujours un "iat" anterieur.
     */
    public boolean isSessionInvalidated(String token, utilisateur utilisateur) {
        Map<String, Object> claims = readClaims(token);
        Long localVersion = readOptionalLongClaim(claims, "ver");
        if (localVersion != null) {
            return !java.util.Objects.equals(localVersion.intValue(), resolveTokenVersion(utilisateur));
        }

        java.time.LocalDateTime invalidatedAt = utilisateur.getTokenVersionUpdatedAt();
        if (invalidatedAt == null) {
            return false;
        }

        Long issuedAtEpochSeconds = readOptionalLongClaim(claims, "iat");
        if (issuedAtEpochSeconds == null) {
            return false;
        }

        return issuedAtEpochSeconds < invalidatedAt.toEpochSecond(ZoneOffset.UTC);
    }

    public boolean isTokenExpired(String token) {
        long expirationEpochSeconds = readRequiredLongClaim(readClaims(token), "exp");
        return expirationEpochSeconds <= clock.instant().getEpochSecond();
    }

    public Optional<String> extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null) {
            return Optional.empty();
        }

        String trimmedHeader = authorizationHeader.trim();
        if (!trimmedHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Optional.empty();
        }

        String token = trimmedHeader.substring(7).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private Map<String, Object> readClaims(String token) {
        return readKeycloakClaims(token).orElseThrow(this::invalidToken);
    }

    private Optional<Map<String, Object>> readKeycloakClaims(String token) {
        JwtDecoder keycloakJwtDecoder = keycloakJwtDecoderProvider.getIfAvailable();
        if (keycloakJwtDecoder == null) {
            return Optional.empty();
        }
        try {
            Jwt jwt = keycloakJwtDecoder.decode(token);
            return Optional.of(new LinkedHashMap<>(jwt.getClaims()));
        } catch (JwtException exception) {
            return Optional.empty();
        }
    }

    private Long readOptionalLongClaim(Map<String, Object> claims, String claimName) {
        Object value = claims.get(claimName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof Instant instant) {
            return instant.getEpochSecond();
        }
        if (value instanceof Date date) {
            return date.toInstant().getEpochSecond();
        }
        return null;
    }

    private long readRequiredLongClaim(Map<String, Object> claims, String claimName) {
        Long value = readOptionalLongClaim(claims, claimName);
        if (value == null) {
            throw invalidToken();
        }

        return value;
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token Keycloak invalide.");
    }

    private Optional<Long> resolveKeycloakUserId(Map<String, Object> claims) {
        return resolveKeycloakUser(claims).map(utilisateur::getId);
    }

    private Optional<utilisateur> resolveKeycloakUser(Map<String, Object> claims) {
        Optional<utilisateur> userBySubject = readOptionalStringClaim(claims, "sub")
            .flatMap(utilisateurRepository::findByKeycloakId);
        if (userBySubject.isPresent()) {
            userBySubject.ifPresent(this::activateAfterKeycloakLogin);
            return userBySubject;
        }

        Optional<utilisateur> userByEmail = keycloakLoginCandidates(claims)
            .stream()
            .map(utilisateurRepository::findByEmailIgnoreCase)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();

        readOptionalStringClaim(claims, "sub").ifPresent(keycloakId ->
            userByEmail
                .filter(user -> !StringUtils.hasText(user.getKeycloakId()))
                .ifPresent(user -> {
                    user.setKeycloakId(keycloakId);
                    utilisateurRepository.save(user);
                })
        );

        userByEmail.ifPresent(this::activateAfterKeycloakLogin);
        return userByEmail;
    }

    private Set<String> keycloakLoginCandidates(Map<String, Object> claims) {
        Set<String> candidates = new LinkedHashSet<>();
        readOptionalStringClaim(claims, "email").ifPresent(candidates::add);
        readOptionalStringClaim(claims, "preferred_username").ifPresent(candidates::add);
        readOptionalStringClaim(claims, "username").ifPresent(candidates::add);
        return candidates;
    }

    private boolean isKeycloakClaims(Map<String, Object> claims) {
        return readOptionalStringClaim(claims, "iss")
            .filter(issuer -> issuer.contains("/realms/"))
            .isPresent()
            && readOptionalStringClaim(claims, "sub").isPresent();
    }

    private ResponseStatusException keycloakUserNotFound(Map<String, Object> claims) {
        // Detail utile au diagnostic (scope email manquant, compte non synchronise, ...)
        // conserve uniquement cote serveur : ne jamais renvoyer au client si un email
        // existe ou non en base, ce qui permettrait l'enumeration de comptes.
        String candidates = String.join(", ", keycloakLoginCandidates(claims));
        String subject = readOptionalStringClaim(claims, "sub").orElse("-");
        String logMessage = candidates.isBlank()
            ? "Utilisateur Keycloak introuvable dans SQL. Le token ne contient pas email/preferred_username. Ajoutez le scope email dans le client Keycloak. sub=" + subject
            : "Utilisateur Keycloak introuvable dans SQL pour: " + candidates + ". sub=" + subject;
        LOGGER.warn(logMessage);
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification Keycloak invalide.");
    }

    private Optional<String> readOptionalStringClaim(Map<String, Object> claims, String claimName) {
        Object value = claims.get(claimName);
        if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
            return Optional.of(stringValue.trim());
        }
        return Optional.empty();
    }

    private void activateAfterKeycloakLogin(utilisateur utilisateur) {
        if (utilisateur.getDateDesactivation() != null || Boolean.TRUE.equals(utilisateur.getActive())) {
            return;
        }

        utilisateur.setActive(Boolean.TRUE);
        utilisateur.setDateActivation(LocalDate.now());
        utilisateur.setPasswordExpiresAt(null);
        utilisateurRepository.save(utilisateur);
    }

    private int resolveTokenVersion(utilisateur utilisateur) {
        return utilisateur.getTokenVersion() == null ? 0 : utilisateur.getTokenVersion();
    }
}
