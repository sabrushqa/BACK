package com.example.demo.controllers;

import com.example.demo.dto.ReclamationRequest;
import com.example.demo.dto.ReclamationResponse;
import com.example.demo.services.ReclamationPdfService;
import com.example.demo.services.ReclamationService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /**
     * Fiche PDF imprimable d'une réclamation du commerçant connecté (403 si
     * elle appartient à un autre commerçant — voir
     * ReclamationService::genererPdfPourCommercant).
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<ByteArrayResource> pdf(
        @RequestHeader("Authorization") String authHeader,
        @PathVariable Long id
    ) {
        ReclamationPdfService.Pdf pdf = reclamationService.genererPdfPourCommercant(authHeader, id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(pdf.nomFichier(), StandardCharsets.UTF_8).build().toString()
            )
            .body(new ByteArrayResource(pdf.contenu()));
    }
}
