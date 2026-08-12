package com.example.demo.services;

import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class KeycloakAdminService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakAdminService.class);

    private final RestClient restClient;
    private final boolean enabled;
    private final String serverUrl;
    private final String realm;
    private final String clientId;
    private final String adminRealm;
    private final String adminClientId;
    private final String adminUsername;
    private final String adminPassword;
    private final boolean otpRequired;

    public KeycloakAdminService(
        RestClient.Builder restClientBuilder,
        @Value("${app.keycloak.admin.enabled:true}") boolean enabled,
        @Value("${app.keycloak.server-url:http://localhost:8088}") String serverUrl,
        @Value("${app.keycloak.realm:PFE26}") String realm,
        @Value("${app.keycloak.client-id:portail-affiliation}") String clientId,
        @Value("${app.keycloak.admin.realm:master}") String adminRealm,
        @Value("${app.keycloak.admin.client-id:admin-cli}") String adminClientId,
        @Value("${app.keycloak.admin.username:}") String adminUsername,
        @Value("${app.keycloak.admin.password:}") String adminPassword,
        @Value("${app.keycloak.otp.required:false}") boolean otpRequired
    ) {
        this.restClient = restClientBuilder.build();
        this.enabled = enabled;
        this.serverUrl = stripTrailingSlash(serverUrl);
        this.realm = realm;
        this.clientId = clientId;
        this.adminRealm = adminRealm;
        this.adminClientId = adminClientId;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.otpRequired = otpRequired;
    }

    public boolean provisionUser(utilisateur utilisateur, String temporaryPassword) {
        if (!isReady()) {
            return false;
        }

        String email = normalizeEmail(utilisateur.getEmail());
        if (!StringUtils.hasText(email)) {
            return false;
        }

        try {
            String accessToken = adminAccessToken();
            Optional<String> existingUserId = findUserIdByEmail(accessToken, email);
            String keycloakUserId = existingUserId.orElseGet(() -> createUser(accessToken, utilisateur, temporaryPassword));
            utilisateur.setKeycloakId(keycloakUserId);
            resetPassword(accessToken, keycloakUserId, temporaryPassword, true);
            updateUser(accessToken, keycloakUserId, Map.of(
                "enabled", true,
                "emailVerified", true,
                "requiredActions", accountSetupRequiredActions()
            ));
            assignRealmRole(accessToken, keycloakUserId, utilisateur.getRole());
            return true;
        } catch (RestClientException | IllegalStateException exception) {
            LOGGER.warn("Synchronisation Keycloak impossible pour {}: {}", email, exception.getMessage());
            return false;
        }
    }

    public void setPermanentPassword(utilisateur utilisateur, String newPassword) {
        if (!isReady()) {
            throw new IllegalStateException(
                "Administration Keycloak non configurée. Renseignez app.keycloak.admin.username et app.keycloak.admin.password."
            );
        }
        if (utilisateur == null || !StringUtils.hasText(newPassword)) {
            throw new IllegalArgumentException("Utilisateur ou mot de passe Keycloak invalide.");
        }

        String accessToken = adminAccessToken();
        String email = normalizeEmail(utilisateur.getEmail());
        String keycloakUserId = resolveExistingUserId(accessToken, utilisateur, email)
            .orElseGet(() -> createActivatedUser(accessToken, utilisateur, newPassword));

        clearActivationRequirements(accessToken, keycloakUserId);
        resetPassword(accessToken, keycloakUserId, newPassword, false);
        clearActivationRequirements(accessToken, keycloakUserId);
        applyPostPasswordRequiredActions(accessToken, keycloakUserId);
        assignRealmRole(accessToken, keycloakUserId, utilisateur.getRole());
        assertActivatedUser(accessToken, keycloakUserId, email);
        utilisateur.setKeycloakId(keycloakUserId);
    }

    public boolean passwordMatches(utilisateur utilisateur, String password) {
        if (!isReady() || utilisateur == null || !StringUtils.hasText(password)) {
            return false;
        }

        String email = normalizeEmail(utilisateur.getEmail());
        if (!StringUtils.hasText(email)) {
            return false;
        }

        try {
            restClient
                .post()
                .uri(uri("/realms/" + encodePath(realm) + "/protocol/openid-connect/token"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(
                    "grant_type=password"
                        + "&client_id=" + encode(clientId)
                        + "&username=" + encode(email)
                        + "&password=" + encode(password.trim())
                )
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException exception) {
            String responseBody = exception.getResponseBodyAsString();
            return responseBody != null
                && responseBody.toLowerCase(Locale.ROOT).contains("account is not fully set up");
        } catch (RestClientException exception) {
            LOGGER.warn("Validation du mot de passe Keycloak impossible pour {}: {}", email, exception.getMessage());
            return false;
        }
    }

    public boolean sendPasswordSetupEmail(utilisateur utilisateur, String redirectUri) {
        if (!isReady() || utilisateur == null) {
            return false;
        }

        String email = normalizeEmail(utilisateur.getEmail());
        if (!StringUtils.hasText(email)) {
            return false;
        }

        try {
            String accessToken = adminAccessToken();
            Optional<String> keycloakUserId = StringUtils.hasText(utilisateur.getKeycloakId())
                ? Optional.of(utilisateur.getKeycloakId())
                : findUserIdByEmail(accessToken, email);

            if (keycloakUserId.isEmpty()) {
                return false;
            }

            restClient
                .put()
                .uri(uri(
                    "/admin/realms/" + encodePath(realm)
                        + "/users/" + encodePath(keycloakUserId.get())
                        + "/execute-actions-email?client_id=" + encode(clientId)
                        + "&redirect_uri=" + encode(redirectUri)
                ))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(accountSetupRequiredActions())
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (RestClientException | IllegalStateException exception) {
            LOGGER.warn("E-mail d'action Keycloak impossible pour {}: {}", email, exception.getMessage());
            return false;
        }
    }

    public boolean requireOtpForExistingUsers() {
        if (!isReady()) {
            LOGGER.warn("Activation OTP Keycloak ignorée: admin Keycloak non prêt.");
            return false;
        }

        if (!otpRequired) {
            LOGGER.info("Activation OTP Keycloak ignorée: app.keycloak.otp.required=false.");
            return true;
        }

        try {
            String accessToken = adminAccessToken();
            List<Map<String, Object>> users = findRealmUsers(accessToken);
            int updated = 0;

            for (Map<String, Object> user : users) {
                Object idObject = user.get("id");
                if (!(idObject instanceof String userId) || !StringUtils.hasText(userId)) {
                    continue;
                }

                if (!Boolean.TRUE.equals(user.get("enabled")) || userHasOtpCredential(accessToken, userId)) {
                    continue;
                }

                Set<String> requiredActions = new LinkedHashSet<>();
                Object actionsObject = user.get("requiredActions");
                if (actionsObject instanceof List<?> actions) {
                    actions.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .filter(StringUtils::hasText)
                        .forEach(requiredActions::add);
                }

                if (requiredActions.add("CONFIGURE_TOTP")) {
                    Map<String, Object> payload = new LinkedHashMap<>(user);
                    payload.put("requiredActions", new ArrayList<>(requiredActions));
                    updateUser(accessToken, userId, payload);
                    updated++;
                }
            }

            LOGGER.info("OTP Keycloak appliqué aux comptes existants sans TOTP ({} utilisateur(s) mis à jour).", updated);
            return true;
        } catch (RestClientException | IllegalStateException exception) {
            LOGGER.warn("Activation OTP Keycloak impossible: {}", exception.getMessage());
            return false;
        }
    }

    public void disableUser(utilisateur utilisateur) {
        if (!isReady() || utilisateur == null) {
            return;
        }

        try {
            String accessToken = adminAccessToken();
            Optional<String> keycloakUserId = StringUtils.hasText(utilisateur.getKeycloakId())
                ? Optional.of(utilisateur.getKeycloakId())
                : findUserIdByEmail(accessToken, normalizeEmail(utilisateur.getEmail()));

            keycloakUserId.ifPresent(userId ->
                restClient
                    .put()
                    .uri(uri("/admin/realms/" + encodePath(realm) + "/users/" + encodePath(userId)))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("enabled", false))
                    .retrieve()
                    .toBodilessEntity()
            );
        } catch (RestClientException | IllegalStateException exception) {
            LOGGER.warn("Désactivation Keycloak impossible pour {}: {}", utilisateur.getEmail(), exception.getMessage());
        }
    }

    /**
     * Pousse la configuration du client public Keycloak (redirectUris, webOrigins,
     * publicClient, PKCE) pour que {@code execute-actions-email} accepte le
     * paramètre {@code redirect_uri} et que le frontend Angular puisse négocier
     * un Authorization Code en local. Idempotent.
     *
     * @return {@code true} si la mise à jour a été appliquée, {@code false} sinon.
     */
    public boolean configureClient(List<String> redirectUris, List<String> webOrigins) {
        if (!isReady()) {
            LOGGER.warn("Configuration client Keycloak ignorée: admin Keycloak non prêt.");
            return false;
        }

        if (!StringUtils.hasText(clientId)) {
            LOGGER.warn("Configuration client Keycloak ignorée: clientId vide.");
            return false;
        }

        List<String> safeRedirects = redirectUris == null ? List.of() : redirectUris.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
        List<String> safeOrigins = webOrigins == null ? List.of() : webOrigins.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();

        if (safeRedirects.isEmpty()) {
            LOGGER.warn("Configuration client Keycloak ignorée: aucune redirectUri fournie.");
            return false;
        }

        try {
            String accessToken = adminAccessToken();
            Optional<Map<String, Object>> clientRepresentation = findClient(accessToken);
            if (clientRepresentation.isEmpty()) {
                LOGGER.warn(
                    "Configuration client Keycloak ignorée: client {} introuvable dans le realm {}.",
                    clientId, realm
                );
                return false;
            }

            Map<String, Object> client = new LinkedHashMap<>(clientRepresentation.get());
            Object idObject = client.get("id");
            if (!(idObject instanceof String clientUuid) || !StringUtils.hasText(clientUuid)) {
                LOGGER.warn("Configuration client Keycloak ignorée: UUID interne manquant.");
                return false;
            }

            client.put("redirectUris", safeRedirects);
            client.put("webOrigins", safeOrigins.isEmpty() ? List.of("+") : safeOrigins);
            client.put("publicClient", true);
            client.put("standardFlowEnabled", true);
            client.put("directAccessGrantsEnabled", true);
            client.put("serviceAccountsEnabled", false);

            Object attributesObject = client.get("attributes");
            Map<String, Object> attributes = attributesObject instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) attributesObject)
                : new LinkedHashMap<>();
            attributes.put("pkce.code.challenge.method", "S256");
            attributes.put("post.logout.redirect.uris", String.join("##", safeRedirects));
            client.put("attributes", attributes);

            restClient
                .put()
                .uri(uri("/admin/realms/" + encodePath(realm) + "/clients/" + encodePath(clientUuid)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(client)
                .retrieve()
                .toBodilessEntity();

            LOGGER.info(
                "Configuration client Keycloak appliquée (clientId={}, redirectUris={}).",
                clientId, safeRedirects
            );
            return true;
        } catch (RestClientException | IllegalStateException exception) {
            LOGGER.warn("Configuration client Keycloak impossible: {}", exception.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> findClient(String accessToken) {
        List<Map<String, Object>> clients = restClient
            .get()
            .uri(uri(
                "/admin/realms/" + encodePath(realm)
                    + "/clients?clientId=" + encode(clientId)
            ))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .body(List.class);

        if (clients == null || clients.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(clients.get(0));
    }

    /**
     * Pousse la configuration SMTP du realm Keycloak afin que les e-mails
     * (execute-actions-email, verify-email, reset-credentials) puissent partir.
     * Idempotent : peut être appelé à chaque démarrage de l'application.
     *
     * @return {@code true} si la configuration a été appliquée, {@code false} sinon.
     */
    public boolean configureRealmSmtp(
        String host,
        String port,
        String fromAddress,
        String fromDisplayName,
        String username,
        String password,
        boolean starttls,
        boolean ssl,
        String replyTo
    ) {
        if (!isReady()) {
            LOGGER.warn("Configuration SMTP Keycloak ignorée: admin Keycloak non prêt (vérifiez KEYCLOAK_ADMIN_USERNAME/PASSWORD).");
            return false;
        }

        if (!StringUtils.hasText(host) || !StringUtils.hasText(fromAddress)) {
            LOGGER.warn("Configuration SMTP Keycloak ignorée: host ou from manquant.");
            return false;
        }

        boolean useAuth = StringUtils.hasText(username) && StringUtils.hasText(password);

        Map<String, String> smtpServer = new LinkedHashMap<>();
        smtpServer.put("host", host);
        smtpServer.put("port", StringUtils.hasText(port) ? port : "587");
        smtpServer.put("from", fromAddress);
        smtpServer.put("fromDisplayName", StringUtils.hasText(fromDisplayName) ? fromDisplayName : "Lana Cash");
        smtpServer.put("replyTo", StringUtils.hasText(replyTo) ? replyTo : fromAddress);
        smtpServer.put("replyToDisplayName", StringUtils.hasText(fromDisplayName) ? fromDisplayName : "Lana Cash");
        smtpServer.put("envelopeFrom", fromAddress);
        smtpServer.put("starttls", Boolean.toString(starttls));
        smtpServer.put("ssl", Boolean.toString(ssl));
        smtpServer.put("auth", Boolean.toString(useAuth));
        if (useAuth) {
            smtpServer.put("user", username);
            smtpServer.put("password", password);
        }

        try {
            String accessToken = adminAccessToken();
            restClient
                .put()
                .uri(uri("/admin/realms/" + encodePath(realm)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("smtpServer", smtpServer))
                .retrieve()
                .toBodilessEntity();
            LOGGER.info("Configuration SMTP Keycloak appliquée au realm {} (host={}, from={}).", realm, host, fromAddress);
            return true;
        } catch (RestClientException | IllegalStateException exception) {
            LOGGER.warn("Configuration SMTP Keycloak impossible: {}", exception.getMessage());
            return false;
        }
    }

    private void resetPassword(String accessToken, String keycloakUserId, String password, boolean temporary) {
        if (!StringUtils.hasText(password)) {
            return;
        }

        restClient
            .put()
            .uri(uri("/admin/realms/" + encodePath(realm) + "/users/" + encodePath(keycloakUserId) + "/reset-password"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "type", "password",
                "value", password,
                "temporary", temporary
            ))
            .retrieve()
            .toBodilessEntity();
    }

    private void updateUser(String accessToken, String keycloakUserId, Map<String, Object> payload) {
        restClient
            .put()
            .uri(uri("/admin/realms/" + encodePath(realm) + "/users/" + encodePath(keycloakUserId)))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .toBodilessEntity();
    }

    private boolean isReady() {
        return enabled
            && StringUtils.hasText(serverUrl)
            && StringUtils.hasText(realm)
            && StringUtils.hasText(adminUsername)
            && StringUtils.hasText(adminPassword);
    }

    private String adminAccessToken() {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient
            .post()
            .uri(uri("/realms/" + encodePath(adminRealm) + "/protocol/openid-connect/token"))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                "grant_type=password"
                    + "&client_id=" + encode(adminClientId)
                    + "&username=" + encode(adminUsername)
                    + "&password=" + encode(adminPassword)
            )
            .retrieve()
            .body(Map.class);

        Object token = response == null ? null : response.get("access_token");
        if (!(token instanceof String value) || !StringUtils.hasText(value)) {
            throw new IllegalStateException("Token admin Keycloak introuvable.");
        }
        return value;
    }

    private Optional<String> findUserIdByEmail(String accessToken, String email) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> users = restClient
            .get()
            .uri(uri("/admin/realms/" + encodePath(realm) + "/users?email=" + encode(email) + "&exact=true"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .body(List.class);

        if (users == null || users.isEmpty()) {
            return Optional.empty();
        }

        Object id = users.get(0).get("id");
        return id instanceof String value && StringUtils.hasText(value)
            ? Optional.of(value)
            : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> findRealmUsers(String accessToken) {
        List<Map<String, Object>> users = restClient
            .get()
            .uri(uri("/admin/realms/" + encodePath(realm) + "/users?max=500"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .body(List.class);

        return users == null ? List.of() : users;
    }

    @SuppressWarnings("unchecked")
    private boolean userHasOtpCredential(String accessToken, String keycloakUserId) {
        List<Map<String, Object>> credentials = restClient
            .get()
            .uri(uri("/admin/realms/" + encodePath(realm) + "/users/" + encodePath(keycloakUserId) + "/credentials"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .body(List.class);

        if (credentials == null) {
            return false;
        }

        return credentials.stream()
            .map(credential -> credential.get("type"))
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .anyMatch(type -> "otp".equalsIgnoreCase(type));
    }

    private Optional<String> resolveExistingUserId(String accessToken, utilisateur utilisateur, String email) {
        if (StringUtils.hasText(utilisateur.getKeycloakId())) {
            String keycloakUserId = utilisateur.getKeycloakId().trim();
            try {
                if (findUserRepresentation(accessToken, keycloakUserId).isPresent()) {
                    return Optional.of(keycloakUserId);
                }
            } catch (HttpClientErrorException.NotFound exception) {
                LOGGER.warn(
                    "Utilisateur Keycloak {} introuvable pour {}, recherche par e-mail.",
                    keycloakUserId,
                    email
                );
            }
        }

        return findUserIdByEmail(accessToken, email);
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> findUserRepresentation(String accessToken, String keycloakUserId) {
        Map<String, Object> user = restClient
            .get()
            .uri(uri("/admin/realms/" + encodePath(realm) + "/users/" + encodePath(keycloakUserId)))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .body(Map.class);

        return user == null || user.isEmpty()
            ? Optional.empty()
            : Optional.of(user);
    }

    private void clearActivationRequirements(String accessToken, String keycloakUserId) {
        Map<String, Object> user = findUserRepresentation(accessToken, keycloakUserId)
            .map(LinkedHashMap::new)
            .orElseGet(LinkedHashMap::new);

        user.put("enabled", true);
        user.put("emailVerified", true);
        user.put("requiredActions", List.of());
        updateUser(accessToken, keycloakUserId, user);
    }

    private void applyPostPasswordRequiredActions(String accessToken, String keycloakUserId) {
        if (!otpRequired) {
            return;
        }

        Map<String, Object> user = findUserRepresentation(accessToken, keycloakUserId)
            .map(LinkedHashMap::new)
            .orElseGet(LinkedHashMap::new);

        user.put("enabled", true);
        user.put("emailVerified", true);
        user.put("requiredActions", List.of("CONFIGURE_TOTP"));
        updateUser(accessToken, keycloakUserId, user);
    }

    private void assertActivatedUser(String accessToken, String keycloakUserId, String email) {
        Map<String, Object> user = findUserRepresentation(accessToken, keycloakUserId)
            .orElseThrow(() -> new IllegalStateException("Utilisateur Keycloak introuvable après activation pour " + email + "."));

        boolean enabled = Boolean.TRUE.equals(user.get("enabled"));
        boolean emailVerified = Boolean.TRUE.equals(user.get("emailVerified"));
        List<?> requiredActions = Optional.ofNullable(user.get("requiredActions"))
            .filter(List.class::isInstance)
            .map(List.class::cast)
            .orElse(List.of());

        List<String> acceptedRequiredActions = otpRequired
            ? List.of("CONFIGURE_TOTP")
            : List.of();

        if (!enabled || !emailVerified || !acceptedRequiredActions.containsAll(requiredActions)) {
            throw new IllegalStateException(
                "Activation Keycloak incomplète pour "
                    + email
                    + " : enabled="
                    + enabled
                    + ", emailVerified="
                    + emailVerified
                    + ", requiredActions="
                    + requiredActions
                    + "."
            );
        }
    }

    private String createUser(String accessToken, utilisateur utilisateur, String temporaryPassword) {
        String email = normalizeEmail(utilisateur.getEmail());
        Map<String, Object> payload = Map.of(
            "username", email,
            "email", email,
            "emailVerified", true,
            "enabled", true,
            "requiredActions", accountSetupRequiredActions(),
            "credentials", List.of(
                Map.of(
                    "type", "password",
                    "value", StringUtils.hasText(temporaryPassword) ? temporaryPassword : generateFallbackPassword(),
                    "temporary", true
                )
            )
        );

        restClient
            .post()
            .uri(uri("/admin/realms/" + encodePath(realm) + "/users"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .toBodilessEntity();

        return findUserIdByEmail(accessToken, email)
            .orElseThrow(() -> new IllegalStateException("Utilisateur Keycloak créé mais introuvable."));
    }

    private String createActivatedUser(String accessToken, utilisateur utilisateur, String password) {
        String email = normalizeEmail(utilisateur.getEmail());
        Map<String, Object> payload = Map.of(
            "username", email,
            "email", email,
            "emailVerified", true,
            "enabled", true,
            "requiredActions", otpRequired ? List.of("CONFIGURE_TOTP") : List.of(),
            "credentials", List.of(
                Map.of(
                    "type", "password",
                    "value", password,
                    "temporary", false
                )
            )
        );

        restClient
            .post()
            .uri(uri("/admin/realms/" + encodePath(realm) + "/users"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .toBodilessEntity();

        return findUserIdByEmail(accessToken, email)
            .orElseThrow(() -> new IllegalStateException("Utilisateur Keycloak activé créé mais introuvable."));
    }

    private void assignRealmRole(String accessToken, String keycloakUserId, RoleUser role) {
        if (role == null) {
            return;
        }

        Map<String, Object> roleRepresentation = findOrCreateRealmRole(accessToken, role.name());
        if (roleRepresentation == null || roleRepresentation.isEmpty()) {
            return;
        }

        try {
            restClient
                .post()
                .uri(uri("/admin/realms/" + encodePath(realm) + "/users/" + encodePath(keycloakUserId) + "/role-mappings/realm"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(roleRepresentation))
                .retrieve()
                .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict exception) {
            // Role déjà affecté, l'opération est idempotente.
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findOrCreateRealmRole(String accessToken, String roleName) {
        try {
            return restClient
                .get()
                .uri(uri("/admin/realms/" + encodePath(realm) + "/roles/" + encodePath(roleName)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);
        } catch (HttpClientErrorException.NotFound exception) {
            restClient
                .post()
                .uri(uri("/admin/realms/" + encodePath(realm) + "/roles"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", roleName))
                .retrieve()
                .toBodilessEntity();

            return restClient
                .get()
                .uri(uri("/admin/realms/" + encodePath(realm) + "/roles/" + encodePath(roleName)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> accountSetupRequiredActions() {
        return otpRequired
            ? List.of("UPDATE_PASSWORD", "CONFIGURE_TOTP")
            : List.of("UPDATE_PASSWORD");
    }

    private String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private URI uri(String pathAndQuery) {
        String normalizedPath = pathAndQuery == null || pathAndQuery.startsWith("/")
            ? pathAndQuery
            : "/" + pathAndQuery;
        return URI.create(serverUrl + normalizedPath);
    }

    private String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }

    private String generateFallbackPassword() {
        return "ChangeMe-" + System.currentTimeMillis();
    }
}
