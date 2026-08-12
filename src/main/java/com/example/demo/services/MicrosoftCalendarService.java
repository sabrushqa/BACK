package com.example.demo.services;

import com.example.demo.dto.MicrosoftCalendarAuthorizationResponse;
import com.example.demo.dto.MicrosoftCalendarCallbackRequest;
import com.example.demo.dto.MicrosoftCalendarStatusResponse;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.interaction_commerciale;
import com.example.demo.entities.microsoft_calendar_connection;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.InteractionCommercialeRepository;
import com.example.demo.repositories.MicrosoftCalendarConnectionRepository;
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
import java.util.UUID;
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
public class MicrosoftCalendarService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MicrosoftCalendarService.class);
    private static final String CALENDAR_SCOPE =
        "offline_access https://graph.microsoft.com/Calendars.ReadWrite";
    private static final String STATE_PREFIX = "microsoft.";
    private static final int STATE_VALIDITY_MINUTES = 10;

    private final MicrosoftCalendarConnectionRepository connectionRepository;
    private final InteractionCommercialeRepository interactionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final JwtService jwtService;
    private final MicrosoftCalendarTokenCipher tokenCipher;
    private final RestClient restClient;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String frontendBaseUrl;
    private final String authorizationEndpoint;
    private final String tokenEndpoint;
    private final String graphApiBaseUrl;

    public MicrosoftCalendarService(
        MicrosoftCalendarConnectionRepository connectionRepository,
        InteractionCommercialeRepository interactionRepository,
        UtilisateurRepository utilisateurRepository,
        JwtService jwtService,
        MicrosoftCalendarTokenCipher tokenCipher,
        RestClient.Builder restClientBuilder,
        @Value("${app.microsoft-calendar.client-id:}") String clientId,
        @Value("${app.microsoft-calendar.client-secret:}") String clientSecret,
        @Value("${app.microsoft-calendar.redirect-uri:}") String configuredRedirectUri,
        @Value("${app.frontend.base-url:http://localhost:4200}") String frontendBaseUrl,
        @Value("${app.microsoft-calendar.authorization-endpoint:https://login.microsoftonline.com/common/oauth2/v2.0/authorize}")
        String authorizationEndpoint,
        @Value("${app.microsoft-calendar.token-endpoint:https://login.microsoftonline.com/common/oauth2/v2.0/token}")
        String tokenEndpoint,
        @Value("${app.microsoft-calendar.graph-api-base-url:https://graph.microsoft.com/v1.0}")
        String graphApiBaseUrl
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
        this.tokenEndpoint = normalize(tokenEndpoint);
        this.graphApiBaseUrl = stripTrailingSlash(graphApiBaseUrl);
    }

    @Transactional(readOnly = true)
    public MicrosoftCalendarStatusResponse getStatus(String authorizationHeader) {
        utilisateur user = readAuthenticatedCommercial(authorizationHeader);
        boolean configured = isConfigured();
        boolean connected = configured && connectionRepository
            .findByUtilisateur_Id(user.getId())
            .map(connection -> StringUtils.hasText(connection.getRefreshTokenEncrypted()))
            .orElse(false);

        String message = !configured
            ? "Outlook Calendar doit être configuré par l’administrateur."
            : connected
                ? "Outlook Calendar est connecté."
                : "Connectez votre calendrier Outlook pour synchroniser les relances.";
        return new MicrosoftCalendarStatusResponse(configured, connected, message);
    }

    public MicrosoftCalendarAuthorizationResponse beginAuthorization(String authorizationHeader) {
        utilisateur user = readAuthenticatedCommercial(authorizationHeader);
        requireConfigured();

        byte[] stateBytes = new byte[32];
        secureRandom.nextBytes(stateBytes);
        String state = STATE_PREFIX
            + Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);

        microsoft_calendar_connection connection = connectionRepository
            .findByUtilisateur_Id(user.getId())
            .orElseGet(microsoft_calendar_connection::new);
        connection.setUtilisateur(user);
        connection.setOauthStateHash(sha256(state));
        connection.setOauthStateExpiresAt(LocalDateTime.now().plusMinutes(STATE_VALIDITY_MINUTES));
        connectionRepository.save(connection);

        String authorizationUrl = UriComponentsBuilder
            .fromUriString(authorizationEndpoint)
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("response_mode", "query")
            .queryParam("scope", CALENDAR_SCOPE)
            .queryParam("prompt", "select_account")
            .queryParam("state", state)
            .build()
            .encode()
            .toUriString();
        return new MicrosoftCalendarAuthorizationResponse(authorizationUrl);
    }

    public MicrosoftCalendarStatusResponse completeAuthorization(
        String authorizationHeader,
        MicrosoftCalendarCallbackRequest request
    ) {
        utilisateur user = readAuthenticatedCommercial(authorizationHeader);
        requireConfigured();
        if (request == null || !StringUtils.hasText(request.code()) || !StringUtils.hasText(request.state())) {
            throw new IllegalArgumentException("La réponse d'autorisation Outlook Calendar est incomplète.");
        }
        if (!request.state().trim().startsWith(STATE_PREFIX)) {
            throw new IllegalArgumentException("La réponse d'autorisation Outlook Calendar est invalide.");
        }

        microsoft_calendar_connection connection = connectionRepository
            .findByUtilisateur_Id(user.getId())
            .orElseThrow(() -> new IllegalArgumentException("La connexion Outlook Calendar a expiré."));
        boolean expired = connection.getOauthStateExpiresAt() == null
            || connection.getOauthStateExpiresAt().isBefore(LocalDateTime.now());
        boolean stateMatches = secureEquals(connection.getOauthStateHash(), sha256(request.state().trim()));
        if (expired || !stateMatches) {
            clearOauthState(connection);
            connectionRepository.save(connection);
            throw new IllegalArgumentException("La connexion Outlook Calendar a expiré. Veuillez recommencer.");
        }

        Map<String, Object> tokenResponse = exchangeAuthorizationCode(request.code().trim());
        String refreshToken = asString(tokenResponse.get("refresh_token"));
        if (!StringUtils.hasText(refreshToken)) {
            throw new IllegalStateException(
                "Microsoft n'a pas fourni de jeton de renouvellement. Vérifiez le scope offline_access puis reconnectez-vous."
            );
        }

        connection.setRefreshTokenEncrypted(tokenCipher.encrypt(refreshToken));
        connection.setConnectedAt(LocalDateTime.now());
        clearOauthState(connection);
        connectionRepository.save(connection);
        return new MicrosoftCalendarStatusResponse(true, true, "Outlook Calendar est connecté.");
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
            return SyncResult.notAttempted("Outlook Calendar n’est pas configuré.");
        }

        microsoft_calendar_connection connection = connectionRepository
            .findByUtilisateur_Id(user.getId())
            .filter(value -> StringUtils.hasText(value.getRefreshTokenEncrypted()))
            .orElse(null);
        if (connection == null) {
            return SyncResult.notAttempted("Outlook Calendar n’est pas connecté.");
        }

        try {
            String refreshToken = tokenCipher.decrypt(connection.getRefreshTokenEncrypted());
            RefreshedToken refreshedToken = refreshAccessToken(refreshToken);
            if (StringUtils.hasText(refreshedToken.refreshToken())) {
                connection.setRefreshTokenEncrypted(tokenCipher.encrypt(refreshedToken.refreshToken()));
                connectionRepository.save(connection);
            }
            Map<String, Object> eventResponse = insertEvent(
                refreshedToken.accessToken(),
                interaction,
                dossier,
                reminderDate
            );
            String eventId = asString(eventResponse.get("id"));
            String eventUrl = asString(eventResponse.get("webLink"));
            interaction.setMicrosoftCalendarEventId(eventId);
            interaction.setMicrosoftCalendarEventUrl(eventUrl);
            interactionRepository.save(interaction);
            return new SyncResult(true, true, "Relance ajoutée à Outlook Calendar.", eventUrl);
        } catch (Exception exception) {
            if (exception instanceof RejectedMicrosoftCredentialsException) {
                connection.setRefreshTokenEncrypted(null);
                connection.setConnectedAt(null);
                connectionRepository.save(connection);
            }
            LOGGER.warn(
                "Synchronisation Outlook Calendar impossible pour l'interaction {} : {}",
                interaction.getIdInteraction(),
                exception.getMessage()
            );
            return new SyncResult(true, false, "Outlook Calendar n’a pas pu être synchronisé.", null);
        }
    }

    private Map<String, Object> exchangeAuthorizationCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", redirectUri);
        form.add("scope", CALENDAR_SCOPE);
        return postTokenForm(form);
    }

    private RefreshedToken refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        form.add("scope", CALENDAR_SCOPE);
        Map<String, Object> response;
        try {
            response = postTokenForm(form);
        } catch (IllegalStateException exception) {
            if (hasHttpStatus(exception, 400)) {
                throw new RejectedMicrosoftCredentialsException(exception);
            }
            throw exception;
        }
        String accessToken = asString(response.get("access_token"));
        if (!StringUtils.hasText(accessToken)) {
            throw new IllegalStateException("Microsoft n'a pas fourni de jeton d'accès.");
        }
        return new RefreshedToken(accessToken, asString(response.get("refresh_token")));
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
                throw new IllegalStateException("Réponse vide du service d'autorisation Microsoft.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new IllegalStateException("Le service d'autorisation Microsoft est indisponible.", exception);
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
        event.put("subject", buildEventTitle(interaction, dossier));
        event.put("body", Map.of("contentType", "text", "content", buildEventDescription(interaction, dossier)));
        event.put("start", Map.of("dateTime", reminderDate + "T00:00:00", "timeZone", "UTC"));
        event.put("end", Map.of("dateTime", reminderDate.plusDays(1) + "T00:00:00", "timeZone", "UTC"));
        event.put("isAllDay", true);
        event.put("isReminderOn", true);
        event.put("reminderMinutesBeforeStart", 60);
        event.put(
            "transactionId",
            UUID.nameUUIDFromBytes(
                ("lanacash-outlook-" + interaction.getIdInteraction()).getBytes(StandardCharsets.UTF_8)
            ).toString()
        );

        try {
            Map<String, Object> response = restClient
                .post()
                .uri(graphApiBaseUrl + "/me/calendar/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(event)
                .retrieve()
                .body(Map.class);
            if (response == null || !StringUtils.hasText(asString(response.get("id")))) {
                throw new IllegalStateException("Outlook Calendar n'a pas confirmé la création de l'événement.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new IllegalStateException("Le service Microsoft Graph est indisponible.", exception);
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
        utilisateur user = userId == null ? null : utilisateurRepository.findById(userId).orElse(null);
        if (user == null || jwtService.isSessionInvalidated(token, user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session Keycloak invalide.");
        }
        if (user.getRole() != RoleUser.COMMERCIAL) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "La connexion Outlook Calendar est réservée aux commerciaux."
            );
        }
        return user;
    }

    private boolean isConfigured() {
        return StringUtils.hasText(clientId)
            && StringUtils.hasText(clientSecret)
            && StringUtils.hasText(redirectUri)
            && StringUtils.hasText(authorizationEndpoint)
            && StringUtils.hasText(tokenEndpoint)
            && StringUtils.hasText(graphApiBaseUrl)
            && tokenCipher.isConfigured();
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Outlook Calendar doit être configuré par l’administrateur."
            );
        }
    }

    private void clearOauthState(microsoft_calendar_connection connection) {
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

    private record RefreshedToken(String accessToken, String refreshToken) {
    }

    private static final class RejectedMicrosoftCredentialsException extends IllegalStateException {
        private RejectedMicrosoftCredentialsException(Throwable cause) {
            super("La connexion Outlook Calendar a expiré.", cause);
        }
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
