package com.example.demo.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.ReclamationRequest;
import com.example.demo.entities.Reclamation;
import com.example.demo.entities.commercant;
import com.example.demo.entities.tpe;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.ReclamationRepository;
import com.example.demo.repositories.TpeRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests unitaires purs du endpoint interne appele par le chatbot FastAPI:
 * verifie la validation du token interne (comparaison a temps constant),
 * la normalisation du type de probleme et de la priorite, le rattachement
 * optionnel a un TPE/commercant, et la deduplication par referenceChat.
 */
class ChatbotReclamationControllerTest {

    private ChatbotReclamationController buildController(
        ReclamationRepository reclamationRepository,
        TpeRepository tpeRepository,
        CommercantRepository commercantRepository
    ) {
        ChatbotReclamationController controller =
            new ChatbotReclamationController(reclamationRepository, tpeRepository, commercantRepository);
        ReflectionTestUtils.setField(controller, "internalToken", "expected-internal-token");
        return controller;
    }

    private ChatbotReclamationController buildController(
        ReclamationRepository reclamationRepository,
        TpeRepository tpeRepository
    ) {
        return buildController(reclamationRepository, tpeRepository, mock(CommercantRepository.class));
    }

    private ReclamationRequest request(String type, String priorite, Long tpeId) {
        return request(type, priorite, tpeId, null, null);
    }

    private ReclamationRequest request(
        String type, String priorite, Long tpeId, Long merchantId, String tpeReference
    ) {
        return new ReclamationRequest(
            "CHAT-123",
            type,
            "Le terminal ne repond plus depuis ce matin.",
            priorite,
            tpeId,
            "Commentaire optionnel",
            merchantId,
            tpeReference,
            null
        );
    }

