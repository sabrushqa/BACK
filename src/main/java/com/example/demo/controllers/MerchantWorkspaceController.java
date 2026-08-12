package com.example.demo.controllers;

import com.example.demo.dto.MerchantSubMerchantCreateRequest;
import com.example.demo.dto.MerchantSubMerchantCreateResponse;
import com.example.demo.dto.MerchantSubMerchantMoveRequest;
import com.example.demo.dto.MerchantSubMerchantMoveResponse;
import com.example.demo.dto.MerchantSubMerchantStatusResponse;
import com.example.demo.dto.MerchantPdvProductRequest;
import com.example.demo.dto.MerchantTpePdvAssignmentRequest;
import com.example.demo.dto.MerchantTpePdvAssignmentResponse;
import com.example.demo.dto.SupervisorActionResponse;
import com.example.demo.services.MerchantWorkspaceManagementService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/commercant/workspace", "/api/merchant/workspace"})
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
public class MerchantWorkspaceController {

    private final MerchantWorkspaceManagementService merchantWorkspaceManagementService;

    public MerchantWorkspaceController(
        MerchantWorkspaceManagementService merchantWorkspaceManagementService
    ) {
        this.merchantWorkspaceManagementService = merchantWorkspaceManagementService;
    }

    @PostMapping("/sub-merchants")
    public ResponseEntity<MerchantSubMerchantCreateResponse> createSubMerchant(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestBody MerchantSubMerchantCreateRequest request
    ) {
        return ResponseEntity.ok(
            merchantWorkspaceManagementService.createSubMerchant(authorizationHeader, request)
        );
    }

    @PostMapping("/pdvs/product-requests")
    public ResponseEntity<SupervisorActionResponse> requestNewPdvProduct(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestBody MerchantPdvProductRequest request
    ) {
        return ResponseEntity.ok(
            merchantWorkspaceManagementService.requestNewPdvProduct(authorizationHeader, request)
        );
    }

    @PostMapping("/tpes/{tpeId}/pdv")
    public ResponseEntity<MerchantTpePdvAssignmentResponse> assignTpeToPdv(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable String tpeId,
        @RequestBody MerchantTpePdvAssignmentRequest request
    ) {
        return ResponseEntity.ok(
            merchantWorkspaceManagementService.assignTpeToPdv(authorizationHeader, tpeId, request)
        );
    }

    @PostMapping("/sub-merchants/{subMerchantId}/activate")
    public ResponseEntity<MerchantSubMerchantStatusResponse> activateSubMerchant(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable Long subMerchantId
    ) {
        return ResponseEntity.ok(
            merchantWorkspaceManagementService.activateSubMerchant(authorizationHeader, subMerchantId)
        );
    }

    @PostMapping("/sub-merchants/{subMerchantId}/deactivate")
    public ResponseEntity<MerchantSubMerchantStatusResponse> deactivateSubMerchant(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable Long subMerchantId
    ) {
        return ResponseEntity.ok(
            merchantWorkspaceManagementService.deactivateSubMerchant(authorizationHeader, subMerchantId)
        );
    }

    @PostMapping("/sub-merchants/{subMerchantId}/pdv")
    public ResponseEntity<MerchantSubMerchantMoveResponse> moveSubMerchantToPdv(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable Long subMerchantId,
        @RequestBody MerchantSubMerchantMoveRequest request
    ) {
        return ResponseEntity.ok(
            merchantWorkspaceManagementService.moveSubMerchantToPdv(authorizationHeader, subMerchantId, request)
        );
    }
}
