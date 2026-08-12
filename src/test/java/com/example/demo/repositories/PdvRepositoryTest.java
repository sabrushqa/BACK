package com.example.demo.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.entities.commercant;
import com.example.demo.entities.pdv;
import com.example.demo.entities.sous_commercant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests d'integration des requetes JPQL personnalisees de PdvRepository
 * contre SQL Server reel (master5_test), pour verifier qu'elles compilent
 * et se comportent correctement sur ce moteur (pas seulement sur H2).
 */
@SpringBootTest
@Transactional
class PdvRepositoryTest {

    @Autowired
    private PdvRepository pdvRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private SousCommercantRepository sousCommercantRepository;

    private commercant commercantA;
    private commercant commercantB;

    @BeforeEach
    void setUp() {
        commercantA = commercantRepository.save(newCommercant("Boutique A"));
        commercantB = commercantRepository.save(newCommercant("Boutique B"));
    }

    private commercant newCommercant(String nom) {
        commercant c = new commercant();
        c.setNomCommercial(nom);
        return c;
    }

    private pdv newPdv(commercant owner, String statut) {
        pdv p = new pdv();
        p.setCommercant(owner);
        p.setStatut(statut);
        p.setNomPDV("Point de vente " + owner.getNomCommercial());
        return pdvRepository.save(p);
    }

    @Test
    void countByCommercantOnlyCountsOwnPdvs() {
        newPdv(commercantA, "ACTIF");
        newPdv(commercantA, "ACTIF");
        newPdv(commercantB, "ACTIF");

        assertThat(pdvRepository.countByCommercant_IdCommercant(commercantA.getIdCommercant())).isEqualTo(2);
        assertThat(pdvRepository.countByCommercant_IdCommercant(commercantB.getIdCommercant())).isEqualTo(1);
    }

    @Test
    void updateStatutByCommercantIdOnlyAffectsTargetedCommercant() {
        pdv pdvA1 = newPdv(commercantA, "EN_ATTENTE");
        pdv pdvA2 = newPdv(commercantA, "EN_ATTENTE");
        pdv pdvB1 = newPdv(commercantB, "EN_ATTENTE");

        int updated = pdvRepository.updateStatutByCommercantId(commercantA.getIdCommercant(), "ACTIF");

        assertThat(updated).isEqualTo(2);
        assertThat(pdvRepository.findById(pdvA1.getIdPDV()).orElseThrow().getStatut()).isEqualTo("ACTIF");
        assertThat(pdvRepository.findById(pdvA2.getIdPDV()).orElseThrow().getStatut()).isEqualTo("ACTIF");
        assertThat(pdvRepository.findById(pdvB1.getIdPDV()).orElseThrow().getStatut()).isEqualTo("EN_ATTENTE");
    }

    @Test
    void findDistinctSousCommercantsByCommercantIdExcludesNullAndOtherCommercants() {
        sous_commercant sousA = new sous_commercant();
        sousA.setCommercant(commercantA);
        sousA.setNom("Sous-commercant A");
        sousA = sousCommercantRepository.save(sousA);

        pdv pdvWithSousCommercant = new pdv();
        pdvWithSousCommercant.setCommercant(commercantA);
        pdvWithSousCommercant.setSousCommercant(sousA);
        pdvWithSousCommercant.setStatut("ACTIF");
        pdvRepository.save(pdvWithSousCommercant);

        newPdv(commercantA, "ACTIF");
        newPdv(commercantB, "ACTIF");

        List<sous_commercant> results = pdvRepository.findDistinctSousCommercantsByCommercantId(
            commercantA.getIdCommercant()
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getNom()).isEqualTo("Sous-commercant A");
    }

    @Test
    void findTop8ByCommercantOrdersByIdDescendingAndCapsAtEight() {
        for (int i = 0; i < 10; i++) {
            newPdv(commercantA, "ACTIF");
        }

        List<pdv> top8 = pdvRepository.findTop8ByCommercant_IdCommercantOrderByIdPDVDesc(
            commercantA.getIdCommercant()
        );

        assertThat(top8).hasSize(8);
        assertThat(top8).isSortedAccordingTo((a, b) -> Long.compare(b.getIdPDV(), a.getIdPDV()));
    }
}
