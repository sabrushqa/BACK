package com.example.demo.controllers;

import com.example.demo.dto.ActivationAccountRequest;
import com.example.demo.dto.MerchantSessionResponse;
import com.example.demo.dto.PasswordResetChallengeResponse;
import com.example.demo.dto.PasswordResetRequest;
import com.example.demo.services.MerchantAccessService;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(
    originPatterns = {
        "http://localhost:*",
        "http://127.0.0.1:*",
        "https://localhost:*",
        "https://127.0.0.1:*"
    },
    allowedHeaders = {"Authorization", "Content-Type", "Accept"},
    allowCredentials = "true"
)
public class AuthController {

    private final MerchantAccessService merchantAccessService;

    public AuthController(MerchantAccessService merchantAccessService) {
        this.merchantAccessService = merchantAccessService;
    }

    @PostMapping("/activate")
    public ResponseEntity<MerchantSessionResponse> activate(@RequestBody ActivationAccountRequest request) {
        return ResponseEntity.ok(merchantAccessService.activateAccount(request));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<PasswordResetChallengeResponse> requestPasswordReset(
        @RequestBody PasswordResetRequest request
    ) {
        return ResponseEntity.ok(merchantAccessService.requestPasswordReset(request));
    }

    @GetMapping("/me")
    public ResponseEntity<MerchantSessionResponse> currentSession(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(merchantAccessService.currentSession(authorizationHeader));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok(Map.of(
            "message",
            "Déconnexion locale effectuée. La session Keycloak doit être fermée côté client."
        ));
    }
}
