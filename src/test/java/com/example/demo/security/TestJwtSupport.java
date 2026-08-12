package com.example.demo.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Fournit un JwtDecoder de test signe avec une cle HMAC fixe (au lieu d'appeler
 * un vrai Keycloak), et un utilitaire pour fabriquer des tokens signes avec la
 * meme cle. JwtService.readClaims() decode le token brut envoye dans le header
 * Authorization: ce decoder de test doit donc etre le seul JwtDecoder du
 * contexte pour que les tokens fabriques ici soient acceptes.
 */
@TestConfiguration
public class TestJwtSupport {

    public static final String TEST_SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    public static final String TEST_ISSUER = "http://localhost:8088/realms/PFE26";

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(TEST_SECRET.getBytes(), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
    }

    public static String mintKeycloakToken(String subject, String email, long expiresInSeconds) {
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build();
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(TEST_ISSUER)
                .subject(subject)
                .claim("email", email)
                .claim("preferred_username", email)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(expiresInSeconds)))
                .build();
            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(new MACSigner(TEST_SECRET.getBytes()));
            return signedJWT.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible de fabriquer un token de test.", exception);
        }
    }

    public static String mintExpiredToken(String subject, String email) {
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build();
            Instant past = Instant.now().minusSeconds(3600);
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(TEST_ISSUER)
                .subject(subject)
                .claim("email", email)
                .issueTime(Date.from(past.minusSeconds(60)))
                .expirationTime(Date.from(past))
                .build();
            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(new MACSigner(TEST_SECRET.getBytes()));
            return signedJWT.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible de fabriquer un token de test.", exception);
        }
    }
}
