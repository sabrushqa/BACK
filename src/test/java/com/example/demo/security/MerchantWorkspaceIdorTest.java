package com.example.demo.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.dto.MerchantTpePdvAssignmentRequest;
import com.example.demo.entities.commercant;
import com.example.demo.entities.pdv;
import com.example.demo.entities.sous_commercant;
import com.example.demo.entities.tpe;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.SousCommercantRepository;
import com.example.demo.repositories.TpeRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.services.MerchantWorkspaceManagementService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prouve qu'un commercant ne peut pas affecter un TPE d'un autre commercant
 * a l'un de ses points de vente, ni activer/desactiver le sous-commercant
 * d'un autre commercant, simplement en devinant l'ID dans la requete.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class MerchantWorkspaceIdorTest {

    @Autowired
    private MerchantWorkspaceManagementService merchantWorkspaceManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private PdvRepository pdvRepository;

    @Autowired
    private TpeRepository tpeRepository;

    @Autowired
    private SousCommercantRepository sousCommercantRepository;

    private utilisateur commercantAUser;
    private utilisateur commercantBUser;
    private pdv pdvA;
    private tpe tpeB;
    private sous_commercant sousCommercantB;

    @BeforeEach
    void setUp() {
        commercantAUser = persistUser("workspace.idor.a@test.lanacash.ma");
        commercant commercantA = new commercant();
        commercantA.setUtilisateur(commercantAUser);
        commercantA = commercantRepository.save(commercantA);

        commercantBUser = persistUser("workspace.idor.b@test.lanacash.ma");
        commercant commercantB = new commercant();
        commercantB.setUtilisateur(commercantBUser);
        commercantB = commercantRepository.save(commercantB);

        pdvA = new pdv();
        pdvA.setCommercant(commercantA);
        pdvA = pdvRepository.save(pdvA);

        pdv pdvB = new pdv();
        pdvB.setCommercant(commercantB);
        pdvB = pdvRepository.save(pdvB);

        tpeB = new tpe();
        tpeB.setPdv(pdvB);
        tpeB.setNumeroSerie("TPE-IDOR-B-1");
        tpeB = tpeRepository.save(tpeB);

        sousCommercantB = new sous_commercant();
        sousCommercantB.setCommercant(commercantB);
        utilisateur subUserB = persistUser("workspace.idor.subb@test.lanacash.ma");
        sousCommercantB.setUtilisateur(subUserB);
        sousCommercantB = sousCommercantRepository.save(sousCommercantB);
    }

    private utilisateur persistUser(String email) {
        utilisateur user = new utilisateur();
        user.setEmail(email);
        user.setRole(RoleUser.COMMERCANT);
        user.setActive(true);
        user.setDateCreation(LocalDate.now());
        return utilisateurRepository.save(user);
    }

    private String tokenFor(utilisateur user) {
        return TestJwtSupport.mintKeycloakToken("kc-sub-" + user.getId(), user.getEmail(), 300);
    }

    @Test
    void cannotAssignAnotherMerchantsTpeToOwnPdv() {
        assertThatThrownBy(() ->
            merchantWorkspaceManagementService.assignTpeToPdv(
                "Bearer " + tokenFor(commercantAUser),
                String.valueOf(tpeB.getIdTPE()),
                new MerchantTpePdvAssignmentRequest(pdvA.getIdPDV())
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotActivateAnotherMerchantsSubMerchant() {
        assertThatThrownBy(() ->
            merchantWorkspaceManagementService.activateSubMerchant(
                "Bearer " + tokenFor(commercantAUser),
                sousCommercantB.getIdSousCommercant()
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
