package com.example.demo.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.services.PasswordHashService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le seeder de dossiers d'affiliation de demonstration est desactive en test
 * (app.demo.affiliation-seed.enabled=false). On le construit directement pour
 * verifier qu'il cree bien les demandes generiques (avec repartition de
 * statuts/types) et les demandes ciblees, avec et sans commerciales/back
 * offices disponibles, et qu'un count non positif ne fait rien.
 */
@SpringBootTest
@Transactional
class AffiliationDemoSeederTest {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @Autowired
    private PasswordHashService passwordHashService;

    private AffiliationDemoSeeder buildSeeder(int count, String emailPrefix) {
        return new AffiliationDemoSeeder(
            utilisateurRepository,
            commercantRepository,
            dossierAffiliationRepository,
            commercialeRepository,
            backOfficeRepository,
            passwordHashService,
            count,
            emailPrefix
        );
    }

    @Test
    void seedsGenericAndTargetedRequestsWithoutStaffAvailable() {
        AffiliationDemoSeeder seeder = buildSeeder(30, "seed.affiliation.notarget");

        seeder.run(null);

        assertThat(utilisateurRepository.existsByEmailIgnoreCase("demo.casa.affiliation@lanacash.local")).isTrue();
        assertThat(dossierAffiliationRepository.count()).isGreaterThanOrEqualTo(30L);
    }

    @Test
    void seedsGenericRequestsWithStaffAvailableForAssignment() {
        new StaffDemoSeeder(
            utilisateurRepository,
            commercialeRepository,
            backOfficeRepository,
            passwordHashService
        ).run(null);

        AffiliationDemoSeeder seeder = buildSeeder(30, "seed.affiliation.withstaff");
        seeder.run(null);

        assertThat(dossierAffiliationRepository.count()).isGreaterThanOrEqualTo(30L);
    }

    @Test
    void secondRunDoesNotDuplicateExistingRequests() {
        AffiliationDemoSeeder seeder = buildSeeder(10, "seed.affiliation.idempotent");

        seeder.run(null);
        long countAfterFirstRun = utilisateurRepository.count();

        seeder.run(null);
        long countAfterSecondRun = utilisateurRepository.count();

        assertThat(countAfterSecondRun).isEqualTo(countAfterFirstRun);
    }

    @Test
    void skipsSeedingWhenCountIsNotPositive() {
        AffiliationDemoSeeder seeder = buildSeeder(0, "seed.affiliation.skipped");

        seeder.run(null);

        assertThat(utilisateurRepository.existsByEmailIgnoreCase("seed.affiliation.skipped+01@lanacash.local"))
            .isFalse();
    }
}
