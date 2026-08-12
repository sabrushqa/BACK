package com.example.demo.controllers;

import com.example.demo.dto.ReclamationRequest;
import com.example.demo.dto.ReclamationResponse;
import com.example.demo.services.ReclamationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/reclamations")
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
public class ReclamationController {

    private final ReclamationService reclamationService;

    public ReclamationController(ReclamationService reclamationService) {
        this.reclamationService = reclamationService;
    }

    /** Créer une réclamation depuis le chatbot TPE */
    @PostMapping
    public ResponseEntity<ReclamationResponse> create(
        @RequestHeader("Authorization") String authHeader,
        @RequestBody @Valid ReclamationRequest request
    ) {
        ReclamationResponse response = reclamationService.createReclamation(authHeader, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Lister les réclamations du commerçant connecté */
    @GetMapping
    public ResponseEntity<List<ReclamationResponse>> listMine(
        @RequestHeader("Authorization") String authHeader
    ) {
        return ResponseEntity.ok(reclamationService.getMyReclamations(authHeader));
    }
}
