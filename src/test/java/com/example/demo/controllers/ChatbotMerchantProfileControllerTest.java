package com.example.demo.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.dto.ChatbotMerchantProfileResponse;
import com.example.demo.services.MerchantAccessService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests unitaires purs du endpoint interne appele par le chatbot FastAPI
 * pour resoudre le profil d'un commerçant (type d'affiliation reel, PDV,
 * TPE) avant de traiter un message : verifie la validation du token interne
 * (comparaison a temps constant, comme ChatbotReclamationController) et les
 * cas 400/404/200.
 */
class ChatbotMerchantProfileControllerTest {

    private ChatbotMerchantProfileController buildController(MerchantAccessService merchantAccessService) {
        ChatbotMerchantProfileController controller = new ChatbotMerchantProfileController(merchantAccessService);
        ReflectionTestUtils.setField(controller, "internalToken", "expected-internal-token");
        return controller;
    }

    @Test
    void rejectsRequestWithMissingToken() {
        ChatbotMerchantProfileController controller = buildController(mock(MerchantAccessService.class));

        var response = controller.profile(null, "42", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsRequestWithWrongToken() {
        ChatbotMerchantProfileController controller = buildController(mock(MerchantAccessService.class));

        var response = controller.profile("wrong-token", "42", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsNonNumericMerchantId() {
        ChatbotMerchantProfileController controller = buildController(mock(MerchantAccessService.class));

        var response = controller.profile("expected-internal-token", "not-a-number", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void returnsNotFoundWhenMerchantUnknown() {
        MerchantAccessService merchantAccessService = mock(MerchantAccessService.class);
        when(merchantAccessService.getMerchantProfileForChatbot(999L, null)).thenReturn(null);
        ChatbotMerchantProfileController controller = buildController(merchantAccessService);

        var response = controller.profile("expected-internal-token", "999", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void returnsProfileWithCorrectToken() {
        MerchantAccessService merchantAccessService = mock(MerchantAccessService.class);
        ChatbotMerchantProfileResponse profile = new ChatbotMerchantProfileResponse(
            42L, "TPE", true, false,
            List.of(new ChatbotMerchantProfileResponse.PdvItem(1L, "PDV Test", "Casablanca", "12 rue Test")),
            List.of(new ChatbotMerchantProfileResponse.TpeItem("TPE-000001", "Ingenico", 1L, "PDV Test"))
        );
        when(merchantAccessService.getMerchantProfileForChatbot(42L, null)).thenReturn(profile);
        ChatbotMerchantProfileController controller = buildController(merchantAccessService);

        var response = controller.profile("expected-internal-token", "42", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(profile);
    }

    @Test
    void scopesProfileToPdvIdWhenProvided() {
        MerchantAccessService merchantAccessService = mock(MerchantAccessService.class);
        ChatbotMerchantProfileResponse profile = new ChatbotMerchantProfileResponse(
            42L, "TPE", true, false,
            List.of(new ChatbotMerchantProfileResponse.PdvItem(7L, "PDV du sous-commerçant", "Rabat", "3 rue Test")),
            List.of(new ChatbotMerchantProfileResponse.TpeItem("TPE-000002", "Ingenico", 7L, "PDV du sous-commerçant"))
        );
        when(merchantAccessService.getMerchantProfileForChatbot(42L, 7L)).thenReturn(profile);
        ChatbotMerchantProfileController controller = buildController(merchantAccessService);

        var response = controller.profile("expected-internal-token", "42", 7L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(profile);
    }
}