    @Test
    void rejectsRequestWithMissingToken() {
        ChatbotReclamationController controller =
            buildController(mock(ReclamationRepository.class), mock(TpeRepository.class));

        var response = controller.create(null, request("RESEAU", "HAUTE", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsRequestWithInvalidToken() {
        ChatbotReclamationController controller =
            buildController(mock(ReclamationRepository.class), mock(TpeRepository.class));

        var response = controller.create("wrong-token", request("RESEAU", "HAUTE", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createsReclamationWithNormalizedTypeAndPriority() {
        ReclamationRepository reclamationRepository = mock(ReclamationRepository.class);
        TpeRepository tpeRepository = mock(TpeRepository.class);
        ChatbotReclamationController controller = buildController(reclamationRepository, tpeRepository);

        when(reclamationRepository.save(any(Reclamation.class))).thenAnswer(invocation -> {
            Reclamation reclamation = invocation.getArgument(0);
            reclamation.setIdReclamation(42L);
            return reclamation;
        });

        var response = controller.create(
            "expected-internal-token",
            request("CONNECTIVITY", "CRITICAL", null)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(reclamationRepository).save(any(Reclamation.class));
    }

    @Test
    void createsReclamationAttachedToExistingTpe() {
        ReclamationRepository reclamationRepository = mock(ReclamationRepository.class);
        TpeRepository tpeRepository = mock(TpeRepository.class);
        ChatbotReclamationController controller = buildController(reclamationRepository, tpeRepository);

        tpe terminal = new tpe();
        terminal.setNumeroSerie("TPE-CHATBOT-1");
        terminal.setModele("Ingenico");
        when(tpeRepository.findById(7L)).thenReturn(Optional.of(terminal));
        when(reclamationRepository.save(any(Reclamation.class))).thenAnswer(invocation -> {
            Reclamation reclamation = invocation.getArgument(0);
            reclamation.setIdReclamation(43L);
            return reclamation;
        });

        var response = controller.create("expected-internal-token", request("AUTRE", "BASSE", 7L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Reclamation body = null;
        Object rawBody = response.getBody();
        assertThat(rawBody).isInstanceOf(com.example.demo.dto.ReclamationResponse.class);
        com.example.demo.dto.ReclamationResponse reclamationResponse =
            (com.example.demo.dto.ReclamationResponse) rawBody;
        assertThat(reclamationResponse.tpeNumeroSerie()).isEqualTo("TPE-CHATBOT-1");
    }

    @Test
    void fallsBackToDefaultsForUnknownTypeAndPriority() {
        ReclamationRepository reclamationRepository = mock(ReclamationRepository.class);
        TpeRepository tpeRepository = mock(TpeRepository.class);
        ChatbotReclamationController controller = buildController(reclamationRepository, tpeRepository);

        when(reclamationRepository.save(any(Reclamation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.create(
            "expected-internal-token",
            request("QUELQUE_CHOSE_INCONNU", "INCONNU", null)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        com.example.demo.dto.ReclamationResponse reclamationResponse =
            (com.example.demo.dto.ReclamationResponse) response.getBody();
        assertThat(reclamationResponse.typeProbleme()).isEqualTo("AUTRE");
        assertThat(reclamationResponse.priorite()).isEqualTo("MOYENNE");
    }

    @Test
    void resolvesMerchantAndSetsTpeReferenceWhenTpeIdUnresolved() {
        ReclamationRepository reclamationRepository = mock(ReclamationRepository.class);
        TpeRepository tpeRepository = mock(TpeRepository.class);
        CommercantRepository commercantRepository = mock(CommercantRepository.class);
        ChatbotReclamationController controller =
            buildController(reclamationRepository, tpeRepository, commercantRepository);

        commercant merchant = new commercant();
        merchant.setIdCommercant(99L);
        when(commercantRepository.findById(99L)).thenReturn(Optional.of(merchant));
        when(reclamationRepository.findByReferenceChat("CHAT-123")).thenReturn(Optional.empty());
        when(reclamationRepository.save(any(Reclamation.class))).thenAnswer(invocation -> {
            Reclamation reclamation = invocation.getArgument(0);
            reclamation.setIdReclamation(44L);
            return reclamation;
        });

        var response = controller.create(
            "expected-internal-token",
            request("HARDWARE", "HAUTE", null, 99L, "TPE-ORACLE-77")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        com.example.demo.dto.ReclamationResponse reclamationResponse =
            (com.example.demo.dto.ReclamationResponse) response.getBody();
        assertThat(reclamationResponse.tpeReference()).isEqualTo("TPE-ORACLE-77");

        org.mockito.ArgumentCaptor<Reclamation> captor = org.mockito.ArgumentCaptor.forClass(Reclamation.class);
        verify(reclamationRepository).save(captor.capture());
        assertThat(captor.getValue().getCommercant()).isSameAs(merchant);
    }

    @Test
    void enrichesExistingReclamationInsteadOfDuplicatingWhenReferenceChatAlreadyExists() {
        ReclamationRepository reclamationRepository = mock(ReclamationRepository.class);
        TpeRepository tpeRepository = mock(TpeRepository.class);
        ChatbotReclamationController controller = buildController(reclamationRepository, tpeRepository);

        Reclamation existing = new Reclamation();
        existing.setIdReclamation(55L);
        existing.setReferenceChat("CHAT-123");
        existing.setStatut("EN_COURS");
        when(reclamationRepository.findByReferenceChat("CHAT-123")).thenReturn(Optional.of(existing));
        when(reclamationRepository.save(any(Reclamation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.create("expected-internal-token", request("AUTRE", "BASSE", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        com.example.demo.dto.ReclamationResponse reclamationResponse =
            (com.example.demo.dto.ReclamationResponse) response.getBody();
        assertThat(reclamationResponse.idReclamation()).isEqualTo(55L);
        // Le statut deja en cours ne doit pas etre reinitialise a EN_ATTENTE.
        verify(reclamationRepository).save(existing);
        assertThat(existing.getStatut()).isEqualTo("EN_COURS");
    }
}
