package com.example.demo.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifie l'ordre et le cloisonnement par commercant des requetes de
 * DossierAffiliationRepository, contre SQL Server reel.
 */
@SpringBootTest
@Transactional
class DossierAffiliationRepositoryTest {

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private CommercantRepository commercantRepository;

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

    private dossier_affiliation newDossier(commercant owner, LocalDate dateSoumission) {
        dossier_affiliation d = new dossier_affiliation();
        d.setCommercant(owner);
        d.setDateSoumission(dateSoumission);
        return dossierAffiliationRepository.save(d);
    }

    @Test
    void findFirstByCommercantReturnsMostRecentSubmissionOnly() {
        newDossier(commercantA, LocalDate.now().minusDays(5));
        dossier_affiliation mostRecentForA = newDossier(commercantA, LocalDate.now());
        newDossier(commercantB, LocalDate.now());

        Optional<dossier_affiliation> result = dossierAffiliationRepository
            .findFirstByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(commercantA.getIdCommercant());

        assertThat(result).isPresent();
        assertThat(result.get().getIdDossier()).isEqualTo(mostRecentForA.getIdDossier());
    }

    @Test
    void findAllByCommercantExcludesOtherCommercants() {
        newDossier(commercantA, LocalDate.now());
        newDossier(commercantA, LocalDate.now());
        newDossier(commercantB, LocalDate.now());

        List<dossier_affiliation> results = dossierAffiliationRepository
            .findAllByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(commercantA.getIdCommercant());

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(d -> d.getCommercant().getIdCommercant().equals(commercantA.getIdCommercant()));
    }

    @Test
    void findAllByRequestedPdvIsNotNullOnlyReturnsExtensionDossiers() {
        newDossier(commercantA, LocalDate.now());

        List<dossier_affiliation> results = dossierAffiliationRepository.findAllByRequestedPdvIsNotNull();

        assertThat(results).isEmpty();
    }
}
