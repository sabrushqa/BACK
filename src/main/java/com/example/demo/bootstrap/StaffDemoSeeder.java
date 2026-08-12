package com.example.demo.bootstrap;

import com.example.demo.entities.back_office;
import com.example.demo.entities.commerciale;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.services.PasswordHashService;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(10)
@ConditionalOnProperty(
    name = "app.demo.staff-seed.enabled",
    havingValue = "true"
)
public class StaffDemoSeeder implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaffDemoSeeder.class);
    private static final String DEFAULT_PASSWORD = "Demo123!";

    private final UtilisateurRepository utilisateurRepository;
    private final CommercialeRepository commercialeRepository;
    private final BackOfficeRepository backOfficeRepository;
    private final PasswordHashService passwordHashService;

    public StaffDemoSeeder(
        UtilisateurRepository utilisateurRepository,
        CommercialeRepository commercialeRepository,
        BackOfficeRepository backOfficeRepository,
        PasswordHashService passwordHashService
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.commercialeRepository = commercialeRepository;
        this.backOfficeRepository = backOfficeRepository;
        this.passwordHashService = passwordHashService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int createdCommerciales = seedCommerciales();
        int createdBackOffices = seedBackOffices();
        LOGGER.info(
            "Staff demo seed completed: {} commerciales et {} back-offices crees (mot de passe initial: {}).",
            createdCommerciales,
            createdBackOffices,
            DEFAULT_PASSWORD
        );
    }

    private int seedCommerciales() {
        List<CommercialeSeed> seeds = List.of(
            new CommercialeSeed(
                "salma.bennani.demo@lanacash.local",
                "BENNANI",
                "Salma",
                "COM-001",
                "Casablanca-Settat",
                "0612345001"
            ),
            new CommercialeSeed(
                "karim.alaoui.demo@lanacash.local",
                "ALAOUI",
                "Karim",
                "COM-002",
                "Rabat-Sale-Kenitra",
                "0612345002"
            ),
            new CommercialeSeed(
                "yasmine.tazi.demo@lanacash.local",
                "TAZI",
                "Yasmine",
                "COM-003",
                "Marrakech-Safi",
                "0612345003"
            )
        );

        int created = 0;
        for (CommercialeSeed seed : seeds) {
            String normalizedEmail = seed.email().trim().toLowerCase(Locale.ROOT);
            if (utilisateurRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                continue;
            }

            utilisateur utilisateur = buildActiveStaffUser(normalizedEmail, RoleUser.COMMERCIAL);
            utilisateur = utilisateurRepository.save(utilisateur);

            commerciale commerciale = new commerciale();
            commerciale.setUtilisateur(utilisateur);
            commerciale.setNom(seed.nom());
            commerciale.setPrenom(seed.prenom());
            commerciale.setMatricule(seed.matricule());
            commerciale.setRegion(seed.region());
            commerciale.setTelephone(seed.telephone());
            commercialeRepository.save(commerciale);
            created++;
        }
        return created;
    }

    private int seedBackOffices() {
        List<BackOfficeSeed> seeds = List.of(
            new BackOfficeSeed(
                "hicham.saidi.demo@lanacash.local",
                "SAIDI",
                "Hicham",
                "BO-001",
                "Verification dossiers"
            ),
            new BackOfficeSeed(
                "sara.elfilali.demo@lanacash.local",
                "EL FILALI",
                "Sara",
                "BO-002",
                "Conformite et signature"
            )
        );

        int created = 0;
        for (BackOfficeSeed seed : seeds) {
            String normalizedEmail = seed.email().trim().toLowerCase(Locale.ROOT);
            if (utilisateurRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                continue;
            }

            utilisateur utilisateur = buildActiveStaffUser(normalizedEmail, RoleUser.BACK_OFFICE);
            utilisateur = utilisateurRepository.save(utilisateur);

            back_office backOffice = new back_office();
            backOffice.setUtilisateur(utilisateur);
            backOffice.setNom(seed.nom());
            backOffice.setPrenom(seed.prenom());
            backOffice.setMatricule(seed.matricule());
            backOffice.setService(seed.service());
            backOfficeRepository.save(backOffice);
            created++;
        }
        return created;
    }

    private utilisateur buildActiveStaffUser(String email, RoleUser role) {
        utilisateur utilisateur = new utilisateur();
        utilisateur.setEmail(email);
        utilisateur.setPassword(passwordHashService.hash(DEFAULT_PASSWORD));
        utilisateur.setRole(role);
        utilisateur.setActive(Boolean.TRUE);
        utilisateur.setDateActivation(LocalDate.now());
        utilisateur.setPasswordExpiresAt(null);
        utilisateur.setTokenVersion(0);
        return utilisateur;
    }

    private record CommercialeSeed(
        String email,
        String nom,
        String prenom,
        String matricule,
        String region,
        String telephone
    ) {
    }

    private record BackOfficeSeed(
        String email,
        String nom,
        String prenom,
        String matricule,
        String service
    ) {
    }
}
