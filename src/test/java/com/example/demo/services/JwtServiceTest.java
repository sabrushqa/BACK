package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.entities.utilisateur;
import com.example.demo.repositories.UtilisateurRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tests unitaires purs (sans contexte Spring, sans DB) de la logique de
 * JwtService: extraction du bearer token et invalidation de session.
 */
class JwtServiceTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<JwtDecoder> decoderProvider = mock(ObjectProvider.class);
    private final UtilisateurRepository utilisateurRepository = mock(UtilisateurRepository.class);
    private final JwtService jwtService = new JwtService(decoderProvider, utilisateurRepository);

    @Test
    void extractBearerTokenReturnsEmptyWhenHeaderIsNull() {
        assertThat(jwtService.extractBearerToken(null)).isEmpty();
    }

    @Test
    void extractBearerTokenReturnsEmptyWhenHeaderHasNoBearerPrefix() {
        assertThat(jwtService.extractBearerToken("Basic dXNlcjpwYXNz")).isEmpty();
    }

    @Test
    void extractBearerTokenReturnsEmptyWhenTokenPartIsBlank() {
        assertThat(jwtService.extractBearerToken("Bearer    ")).isEmpty();
    }

    @Test
    void extractBearerTokenIsCaseInsensitiveOnPrefix() {
        assertThat(jwtService.extractBearerToken("bEaReR abc123")).contains("abc123");
    }

    @Test
    void extractBearerTokenTrimsSurroundingWhitespace() {
        assertThat(jwtService.extractBearerToken("  Bearer   abc123  ")).contains("abc123");
    }

    @Test
    void isSessionInvalidatedReturnsFalseWhenNoInvalidationRecorded() {
        stubDecoderClaims(Map.of(
            "iss", "http://localhost:8088/realms/PFE26",
            "sub", "abc",
            "exp", Instant.now().plusSeconds(300)
        ));

        utilisateur user = new utilisateur();

        assertThat(jwtService.isSessionInvalidated("some-token", user)).isFalse();
    }

    @Test
    void isSessionInvalidatedReturnsTrueWhenTokenIssuedBeforeInvalidation() {
        Instant now = Instant.now();
        stubDecoderClaims(Map.of(
            "iss", "http://localhost:8088/realms/PFE26",
            "sub", "abc",
            "iat", now.minusSeconds(3600),
            "exp", now.plusSeconds(300)
        ));

        utilisateur user = new utilisateur();
        user.setTokenVersion(1);

        assertThat(jwtService.isSessionInvalidated("some-token", user)).isTrue();
    }

    @Test
    void isTokenExpiredReturnsTrueForPastExpiration() {
        Instant pastInstant = Instant.now().minusSeconds(3600);
        stubDecoderClaims(Map.of(
            "iss", "http://localhost:8088/realms/PFE26",
            "sub", "abc",
            "exp", pastInstant
        ));

        assertThat(jwtService.isTokenExpired("some-token")).isTrue();
    }

    @Test
    void isTokenExpiredReturnsFalseForFutureExpiration() {
        Instant futureInstant = Instant.now().plusSeconds(3600);
        stubDecoderClaims(Map.of(
            "iss", "http://localhost:8088/realms/PFE26",
            "sub", "abc",
            "exp", futureInstant
        ));

        assertThat(jwtService.isTokenExpired("some-token")).isFalse();
    }

    @Test
    void isSessionInvalidatedReturnsFalseWhenLocalVersionMatchesStoredVersion() {
        stubDecoderClaims(Map.of(
            "iss", "http://localhost:8088/realms/PFE26",
            "sub", "abc",
            "ver", 3,
            "exp", Instant.now().plusSeconds(300)
        ));

        utilisateur user = new utilisateur();
        user.setTokenVersion(3);

        assertThat(jwtService.isSessionInvalidated("some-token", user)).isFalse();
    }

    @Test
    void isSessionInvalidatedReturnsTrueWhenLocalVersionDiffersFromStoredVersion() {
        stubDecoderClaims(Map.of(
            "iss", "http://localhost:8088/realms/PFE26",
            "sub", "abc",
            "ver", 1,
            "exp", Instant.now().plusSeconds(300)
        ));

        utilisateur user = new utilisateur();
        user.setTokenVersion(2);

        assertThat(jwtService.isSessionInvalidated("some-token", user)).isTrue();
    }

    @Test
    void isSessionInvalidatedTreatsNullTokenVersionAsZero() {
        stubDecoderClaims(Map.of(
            "iss", "http://localhost:8088/realms/PFE26",
            "sub", "abc",
            "ver", 0,
            "exp", Instant.now().plusSeconds(300)
        ));

        utilisateur user = new utilisateur();
        user.setTokenVersion(null);

        assertThat(jwtService.isSessionInvalidated("some-token", user)).isFalse();
    }

    @Test
    void isSessionInvalidatedReturnsFalseWhenInvalidationDateSetButIatClaimMissing() {
        stubDecoderClaims(Map.of(
            "iss", "http://localhost:8088/realms/PFE26",
            "sub", "abc",
            "exp", Instant.now().plusSeconds(300)
        ));

        utilisateur user = new utilisateur();
        user.setTokenVersion(5);
        user.setTokenVersion(null);

        assertThat(jwtService.isSessionInvalidated("some-token", user)).isFalse();
    }

    @Test
    void isSessionInvalidatedReturnsFalseWhenTokenIssuedAfterInvalidation() {
        utilisateur user = new utilisateur();
        user.setTokenVersion(5);
        user.setTokenVersion(null);

        Instant issuedAt = Instant.now().plusSeconds(60);
        stubDecoderClaims(Map.of(
            "iss", "http://localhost:8088/realms/PFE26",
            "sub", "abc",
            "iat", issuedAt,
            "exp", issuedAt.plusSeconds(300)
        ));

        assertThat(jwtService.isSessionInvalidated("some-token", user)).isFalse();
    }

    @Test
    void extractExpirationReturnsExpectedOffsetDateTime() {
        Instant expiration = Instant.now().plusSeconds(600);
        stubDecoderClaims(Map.of(
            "iss", "http://localhost:8088/realms/PFE26",
            "sub", "abc",
            "exp", expiration
        ));

        OffsetDateTime result = jwtService.extractExpiration("some-token");

        assertThat(result).isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochSecond(expiration.getEpochSecond()), ZoneOffset.UTC));
    }

    @Test
    void extractExpirationThrowsUnauthorizedWhenExpClaimMissing() {
        stubDecoderClaims(Map.of(
            "iss", "http://localhost:8088/realms/PFE26",
            "sub", "abc"
        ));

        assertThatThrownBy(() -> jwtService.extractExpiration("some-token"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Token Keycloak invalide");
    }

    @Test
    void readClaimsThrowsUnauthorizedWhenNoDecoderAvailable() {
        when(decoderProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> jwtService.isTokenExpired("some-token"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Token Keycloak invalide");
    }

    @Test
    void readClaimsThrowsUnauthorizedWhenDecoderRejectsToken() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode(org.mockito.ArgumentMatchers.anyString())).thenThrow(new JwtException("bad token"));
        when(decoderProvider.getIfAvailable()).thenReturn(decoder);

        assertThatThrownBy(() -> jwtService.isTokenExpired("some-token"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Token Keycloak invalide");
    }

    @Test
    void extractUserIdReturnsInternalUserIdWhenUidClaimPresent() {
        stubDecoderClaims(Map.of(
            "iss", "http://localhost:8088/realms/PFE26",
            "sub", "abc",
            "uid", 42
        ));

        assertThat(jwtService.extractUserId("some-token")).isEqualTo(42L);
    }

    @Test
    void extractUserIdReturnsNullWhenClaimsAreNotKeycloakShaped() {
        stubDecoderClaims(Map.of("exp", Instant.now().plusSeconds(300)));

        assertThat(jwtService.extractUserId("some-token")).isNull();
    }

    @Test
    void extractUserIdResolvesViaKeycloakSubjectAndActivatesInactiveUser() {
        stubDecoderClaims(Map.of(
            "iss", "http://localhost:8088/realms/PFE26",
            "sub", "kc-sub-1"
        ));

        utilisateur user = new utilisateur();
        user.setId(7L);
        user.setActive(false);
        when(utilisateurRepository.findByKeycloakId("kc-sub-1")).thenReturn(Optional.of(user));

        assertThat(jwtService.extractUserId("some-token")).isEqualTo(7L);
        assertThat(user.getActive()).isTrue();
        verify(utilisateurRepository).save(user);
    }

    @Test
    void extractUserIdSkipsActivationWhenUserAlreadyActive() {
        stubDecoderClaims(Map.of(
            "iss", "http://localhost:8088/realms/PFE26",
            "sub", "kc-sub-2"
        ));

        utilisateur user = new utilisateur();
        user.setId(8L);
        user.setActive(true);
        when(utilisateurRepository.findByKeycloakId("kc-sub-2")).thenReturn(Optional.of(user));

        assertThat(jwtService.extractUserId("some-token")).isEqualTo(8L);
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void extractUserIdSkipsActivationWhenUserHasDeactivationDate() {
        stubDecoderClaims(Map.of(
            "iss", "http://localhost:8088/realms/PFE26",
            "sub", "kc-sub-3"
        ));

        utilisateur user = new utilisateur();
        user.setId(9L);
        user.setActive(false);
        user.setDateDesactivation(java.time.LocalDate.now());
        when(utilisateurRepository.findByKeycloakId("kc-sub-3")).thenReturn(Optional.of(user));

        assertThat(jwtService.extractUserId("some-token")).isEqualTo(9L);
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void extractUserIdResolvesViaEmailFallbackWhenSubjectNotFound() {
        stubDecoderClaims(Map.of(
            "iss", "http://localhost:8088/realms/PFE26",
            "sub", "kc-sub-unknown",
            "email", "user@test.lanacash.ma"
        ));

        utilisateur user = new utilisateur();
        user.setId(11L);
        user.setActive(true);
        when(utilisateurRepository.findByKeycloakId("kc-sub-unknown")).thenReturn(Optional.empty());
        when(utilisateurRepository.findByEmailIgnoreCase("user@test.lanacash.ma")).thenReturn(Optional.of(user));

        assertThat(jwtService.extractUserId("some-token")).isEqualTo(11L);
        assertThat(user.getKeycloakId()).isEqualTo("kc-sub-unknown");
        verify(utilisateurRepository).save(user);
    }

    @Test
    void extractUserIdThrowsUnauthorizedWithCandidatesWhenNoUserMatches() {
        stubDecoderClaims(Map.of(
            "iss", "http://localhost:8088/realms/PFE26",
            "sub", "kc-sub-missing",
            "email", "missing@test.lanacash.ma"
        ));

        when(utilisateurRepository.findByKeycloakId("kc-sub-missing")).thenReturn(Optional.empty());
        when(utilisateurRepository.findByEmailIgnoreCase("missing@test.lanacash.ma")).thenReturn(Optional.empty());

        // Le message renvoye au client reste generique : les details (email/sub)
        // ne doivent jamais fuiter cote reponse HTTP, sinon un attaquant peut
        // enumerer les comptes existants en observant les differences de message.
        // Ces details sont uniquement journalises cote serveur (JwtService.keycloakUserNotFound).
        assertThatThrownBy(() -> jwtService.extractUserId("some-token"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Authentification Keycloak invalide.")
            .extracting(throwable -> ((ResponseStatusException) throwable).getStatusCode())
            .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }

    @Test
    void extractUserIdThrowsUnauthorizedWithoutCandidatesMessageWhenNoEmailInToken() {
        stubDecoderClaims(Map.of(
            "iss", "http://localhost:8088/realms/PFE26",
            "sub", "kc-sub-noemail"
        ));

        when(utilisateurRepository.findByKeycloakId("kc-sub-noemail")).thenReturn(Optional.empty());

        // Meme raisonnement que ci-dessus : message client generique, le detail
        // "scope email manquant" n'est journalise que cote serveur.
        assertThatThrownBy(() -> jwtService.extractUserId("some-token"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Authentification Keycloak invalide.")
            .extracting(throwable -> ((ResponseStatusException) throwable).getStatusCode())
            .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }

    private void stubDecoderClaims(Map<String, Object> claims) {
        Jwt jwt = Jwt.withTokenValue("fake-token")
            .header("alg", "none")
            .claims(map -> map.putAll(claims))
            .build();
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode(org.mockito.ArgumentMatchers.anyString())).thenReturn(jwt);
        when(decoderProvider.getIfAvailable()).thenReturn(decoder);
    }
}
