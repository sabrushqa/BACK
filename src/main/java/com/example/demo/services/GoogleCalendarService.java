package com.example.demo.services;

import com.example.demo.dto.GoogleCalendarAuthorizationResponse;
import com.example.demo.dto.GoogleCalendarCallbackRequest;
import com.example.demo.dto.GoogleCalendarStatusResponse;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.google_calendar_connection;
import com.example.demo.entities.interaction_commerciale;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.GoogleCalendarConnectionRepository;
import com.example.demo.repositories.InteractionCommercialeRepository;
import com.example.demo.repositories.UtilisateurRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Transactional
public class GoogleCalendarService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleCalendarService.class);
    private static final String CALENDAR_SCOPE =
        "https://www.googleapis.com/auth/calendar.events.owned";
    private static final int STATE_VALIDITY_MINUTES = 10;

    private final GoogleCalendarConnectionRepository connectionRepository;
    private final InteractionCommercialeRepository interactionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final JwtService jwtService;
    private final GoogleCalendarTokenCipher tokenCipher;
    private final RestClient restClient;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String frontendBaseUrl;
    private final String authorizationEndpoint;
    private final String tokenEndpoint;
    private final String calendarApiBaseUrl;

    public GoogleCalendarService(
        GoogleCalendarConnectionRepository connectionRepository,
        InteractionCommercialeRepository interactionRepository,
        UtilisateurRepository utilisateurRepository,
        JwtService jwtService,
        GoogleCalendarTokenCipher tokenCipher,
        RestClient.Builder restClientBuilder,
        @Value("${app.google-calendar.client-id:}") String clientId,
        @Value("${app.google-calendar.client-secret:}") String clientSecret,
        @Value("${app.google-calendar.redirect-uri:}") String configuredRedirectUri,
        @Value("${app.frontend.base-url:http://localhost:4200}") String frontendBaseUrl,
        @Value("${app.google-calendar.authorization-endpoint:https://accounts.google.com/o/oauth2/v2/auth}")
        String authorizationEndpoint,
        @Value("${app.google-calendar.token-endpoint:https://oauth2.googleapis.com/token}")
        String tokenEndpoint,
        @Value("${app.google-calendar.api-base-url:https://www.googleapis.com/calendar/v3}")
        String calendarApiBaseUrl
    ) {
        this.connectionRepository = connectionRepository;
        this.interactionRepository = interactionRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.jwtService = jwtService;
        this.tokenCipher = tokenCipher;
        this.restClient = restClientBuilder.build();
        this.clientId = normalize(clientId);
        this.clientSecret = normalize(clientSecret);
        this.frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);
        this.redirectUri = StringUtils.hasText(configuredRedirectUri)
            ? configuredRedirectUri.trim()
            : this.frontendBaseUrl + "/commercial/calendrier";
        this.authorizationEndpoint = stripTrailingSlash(authorizationEndpoint);
        this.tokenEndpoint = tokenEndpoint == null ? "" : tokenEndpoint.trim();
        this.calendarApiBaseUrl = stripTrailingSlash(calendarApiBaseUrl);
    }

    @Transactional(readOnly = true)
    public GoogleCalendarStatusResponse getStatus(String authorizationHeader) {
        utilisateur user = readAuthenticatedCommercial(authorizationHeader);
        boolean configured = isConfigured();
        boolean connected = configured && connectionRepository
            .findByUtilisateur_Id(user.getId())
            .map(connection -> StringUtils.hasText(connection.getRefreshTokenEncrypted()))
            .orElse(false);

        String message = !configured
            ? "Google Calendar doit être configuré par l’administrateur."
            : connected
                ? "Google Calendar est connecté."
                : "Connectez votre calendrier Google pour synchroniser les relances.";
        return new GoogleCalendarStatusResponse(configured, connected, message);
    }

    public GoogleCalendarAuthorizationResponse beginAuthorization(String authorizationHeader) {
        utilisateur user = readAuthenticatedCommercial(authorizationHeader);
        requireConfigured();

        byte[] stateBytes = new byte[32];
        secureRandom.nextBytes(stateBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);

        google_calendar_connection connection = connectionRepository
            .findByUtilisateur_Id(user.getId())
            .orElseGet(google_calendar_connection::new);
        connection.setUtilisateur(user);
        connection.setOauthStateHash(sha256(state));
        connection.setOauthStateExpiresAt(LocalDateTime.now().plusMinutes(STATE_VALIDITY_MINUTES));
        connectionRepository.save(connection);

        String authorizationUrl = UriComponentsBuilder
            .fromUriString(authorizationEndpoint)
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", CALENDAR_SCOPE)
            .queryParam("access_type", "offline")
            .queryParam("include_granted_scopes", "true")
            .queryParam("prompt", "consent")
            .queryParam("state", state)
            .build()
            .encode()
            .toUriString();
        return new GoogleCalendarAuthorizationResponse(authorizationUrl);
    }

    public GoogleCalendarStatusResponse completeAuthorization(
        String authorizationHeader,
        GoogleCalendarCallbackRequest request
    ) {
        utilisateur user = readAuthenticatedCommercial(authorizationHeader);
        requireConfigured();
        if (request == null || !StringUtils.hasText(request.code()) || !StringUtils.hasText(request.state())) {
            throw new IllegalArgumentException("La réponse d'autorisation Google Calendar est incomplète.");
        }

        google_calendar_connection connection = connectionRepository
            .findByUtilisateur_Id(user.getId())
            .orElseThrow(() -> new IllegalArgumentException("La connexion Google Calendar a expiré."));
        boolean expired = connection.getOauthStateExpiresAt() == null
            || connection.getOauthStateExpiresAt().isBefore(LocalDateTime.now());
        boolean stateMatches = secureEquals(connection.getOauthStateHash(), sha256(request.state().trim()));
        if (expired || !stateMatches) {
            clearOauthState(connection);
            connectionRepository.save(connection);
            throw new IllegalArgumentException("La connexion Google Calendar a expiré. Veuillez recommencer.");
        }

        Map<String, Object> tokenResponse = exchangeAuthorizationCode(request.code().trim());
        String refreshToken = asString(tokenResponse.get("refresh_token"));
        if (!StringUtils.hasText(refreshToken)) {
            throw new IllegalStateException(
                "Google n'a pas fourni de jeton de renouvellement. Retirez l'accès LanaCash dans Google puis reconnectez-vous."
            );
        }

        connection.setRefreshTokenEncrypted(tokenCipher.encrypt(refreshToken));
        connection.setConnectedAt(LocalDateTime.now());
        clearOauthState(connection);
        connectionRepository.save(connection);
        return new GoogleCalendarStatusResponse(true, true, "Google Calendar est connecté.");
    }

    public SyncResult createReminder(
        utilisateur user,
        interaction_commerciale interaction,
        dossier_affiliation dossier,
        LocalDate reminderDate
    ) {
        if (user == null || interaction == null || dossier == null || reminderDate == null) {
            return SyncResult.notAttempted(null);
        }
        if (!isConfigured()) {
            return SyncResult.notAttempted("Google Calendar n’est pas configuré.");
        }

        google_calendar_connection connection = connectionRepository
            .findByUtilisateur_Id(user.getId())
            .filter(value -> StringUtils.hasText(value.getRefreshTokenEncrypted()))
            .orElse(null);
        if (connection == null) {
            return SyncResult.notAttempted("Google Calendar n’est pas connecté.");
        }

        try {
            String refreshToken = tokenCipher.decrypt(connection.getRefreshTokenEncrypted());
            String accessToken = refreshAccessToken(refreshToken);
            Map<String, Object> eventResponse = insertEvent(accessToken, interaction, dossier, reminderDate);
            String eventId = asString(eventResponse.get("id"));
            String eventUrl = asString(eventResponse.get("htmlLink"));
            interaction.setGoogleCalendarEventId(eventId);
            interaction.setGoogleCalendarEventUrl(eventUrl);
            interactionRepository.save(interaction);
            return new SyncResult(true, true, "Relance ajoutée à Google Calendar.", eventUrl);
        } catch (Exception exception) {
            if (exception instanceof RejectedGoogleCredentialsException) {
                connection.setRefreshTokenEncrypted(null);
                connection.setConnectedAt(null);
                connectionRepository.save(connection);
            }
            LOGGER.warn(
                "Synchronisation Google Calendar impossible pour l'interaction {} : {}",
                interaction.getIdInteraction(),
                exception.getMessage()
            );
            return new SyncResult(
                true,
                false,
                "Google Calendar n’a pas pu être synchronisé.",
                null
            );
        }
    }

    private Map<String, Object> exchangeAuthorizationCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", redirectUri);
        return postTokenForm(form);
    }

    private String refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        Map<String, Object> response;
        try {
            response = postTokenForm(form);
        } catch (IllegalStateException exception) {
            if (hasHttpStatus(exception, 400)) {
                throw new RejectedGoogleCredentialsException(exception);
            }
            throw exception;
        }
        String accessToken = asString(response.get("access_token"));
        if (!StringUtils.hasText(accessToken)) {
            throw new IllegalStateException("Google n'a pas fourni de jeton d'accès.");
        }
        return accessToken;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postTokenForm(MultiValueMap<String, String> form) {
        try {
            Map<String, Object> response = restClient
                .post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(Map.class);
            if (response == null) {
                throw new IllegalStateException("Réponse vide du service d'autorisation Google.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new IllegalStateException("Le service d'autorisation Google est indisponible.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> insertEvent(
        String accessToken,
        interaction_commerciale interaction,
        dossier_affiliation dossier,
        LocalDate reminderDate
    ) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("summary", buildEventTitle(interaction, dossier));
        event.put("description", buildEventDescription(interaction, dossier));
        event.put("start", Map.of("date", reminderDate.toString()));
        event.put("end", Map.of("date", reminderDate.plusDays(1).toString()));
        event.put("reminders", Map.of("useDefault", true));
        event.put(
            "extendedProperties",
            Map.of(
                "private",
                Map.of(
                    "lanaCashInteractionId", String.valueOf(interaction.getIdInteraction()),
                    "lanaCashDossierId", String.valueOf(dossier.getIdDossier())
                )
            )
        );

        try {
            Map<String, Object> response = restClient
                .post()
                .uri(calendarApiBaseUrl + "/calendars/primary/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(event)
                .retrieve()
                .body(Map.class);
            if (response == null || !StringUtils.hasText(asString(response.get("id")))) {
                throw new IllegalStateException("Google Calendar n'a pas confirmé la création de l'événement.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new IllegalStateException("Le service Google Calendar est indisponible.", exception);
        }
    }

    private String buildEventTitle(interaction_commerciale interaction, dossier_affiliation dossier) {
        String type = interaction.getProchaineRelanceType() == null
            ? "Relance"
            : formatEnum(interaction.getProchaineRelanceType().name());
        return type + " — " + merchantDisplayName(dossier);
    }

    private String buildEventDescription(interaction_commerciale interaction, dossier_affiliation dossier) {
        StringBuilder description = new StringBuilder()
            .append("Dossier LanaCash #")
            .append(dossier.getIdDossier())
            .append("\nProspect : ")
            .append(merchantDisplayName(dossier));
        if (StringUtils.hasText(interaction.getResultat())) {
            description.append("\nRésultat : ").append(interaction.getResultat().trim());
        }
        if (StringUtils.hasText(interaction.getCommentaire())) {
            description.append("\nCommentaire : ").append(interaction.getCommentaire().trim());
        }
        description
            .append("\n\nOuvrir le dossier : ")
            .append(frontendBaseUrl)
            .append("/commercial/demandes-commerciales/")
            .append(dossier.getIdDossier());
        return description.toString();
    }

    private String merchantDisplayName(dossier_affiliation dossier) {
        commercant merchant = dossier.getCommercant();
        if (merchant != null) {
            if (StringUtils.hasText(merchant.getNomCommercial())) {
                return merchant.getNomCommercial().trim();
            }
            if (StringUtils.hasText(merchant.getRaisonSociale())) {
                return merchant.getRaisonSociale().trim();
            }
            if (StringUtils.hasText(merchant.getEmailContact())) {
                return merchant.getEmailContact().trim();
            }
        }
        return "Dossier #" + dossier.getIdDossier();
    }

    private utilisateur readAuthenticatedCommercial(String authorizationHeader) {
        String token = jwtService.extractBearerToken(authorizationHeader)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification Keycloak requise."));
        if (jwtService.isTokenExpired(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session Keycloak expirée.");
        }
        Long userId = jwtService.extractUserId(token);
        utilisateur user = userId == null
            ? null
            : utilisateurRepository.findById(userId).orElse(null);
        if (user == null || jwtService.isSessionInvalidated(token, user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session Keycloak invalide.");
        }
        if (user.getRole() != RoleUser.COMMERCIAL) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "La connexion Google Calendar est réservée aux commerciaux."
            );
        }
        return user;
    }

    private boolean isConfigured() {
        return StringUtils.hasText(clientId)
            && StringUtils.hasText(clientSecret)
            && StringUtils.hasText(redirectUri)
            && tokenCipher.isConfigured();
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Google Calendar doit être configuré par l’administrateur."
            );
        }
    }

    private void clearOauthState(google_calendar_connection connection) {
        connection.setOauthStateHash(null);
        connection.setOauthStateExpiresAt(null);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponible.", exception);
        }
    }

    private boolean secureEquals(String expected, String actual) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(actual)) {
            return false;
        }
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String asString(Object value) {
        return value instanceof String text ? text : "";
    }

    private boolean hasHttpStatus(Throwable exception, int expectedStatus) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof RestClientResponseException responseException) {
                return responseException.getStatusCode().value() == expectedStatus;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class RejectedGoogleCredentialsException extends IllegalStateException {
        private RejectedGoogleCredentialsException(Throwable cause) {
            super("La connexion Google Calendar a expiré.", cause);
        }
    }

    private String formatEnum(String value) {
        String lower = value.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String stripTrailingSlash(String value) {
        return normalize(value).replaceAll("/+$", "");
    }

    public record SyncResult(
        boolean attempted,
        boolean synced,
        String message,
        String eventUrl
    ) {
        public static SyncResult notAttempted(String message) {
            return new SyncResult(false, false, message, null);
        }
    }
}
