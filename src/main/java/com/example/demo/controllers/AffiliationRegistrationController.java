package com.example.demo.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.dto.AffiliationDocumentValidationResponse;
import com.example.demo.dto.AffiliationRegistrationRequest;
import com.example.demo.dto.AffiliationRegistrationResponse;
import com.example.demo.services.AffiliationDocumentValidationService;
import com.example.demo.services.AffiliationRegistrationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/affiliations")
@CrossOrigin(
    originPatterns = {
        "http://localhost:*",
        "http://127.0.0.1:*",
        "https://localhost:*",
        "https://127.0.0.1:*"
    }
)
public class AffiliationRegistrationController {

    private final AffiliationRegistrationService affiliationRegistrationService;
    private final AffiliationDocumentValidationService affiliationDocumentValidationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AffiliationRegistrationController(
        AffiliationRegistrationService affiliationRegistrationService,
        AffiliationDocumentValidationService affiliationDocumentValidationService
    ) {
        this.affiliationRegistrationService = affiliationRegistrationService;
        this.affiliationDocumentValidationService = affiliationDocumentValidationService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AffiliationRegistrationResponse> submitAffiliation(
        @ModelAttribute AffiliationRegistrationRequest request,
        MultipartHttpServletRequest multipartRequest
    ) {
        Map<String, MultipartFile> uploadedDocuments = new LinkedHashMap<>(multipartRequest.getFileMap());
        AffiliationRegistrationRequest resolvedRequest = resolveRequestPayload(request, multipartRequest);

        AffiliationRegistrationResponse response = affiliationRegistrationService.submit(
            resolvedRequest,
            uploadedDocuments
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<AffiliationRegistrationResponse> submitAffiliationForm(
        @ModelAttribute AffiliationRegistrationRequest request
    ) {
        AffiliationRegistrationResponse response = affiliationRegistrationService.submit(
            request,
            Map.of()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/documents/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AffiliationDocumentValidationResponse> validateDocument(
        @RequestParam("documentKey") String documentKey,
        @RequestParam("file") MultipartFile file
    ) {
        AffiliationDocumentValidationResponse response =
            affiliationDocumentValidationService.validateDocument(documentKey, file);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleStorageError(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("message", exception.getMessage()));
    }

    private AffiliationRegistrationRequest resolveRequestPayload(
        AffiliationRegistrationRequest fallbackRequest,
        MultipartHttpServletRequest multipartRequest
    ) {
        String payload = multipartRequest.getParameter("payload");

        if (!StringUtils.hasText(payload)) {
            return fallbackRequest;
        }

        try {
            return objectMapper.readValue(payload, AffiliationRegistrationRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Le contenu du formulaire d'affiliation est invalide.");
        }
    }
}
