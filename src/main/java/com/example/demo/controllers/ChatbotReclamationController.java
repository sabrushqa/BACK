package com.example.demo.controllers;

import com.example.demo.dto.ReclamationRequest;
import com.example.demo.dto.ReclamationResponse;
import com.example.demo.entities.Reclamation;
import com.example.demo.entities.commercant;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.ReclamationRepository;
import com.example.demo.repositories.TpeRepository;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint interne appelé par le chatbot FastAPI uniquement.
 * Protégé par un token partagé (X-Internal-Token) — pas de JWT Keycloak.
 */
@RestController
@RequestMapping("/api/chatbot/reclamations")
@CrossOrigin(
    originPatterns = {
        "http://localhost:*",
        "http://127.0.0.1:*"
    },
    allowedHeaders = {"Content-Type", "Accept", "X-Internal-Token"},
    allowCredentials = "false"
)
public class ChatbotReclamationController {

    private final ReclamationRepository reclamationRepository;
    private final TpeRepository         tpeRepository;
    private final CommercantRepository  commercantRepository;

    // V-12 : token interne injecté depuis application.properties / env var
    @Value("${app.chatbot.internal-token}")
    private String internalToken;

    public ChatbotReclamationController(
        ReclamationRepository reclamationRepository,
        TpeRepository         tpeRepository,
        CommercantRepository  commercantRepository
    ) {
        this.reclamationRepository = reclamationRepository;
        this.tpeRepository         = tpeRepository;
        this.commercantRepository  = commercantRepository;
    }

    @PostMapping
    public ResponseEntity<?> create(
        @RequestHeader(value = "X-Internal-Token", required = false) String token,
        @RequestBody @Valid ReclamationRequest req
    ) {
        // V-12 : valider le token avant tout traitement
        if (token == null || !isTokenValid(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Token interne manquant ou invalide");
        }

        // Dedup : le portail marchand (CommercantReclamationsPage.tsx::saveTicket)
        // peut aussi persister ce meme ticket (meme referenceChat) des que la
        // reponse du chatbot contenant `ticket` arrive côté client. On enrichit
        // la ligne existante au lieu d'en creer une seconde.
        Reclamation r = reclamationRepository.findByReferenceChat(req.referenceChat())
            .orElseGet(Reclamation::new);
        boolean isNew = r.getIdReclamation() == null;

        if (isNew) {
            r.setDateCreation(LocalDate.now());
            r.setReferenceChat(req.referenceChat());
            r.setStatut("EN_ATTENTE");
        }
        r.setTypeProbleme(normalizeType(req.typeProbleme()));
        r.setDescription(req.description());
        r.setPriorite(normalizePriority(req.priorite()));
        r.setCommentaire(req.commentaire());

        if (req.merchantId() != null && r.getCommercant() == null) {
            commercantRepository.findById(req.merchantId()).ifPresent(r::setCommercant);
        }

        if (req.tpeId() != null && r.getTpe() == null) {
            tpeRepository.findById(req.tpeId()).ifPresent(r::setTpe);
        }
        if (req.tpeReference() != null && r.getTpeReference() == null) {
            r.setTpeReference(req.tpeReference());
        }

        Reclamation saved = reclamationRepository.save(r);

        return ResponseEntity.status(HttpStatus.CREATED).body(
            new ReclamationResponse(
                saved.getIdReclamation(),
                saved.getReferenceChat(),
                saved.getTypeProbleme(),
                saved.getDescription(),
                saved.getStatut(),
                saved.getPriorite(),
                saved.getDateCreation(),
                saved.getDateResolution(),
                saved.getCommentaire(),
                saved.getTpe() != null ? saved.getTpe().getNumeroSerie() : null,
                saved.getTpe() != null ? saved.getTpe().getModele()      : null,
                saved.getTpeReference()
            )
        );
    }

    private boolean isTokenValid(String candidate) {
        return MessageDigest.isEqual(
            candidate.getBytes(StandardCharsets.UTF_8),
            internalToken.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String normalizeType(String type) {
        if (type == null) return "AUTRE";
        return switch (type.toUpperCase()) {
            case "CONNECTIVITY", "CONNECTIVITE", "CONNECTIVITÉ",
                 "NETWORK", "RESEAU", "RÉSEAU" -> "CONNECTIVITE";
            case "TRANSACTION"                  -> "TRANSACTION";
            case "HARDWARE", "MATERIEL", "MATÉRIEL" -> "MATERIEL";
            case "SOFTWARE", "LOGICIEL"         -> "LOGICIEL";
            default                             -> "AUTRE";
        };
    }

    private String normalizePriority(String p) {
        if (p == null) return "MOYENNE";
        return switch (p.toUpperCase()) {
            case "CRITICAL", "CRITIQUE" -> "CRITIQUE";
            case "HIGH", "HAUTE"        -> "HAUTE";
            case "LOW", "BASSE"         -> "BASSE";
            default                     -> "MOYENNE";
        };
    }
}
