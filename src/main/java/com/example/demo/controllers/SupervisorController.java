package com.example.demo.controllers;

import com.example.demo.dto.AssignAffiliationRequest;
import com.example.demo.dto.CreateBackOfficeRequest;
import com.example.demo.dto.CreateCommercialeRequest;
import com.example.demo.dto.SupervisorActionResponse;
import com.example.demo.dto.SupervisorOverviewResponse;
import com.example.demo.dto.SupervisorPasswordChangeRequest;
import com.example.demo.dto.SupervisorPdvMapResponse;
import com.example.demo.dto.SupervisorTpeAssignRequest;
import com.example.demo.dto.SupervisorTpeStockResponse;
import com.example.demo.services.SupervisorManagementService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/supervisor")
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
public class SupervisorController {

    private final SupervisorManagementService supervisorManagementService;

    public SupervisorController(SupervisorManagementService supervisorManagementService) {
        this.supervisorManagementService = supervisorManagementService;
    }

    @GetMapping("/overview")
    public ResponseEntity<SupervisorOverviewResponse> getOverview(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(supervisorManagementService.getOverview(authorizationHeader));
    }

    @GetMapping("/pdvs/map")
    public ResponseEntity<SupervisorPdvMapResponse> getPdvMap(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(supervisorManagementService.getPdvMap(authorizationHeader));
    }

    @PostMapping("/pdvs/regeocoder")
    public ResponseEntity<SupervisorActionResponse> regeocoderPdvs(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(supervisorManagementService.regeocoderPdvsExistants(authorizationHeader));
    }

    @PostMapping("/back-offices")
    public ResponseEntity<SupervisorActionResponse> createBackOffice(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestBody CreateBackOfficeRequest request
    ) {
        return ResponseEntity.ok(
            supervisorManagementService.createBackOffice(authorizationHeader, request)
        );
    }

    @PostMapping("/commerciales")
    public ResponseEntity<SupervisorActionResponse> createCommerciale(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestBody CreateCommercialeRequest request
    ) {
        return ResponseEntity.ok(
            supervisorManagementService.createCommerciale(authorizationHeader, request)
        );
    }

    @PostMapping("/change-password")
    public ResponseEntity<SupervisorActionResponse> changePassword(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestBody SupervisorPasswordChangeRequest request
    ) {
        return ResponseEntity.ok(
            supervisorManagementService.changePassword(authorizationHeader, request)
        );
    }

    @PostMapping("/back-offices/{id}/deactivate")
    public ResponseEntity<SupervisorActionResponse> deactivateBackOffice(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable("id") Long backOfficeId
    ) {
        return ResponseEntity.ok(
            supervisorManagementService.deactivateBackOffice(authorizationHeader, backOfficeId)
        );
    }

    @PostMapping("/back-offices/{id}/send-activation")
    public ResponseEntity<SupervisorActionResponse> sendBackOfficeActivation(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable("id") Long backOfficeId
    ) {
        return ResponseEntity.ok(
            supervisorManagementService.sendBackOfficeActivation(authorizationHeader, backOfficeId)
        );
    }

    @PostMapping("/commerciales/{id}/deactivate")
    public ResponseEntity<SupervisorActionResponse> deactivateCommerciale(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable("id") Long commercialeId
    ) {
        return ResponseEntity.ok(
            supervisorManagementService.deactivateCommerciale(authorizationHeader, commercialeId)
        );
    }

    @PostMapping("/commerciales/{id}/send-activation")
    public ResponseEntity<SupervisorActionResponse> sendCommercialeActivation(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable("id") Long commercialeId
    ) {
        return ResponseEntity.ok(
            supervisorManagementService.sendCommercialeActivation(authorizationHeader, commercialeId)
        );
    }

    @PostMapping("/commercants/{id}/deactivate")
    public ResponseEntity<SupervisorActionResponse> deactivateCommercant(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable("id") Long commercantId
    ) {
        return ResponseEntity.ok(
            supervisorManagementService.deactivateCommercant(authorizationHeader, commercantId)
        );
    }

    @PostMapping("/commercants/{id}/send-activation")
    public ResponseEntity<SupervisorActionResponse> sendCommercantActivation(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable("id") Long commercantId
    ) {
        return ResponseEntity.ok(
            supervisorManagementService.sendCommercantActivation(authorizationHeader, commercantId)
        );
    }

    @GetMapping("/tpes")
    public ResponseEntity<SupervisorTpeStockResponse> getTpeStock(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(supervisorManagementService.getTpeStock(authorizationHeader));
    }

    @GetMapping("/tpes/eligible")
    public ResponseEntity<SupervisorTpeStockResponse> getEligibleTpesForDossier(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam("dossierId") Long dossierId
    ) {
        return ResponseEntity.ok(
            supervisorManagementService.getEligibleTpesForDossier(authorizationHeader, dossierId)
        );
    }

    @PostMapping("/tpes/{id}/activate")
    public ResponseEntity<SupervisorActionResponse> activateTpe(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable("id") String tpeId
    ) {
        return ResponseEntity.ok(supervisorManagementService.activateTpe(authorizationHeader, tpeId));
    }

    @PostMapping("/tpes/{id}/deactivate")
    public ResponseEntity<SupervisorActionResponse> deactivateTpe(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable("id") String tpeId
    ) {
        return ResponseEntity.ok(supervisorManagementService.deactivateTpe(authorizationHeader, tpeId));
    }

    @PostMapping("/tpes/{id}/assign-commercant")
    public ResponseEntity<SupervisorActionResponse> assignTpeToCommercant(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable("id") String tpeId,
        @RequestBody SupervisorTpeAssignRequest request
    ) {
        return ResponseEntity.ok(
            supervisorManagementService.assignTpeToCommercant(authorizationHeader, tpeId, request)
        );
    }

    @PostMapping("/affiliations/{id}/assign")
    public ResponseEntity<SupervisorActionResponse> assignAffiliationToCommerciale(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable("id") Long dossierId,
        @RequestBody AssignAffiliationRequest request
    ) {
        return ResponseEntity.ok(
            supervisorManagementService.assignAffiliationToCommerciale(authorizationHeader, dossierId, request)
        );
    }
}
