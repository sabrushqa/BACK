package com.example.demo.controllers;

import com.example.demo.dto.ChatbotMerchantProfileResponse;
import com.example.demo.services.MerchantAccessService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint interne appelé par le chatbot FastAPI (v2, agent/tools/merchant_profile_tool.py)
 * pour savoir avec QUI il parle avant de traiter un message : type d'affiliation
 * réel (TPE / SOFTPOS / QR_CODE / E_COMMERCE / ENCAISSEMENT_ET_ECOMMERCE), ses
 * PDV et ses TPE réellement affectés. Sans ça, l'agent ne peut pas distinguer
 * un commerçant e-commerce pur (qui n'a ni PDV ni TPE au sens encaissement
 * physique) d'un commerçant TPE qui signale une vraie panne matérielle.
 *
 * Protégé par le même token interne que ChatbotReclamationController
 * (X-Internal-Token) — pas de JWT Keycloak, le chatbot n'en a pas.
 */
@RestController
@RequestMapping("/api/chatbot/merchants")
@CrossOrigin(
    originPatterns = {
        "http://localhost:*",
        "http://127.0.0.1:*"
    },
    allowedHeaders = {"Content-Type", "Accept", "X-Internal-Token"},
    allowCredentials = "false"
)
public class ChatbotMerchantProfileController {

    private final MerchantAccessService merchantAccessService;

    @Value("${app.chatbot.internal-token}")
    private String internalToken;

    public ChatbotMerchantProfileController(MerchantAccessService merchantAccessService) {
        this.merchantAccessService = merchantAccessService;
    }

    @GetMapping("/{merchantId}/profile")
    public ResponseEntity<?> profile(
        @RequestHeader(value = "X-Internal-Token", required = false) String token,
        @PathVariable String merchantId,
        // Present uniquement quand l'appelant reel est un sous-commerçant
        // (voir ChatbotProxyController::resolveMerchantPdvId) : restreint le
        // profil renvoye au seul PDV de ce sous-commerçant, plutot qu'a tout
        // le commerçant parent designe par merchantId.
        @RequestParam(value = "pdvId", required = false) Long pdvId
    ) {
        if (token == null || !isTokenValid(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Token interne manquant ou invalide");
        }

        Long commercantId;
        try {
            commercantId = Long.valueOf(merchantId);
        } catch (NumberFormatException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("merchantId invalide");
        }

        ChatbotMerchantProfileResponse profile = merchantAccessService.getMerchantProfileForChatbot(commercantId, pdvId);
        if (profile == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Commerçant introuvable");
        }

        return ResponseEntity.ok(profile);
    }

    private boolean isTokenValid(String candidate) {
        return MessageDigest.isEqual(
            candidate.getBytes(StandardCharsets.UTF_8),
            internalToken.getBytes(StandardCharsets.UTF_8)
        );
    }
}
