package com.example.demo.controllers;

import com.example.demo.dto.BackofficeReclamationResponse;
import com.example.demo.dto.ReclamationDashboardResponse;
import com.example.demo.services.ReclamationService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/backoffice/reclamations")
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
public class BackofficeReclamationController {

    private final ReclamationService reclamationService;

    public BackofficeReclamationController(ReclamationService reclamationService) {
        this.reclamationService = reclamationService;
    }

    /** Liste toutes les réclamations, avec filtres optionnels. */
    @GetMapping
    public ResponseEntity<List<BackofficeReclamationResponse>> list(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam(required = false) String statut,
        @RequestParam(required = false) String priorite,
        @RequestParam(required = false) String type
    ) {
        return ResponseEntity.ok(
            reclamationService.getAllReclamations(authorizationHeader, statut, priorite, type)
        );
    }

    /** Statistiques globales pour le dashboard. */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(reclamationService.getStats(authorizationHeader));
    }

    /** Indicateurs graphiques pour le dashboard (par jour / par état, filtrable par période et par type). */
    @GetMapping("/dashboard")
    public ResponseEntity<ReclamationDashboardResponse> dashboard(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam(required = false) Integer days,
        @RequestParam(required = false) String type
    ) {
        return ResponseEntity.ok(reclamationService.getDashboard(authorizationHeader, days, type));
    }

    /** Mettre à jour le statut d'une réclamation (résoudre / escalader). */
    @PatchMapping("/{id}/statut")
    public ResponseEntity<BackofficeReclamationResponse> updateStatut(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable Long id,
        @RequestBody Map<String, String> body
    ) {
        String newStatut = body.get("statut");
        if (newStatut == null || newStatut.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(reclamationService.updateStatut(authorizationHeader, id, newStatut));
    }
}
