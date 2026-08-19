package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.dto.MerchantPdvProductRequest;
import com.example.demo.dto.MerchantSubMerchantCreateRequest;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exerce la demande de nouveau point de vente (chemin heureux complet) et la
 * creation de sous-commercant (validations, puis echec attendu au niveau
 * Keycloak qui est desactive en test).
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class MerchantWorkspaceCreationTest {

    @Autowired
    private MerchantWorkspaceManagementService merchantWorkspaceManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private PdvRepository pdvRepository;

    private utilisateur persistUser(String email, RoleUser role) {
        utilisateur user = new utilisateur();
        user.setEmail(email);
        user.setRole(role);
        user.setActive(true);
        user.setDateCreation(LocalDate.now());
        return utilisateurRepository.save(user);
    }

    private String tokenFor(utilisateur user) {
        return TestJwtSupport.mintKeycloakToken("kc-sub-" + user.getId(), user.getEmail(), 300);
    }

    @Test
    void requestsNewPdvProductForAcceptedTpeMerchant() {
        utilisateur merchantUser = persistUser("commercant.newpdv@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation acceptedDossier = new dossier_affiliation();
        acceptedDossier.setCommercant(commercant);
        acceptedDossier.setStatus(StatusDossier.ACCEPTE);
        acceptedDossier.setTypeAffiliation(TypeAffiliation.TPE);
        acceptedDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(acceptedDossier);

        var response = merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(merchantUser),
            new MerchantPdvProductRequest(
                "Nouveau PDV", "12 rue Test", "Casablanca", null, null, "0600000000", null,
                "TPE", "1", "STANDARD", "GPRS", "ACHAT", null, null, null, null, null,
                33.5731, -7.5898,
                null
            )
        );

        assertThat(response.message()).isNotBlank();
    }

    @Test
    void requestsNewEcommerceChannelForEcommerceMerchant() {
        utilisateur merchantUser = persistUser("commercant.newpdv.ecommerce@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation acceptedDossier = new dossier_affiliation();
        acceptedDossier.setCommercant(commercant);
        acceptedDossier.setStatus(StatusDossier.ACCEPTE);
        acceptedDossier.setTypeAffiliation(TypeAffiliation.E_COMMERCE);
        acceptedDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(acceptedDossier);

        var response = merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(merchantUser),
            new MerchantPdvProductRequest(
                null, null, null, null, null, null, null,
                "E_COMMERCE", null, null, null, null, null, null,
                "INTEGRATION_API", "https://nouvelle-boutique.example.ma", null,
                null, null,
                null
            )
        );

        assertThat(response.message()).isNotBlank();
    }

    @Test
    void addsMoreTpeOnAnExistingPdvWithoutCreatingANewOne() {
        // Le commercant a deja un point de vente et veut simplement AJOUTER
        // des TPE dessus (ex: caisse supplementaire), pas ouvrir une nouvelle
        // boutique — aucune info d'adresse/nom a redemander.
        utilisateur merchantUser = persistUser("commercant.existingpdv@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation acceptedDossier = new dossier_affiliation();
        acceptedDossier.setCommercant(commercant);
        acceptedDossier.setStatus(StatusDossier.ACCEPTE);
        acceptedDossier.setTypeAffiliation(TypeAffiliation.TPE);
        acceptedDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(acceptedDossier);

        pdv pointVenteExistant = new pdv();
        pointVenteExistant.setNomPDV("Boutique existante");
        pointVenteExistant.setCommercant(commercant);
        pointVenteExistant.setStatut("ACTIF");
        pointVenteExistant = pdvRepository.save(pointVenteExistant);
        long nombrePdvAvant = pdvRepository.count();

        var response = merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(merchantUser),
            new MerchantPdvProductRequest(
                null, null, null, null, null, null, null,
                "TPE", "2", "STANDARD", "GPRS", "ACHAT", null, null, null, null, null,
                null, null,
                pointVenteExistant.getIdPDV()
            )
        );

        assertThat(response.message()).isNotBlank();
        // Aucun nouveau PDV cree : le compte doit rester identique.
        assertThat(pdvRepository.count()).isEqualTo(nombrePdvAvant);

        dossier_affiliation extensionDossier = dossierAffiliationRepository
            .findAllByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(commercant.getIdCommercant())
            .stream()
            .filter(d -> "NOUVEAU_PDV".equals(d.getOrigineCreation()))
            .findFirst()
            .orElseThrow();
        assertThat(extensionDossier.getRequestedPdv().getIdPDV()).isEqualTo(pointVenteExistant.getIdPDV());
        assertThat(extensionDossier.getRequestedPdvDejaExistant())
            .as("Doit etre marque comme reutilisant un PDV existant, pour que l'UI n'affiche jamais \"Nouveau point de vente\"")
            .isTrue();
    }

    @Test
    void marksTheDossierAsNotUsingAnExistingPdvWhenANewOneIsCreated() {
        utilisateur merchantUser = persistUser("commercant.newpdv.flag@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation acceptedDossier = new dossier_affiliation();
        acceptedDossier.setCommercant(commercant);
        acceptedDossier.setStatus(StatusDossier.ACCEPTE);
        acceptedDossier.setTypeAffiliation(TypeAffiliation.TPE);
        acceptedDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(acceptedDossier);

        merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(merchantUser),
            new MerchantPdvProductRequest(
                "Nouveau PDV Flag", "12 rue Test", "Casablanca", null, null, "0600000000", null,
                "TPE", "1", "STANDARD", "GPRS", "ACHAT", null, null, null, null, null,
                33.5731, -7.5898,
                null
            )
        );

        dossier_affiliation extensionDossier = dossierAffiliationRepository
            .findAllByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(commercant.getIdCommercant())
            .stream()
            .filter(d -> "NOUVEAU_PDV".equals(d.getOrigineCreation()))
            .findFirst()
            .orElseThrow();
        assertThat(extensionDossier.getRequestedPdvDejaExistant()).isFalse();
    }

    @Test
    void rejectsExistingPdvThatBelongsToAnotherCommercant() {
        utilisateur merchantUser = persistUser("commercant.existingpdv.autre@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation acceptedDossier = new dossier_affiliation();
        acceptedDossier.setCommercant(commercant);
        acceptedDossier.setStatus(StatusDossier.ACCEPTE);
        acceptedDossier.setTypeAffiliation(TypeAffiliation.TPE);
        acceptedDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(acceptedDossier);

        utilisateur autreMerchantUser = persistUser("commercant.autre.pdv@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant autreCommercant = new commercant();
        autreCommercant.setUtilisateur(autreMerchantUser);
        autreCommercant = commercantRepository.save(autreCommercant);

        pdv pointVenteAutrui = new pdv();
        pointVenteAutrui.setNomPDV("Boutique d'un autre");
        pointVenteAutrui.setCommercant(autreCommercant);
        pointVenteAutrui.setStatut("ACTIF");
        pointVenteAutrui = pdvRepository.save(pointVenteAutrui);
        final Long idPdvAutrui = pointVenteAutrui.getIdPDV();

        assertThatThrownBy(() ->
            merchantWorkspaceManagementService.requestNewPdvProduct(
                "Bearer " + tokenFor(merchantUser),
                new MerchantPdvProductRequest(
                    null, null, null, null, null, null, null,
                    "TPE", "1", "STANDARD", "GPRS", "ACHAT", null, null, null, null, null,
                    null, null,
                    idPdvAutrui
                )
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsEcommerceExtensionRequestForTpeMerchant() {
        // Un commercant deja affilie TPE doit pouvoir ajouter le canal
        // e-commerce en extension — avant ce correctif, isCompatibleAugmentationType
        // le refusait a tort (seul un commercant deja E_COMMERCE pouvait
        // demander E_COMMERCE), ce qui empechait tout dossier "TPE + e-commerce"
        // de se constituer via deux demandes distinctes.
        utilisateur merchantUser = persistUser("commercant.newpdv.tpe-then-ecom@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation acceptedDossier = new dossier_affiliation();
        acceptedDossier.setCommercant(commercant);
        acceptedDossier.setStatus(StatusDossier.ACCEPTE);
        acceptedDossier.setTypeAffiliation(TypeAffiliation.TPE);
        acceptedDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(acceptedDossier);

        var response = merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(merchantUser),
            new MerchantPdvProductRequest(
                null, null, null, null, null, null, null,
                "E_COMMERCE", null, null, null, null, null, null,
                "INTEGRATION_API", "https://nouvelle-boutique.example.ma", null,
                null, null,
                null
            )
        );

        assertThat(response.message()).isNotBlank();
    }

    @Test
    void allowsQrCodeExtensionRequestForEcommerceMerchant() {
        // Symetriquement, un commercant deja e-commerce doit pouvoir demander
        // un canal physique (TPE/SoftPOS/QR Code) — QR_CODE etait meme absent
        // de la liste des types autorises pour TOUT commercant avant ce
        // correctif, quelle que soit son affiliation actuelle.
        utilisateur merchantUser = persistUser("commercant.newpdv.ecom-then-qr@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation acceptedDossier = new dossier_affiliation();
        acceptedDossier.setCommercant(commercant);
        acceptedDossier.setStatus(StatusDossier.ACCEPTE);
        acceptedDossier.setTypeAffiliation(TypeAffiliation.E_COMMERCE);
        acceptedDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(acceptedDossier);

        var response = merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(merchantUser),
            new MerchantPdvProductRequest(
                "Nouveau PDV QR", "12 rue Test", "Casablanca", null, null, "0600000003", null,
                "QR_CODE", "1", "STANDARD", "GPRS", "ACHAT", null, null, null, null, null,
                33.5731, -7.5898,
                null
            )
        );

        assertThat(response.message()).isNotBlank();
    }

    @Test
    void rejectsCombinedTypeAsExtensionRequest() {
        // Seule restriction restante : on ajoute UN canal a la fois via une
        // extension, jamais directement le type combine ENCAISSEMENT_ET_ECOMMERCE.
        utilisateur merchantUser = persistUser("commercant.newpdv.combined-rejected@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation acceptedDossier = new dossier_affiliation();
        acceptedDossier.setCommercant(commercant);
        acceptedDossier.setStatus(StatusDossier.ACCEPTE);
        acceptedDossier.setTypeAffiliation(TypeAffiliation.TPE);
        acceptedDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(acceptedDossier);

        assertThatThrownBy(() ->
            merchantWorkspaceManagementService.requestNewPdvProduct(
                "Bearer " + tokenFor(merchantUser),
                new MerchantPdvProductRequest(
                    null, null, null, null, null, null, null,
                    "ENCAISSEMENT_ET_ECOMMERCE", null, null, null, null, null, null,
                    "INTEGRATION_API", "https://nouvelle-boutique.example.ma", null,
                    null, null,
                null
            )
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPdvProductRequestWhenNoAcceptedDossier() {
        utilisateur merchantUser = persistUser("commercant.newpdv.noaccepted@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercantRepository.save(commercant);

        assertThatThrownBy(() ->
            merchantWorkspaceManagementService.requestNewPdvProduct(
                "Bearer " + tokenFor(merchantUser),
                new MerchantPdvProductRequest(
                    "Nouveau PDV", "12 rue Test", "Casablanca", null, null, "0600000000", null,
                    "TPE", "1", "STANDARD", "GPRS", "ACHAT", null, null, null, null, null,
                    33.5731, -7.5898,
                null
            )
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createSubMerchantValidatesThenFailsGracefullyOnKeycloakStep() {
        utilisateur merchantUser = persistUser("commercant.subcreate@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation acceptedDossier = new dossier_affiliation();
        acceptedDossier.setCommercant(commercant);
        acceptedDossier.setStatus(StatusDossier.ACCEPTE);
        acceptedDossier.setTypeAffiliation(TypeAffiliation.TPE);
        acceptedDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(acceptedDossier);

        pdv pointVente = new pdv();
        pointVente.setCommercant(commercant);
        pointVente.setStatut("ACTIF");
        pointVente = pdvRepository.save(pointVente);
        final Long pdvId = pointVente.getIdPDV();

        assertThatThrownBy(() ->
            merchantWorkspaceManagementService.createSubMerchant(
                "Bearer " + tokenFor(merchantUser),
                new MerchantSubMerchantCreateRequest(
                    pdvId, null, "Bennani", "Omar", "sous.commercant.subcreate@test.lanacash.ma", "0600000002"
                )
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void rejectsSubMerchantCreationWithSameEmailAsMerchant() {
        utilisateur merchantUser = persistUser("commercant.subsameemail@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        pdv pointVente = new pdv();
        pointVente.setCommercant(commercant);
        pointVente.setStatut("ACTIF");
        pointVente = pdvRepository.save(pointVente);
        final Long pdvId = pointVente.getIdPDV();

        assertThatThrownBy(() ->
            merchantWorkspaceManagementService.createSubMerchant(
                "Bearer " + tokenFor(merchantUser),
                new MerchantSubMerchantCreateRequest(
                    pdvId, null, "Bennani", "Omar", "commercant.subsameemail@test.lanacash.ma", "0600000002"
                )
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
