package com.example.demo.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.services.PasswordHashService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le seeder de comptes de demonstration staff (commerciales et back-offices)
 * est desactive en test (app.demo.staff-seed.enabled=false) donc jamais
 * instancie par le contexte Spring. On le construit directement ici pour
 * verifier qu'il cree bien les comptes attendus et qu'il est idempotent
 * (relancer le seed ne duplique pas les comptes deja crees).
 */
@SpringBootTest
@Transactional
class StaffDemoSeederTest {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @Autowired
    private PasswordHashService passwordHashService;

    private StaffDemoSeeder buildSeeder() {
        return new StaffDemoSeeder(
            utilisateurRepository,
            commercialeRepository,
            backOfficeRepository,
            passwordHashService,
            "Demo123!"
        );
    }

    @Test
    void seedsCommercialesAndBackOfficesOnFirstRun() {
        StaffDemoSeeder seeder = buildSeeder();

        seeder.run(null);

        assertThat(utilisateurRepository.existsByEmailIgnoreCase("salma.bennani.demo@lanacash.local")).isTrue();
        assertThat(utilisateurRepository.existsByEmailIgnoreCase("hicham.saidi.demo@lanacash.local")).isTrue();

        var commercialUser = utilisateurRepository.findByEmailIgnoreCase("salma.bennani.demo@lanacash.local")
            .orElseThrow();
        assertThat(commercialUser.getRole()).isEqualTo(RoleUser.COMMERCIAL);
        assertThat(commercialeRepository.findByUtilisateur_Id(commercialUser.getId())).isPresent();

        var backOfficeUser = utilisateurRepository.findByEmailIgnoreCase("hicham.saidi.demo@lanacash.local")
            .orElseThrow();
        assertThat(backOfficeUser.getRole()).isEqualTo(RoleUser.BACK_OFFICE);
        assertThat(backOfficeRepository.findByUtilisateur_Id(backOfficeUser.getId())).isPresent();
    }

    @Test
    void secondRunDoesNotDuplicateExistingAccounts() {
        StaffDemoSeeder seeder = buildSeeder();

        seeder.run(null);
        long usersAfterFirstRun = utilisateurRepository.count();

        seeder.run(null);
        long usersAfterSecondRun = utilisateurRepository.count();

        assertThat(usersAfterSecondRun).isEqualTo(usersAfterFirstRun);
    }
}
