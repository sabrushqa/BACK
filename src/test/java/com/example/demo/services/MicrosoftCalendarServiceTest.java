package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.demo.dto.MicrosoftCalendarCallbackRequest;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.interaction_commerciale;
import com.example.demo.entities.microsoft_calendar_connection;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.TypeInteraction;
import com.example.demo.repositories.InteractionCommercialeRepository;
import com.example.demo.repositories.MicrosoftCalendarConnectionRepository;
import com.example.demo.repositories.UtilisateurRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@ExtendWith(MockitoExtension.class)
class MicrosoftCalendarServiceTest {

    private static final String TOKEN_ENDPOINT = "https://login.microsoft.test/common/oauth2/v2.0/token";
    private static final String GRAPH_API = "https://graph.microsoft.test/v1.0";
    private static final String REDIRECT_URI = "http://localhost:4200/commercial/calendrier";
    private static final String ENCRYPTION_KEY =
        "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

    @Mock
    private MicrosoftCalendarConnectionRepository connectionRepository;

    @Mock
    private InteractionCommercialeRepository interactionRepository;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private JwtService jwtService;

    private utilisateur commercialUser;

    @BeforeEach
    void setUpCommercial() {
        commercialUser = new utilisateur();
        commercialUser.setId(7L);
        commercialUser.setRole(RoleUser.COMMERCIAL);
        commercialUser.setEmail("commercial.outlook@test.lanacash.ma");
    }

    @Test
    void completesAuthorizationCodeFlowAndStoresEncryptedRefreshToken() {
        stubAuthenticatedCommercial();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MicrosoftCalendarTokenCipher cipher = new MicrosoftCalendarTokenCipher(ENCRYPTION_KEY);
        AtomicReference<microsoft_calendar_connection> storedConnection = new AtomicReference<>();
        when(connectionRepository.findByUtilisateur_Id(7L))
            .thenAnswer(invocation -> Optional.ofNullable(storedConnection.get()));
        when(connectionRepository.save(any(microsoft_calendar_connection.class)))
            .thenAnswer(invocation -> {
                microsoft_calendar_connection connection = invocation.getArgument(0);
                storedConnection.set(connection);
                return connection;
            });

        MicrosoftCalendarService service = createService(builder, cipher);
        String authorizationUrl = service.beginAuthorization("Bearer token").authorizationUrl();
        String state = UriComponentsBuilder
            .fromUriString(authorizationUrl)
            .build()
            .getQueryParams()
            .getFirst("state");

        assertThat(state).startsWith("microsoft.");
        assertThat(authorizationUrl).contains("Calendars.ReadWrite", "offline_access");

        server.expect(requestTo(TOKEN_ENDPOINT))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("grant_type=authorization_code")))
            .andRespond(withSuccess(
                "{\"access_token\":\"access-1\",\"refresh_token\":\"refresh-1\"}",
                MediaType.APPLICATION_JSON
            ));

        var status = service.completeAuthorization(
            "Bearer token",
            new MicrosoftCalendarCallbackRequest("authorization-code", state)
        );

        assertThat(status.connected()).isTrue();
        assertThat(cipher.decrypt(storedConnection.get().getRefreshTokenEncrypted())).isEqualTo("refresh-1");
        assertThat(storedConnection.get().getOauthStateHash()).isNull();
        server.verify();
    }

    @Test
    void refreshesTokenAndCreatesAllDayOutlookEvent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MicrosoftCalendarTokenCipher cipher = new MicrosoftCalendarTokenCipher(ENCRYPTION_KEY);
        microsoft_calendar_connection connection = new microsoft_calendar_connection();
        connection.setUtilisateur(commercialUser);
        connection.setRefreshTokenEncrypted(cipher.encrypt("refresh-old"));
        when(connectionRepository.findByUtilisateur_Id(7L)).thenReturn(Optional.of(connection));
        when(connectionRepository.save(any(microsoft_calendar_connection.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(interactionRepository.save(any(interaction_commerciale.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        server.expect(requestTo(TOKEN_ENDPOINT))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("grant_type=refresh_token")))
            .andRespond(withSuccess(
                "{\"access_token\":\"access-2\",\"refresh_token\":\"refresh-new\"}",
                MediaType.APPLICATION_JSON
            ));
        server.expect(requestTo(GRAPH_API + "/me/calendar/events"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer access-2"))
            .andExpect(jsonPath("$.subject").value("Visite — Boutique Outlook"))
            .andExpect(jsonPath("$.isAllDay").value(true))
            .andExpect(jsonPath("$.start.dateTime").value("2026-08-20T00:00:00"))
            .andRespond(withSuccess(
                "{\"id\":\"outlook-event-1\",\"webLink\":\"https://outlook.office.com/calendar/item/1\"}",
                MediaType.APPLICATION_JSON
            ));

        commercant merchant = new commercant();
        merchant.setNomCommercial("Boutique Outlook");
        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setIdDossier(12L);
        dossier.setCommercant(merchant);
        interaction_commerciale interaction = new interaction_commerciale();
        interaction.setIdInteraction(33L);
        interaction.setProchaineRelanceType(TypeInteraction.VISITE);

        MicrosoftCalendarService.SyncResult result = createService(builder, cipher)
            .createReminder(commercialUser, interaction, dossier, LocalDate.of(2026, 8, 20));

        assertThat(result.synced()).isTrue();
        assertThat(interaction.getMicrosoftCalendarEventId()).isEqualTo("outlook-event-1");
        assertThat(interaction.getMicrosoftCalendarEventUrl()).contains("outlook.office.com");
        assertThat(cipher.decrypt(connection.getRefreshTokenEncrypted())).isEqualTo("refresh-new");
        server.verify();
    }

    private MicrosoftCalendarService createService(
        RestClient.Builder builder,
        MicrosoftCalendarTokenCipher cipher
    ) {
        return new MicrosoftCalendarService(
            connectionRepository,
            interactionRepository,
            utilisateurRepository,
            jwtService,
            cipher,
            builder,
            "microsoft-client-id",
            "microsoft-client-secret",
            REDIRECT_URI,
            "http://localhost:4200",
            "https://login.microsoft.test/common/oauth2/v2.0/authorize",
            TOKEN_ENDPOINT,
            GRAPH_API
        );
    }

    private void stubAuthenticatedCommercial() {
        when(jwtService.extractBearerToken("Bearer token")).thenReturn(Optional.of("token"));
        when(jwtService.isTokenExpired("token")).thenReturn(false);
        when(jwtService.extractUserId("token")).thenReturn(7L);
        when(utilisateurRepository.findById(7L)).thenReturn(Optional.of(commercialUser));
        when(jwtService.isSessionInvalidated("token", commercialUser)).thenReturn(false);
    }
}
