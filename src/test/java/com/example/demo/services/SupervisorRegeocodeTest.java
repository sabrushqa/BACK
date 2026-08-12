package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.entities.commercant;
import com.example.demo.entities.pdv;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exerce regeocoderPdvsExistants(), jamais appele dans les autres tests: filtre
 * les PDV sans coordonnees mais avec adresse/ville et date recente, tente un
 * nouveau geocodage (service mocke pour eviter tout vrai appel reseau et le
 * throttling d'1.1s par PDV), et compte les succes/echecs.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class SupervisorRegeocodeTest {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private PdvRepository pdvRepository;

    @Autowired
    private SwitchMonetiqueClient switchMonetiqueClient;

    @Autowired
    private PasswordHashService passwordHashService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ActivationMailService activationMailService;

    @Autowired
    private KeycloakAdminService keycloakAdminService;

    @Autowired
    private SupervisorNotificationService supervisorNotificationService;

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

    private SupervisorManagementService buildServiceWithMockGeocoder(GeocodingService geocodingService) {
        return new SupervisorManagementService(
            utilisateurRepository,
            backOfficeRepository,
            commercialeRepository,
            commercantRepository,
            switchMonetiqueClient,
            dossierAffiliationRepository,
            pdvRepository,
            passwordHashService,
            jwtService,
            activationMailService,
            keycloakAdminService,
            supervisorNotificationService,
            geocodingService,
            60,
            "http://localhost:4200",
            "2000-01-01"
        );
    }

    @Test
    void regeocodesEligiblePdvsAndReportsSuccessCount() {
        GeocodingService geocodingService = mock(GeocodingService.class);
        when(geocodingService.geocoder(eq("12 rue Test"), any(), eq("Casablanca"), any()))
            .thenReturn(Optional.of(new GeocodingService.Coordonnees(33.5731, -7.5898)));
        SupervisorManagementService service = buildServiceWithMockGeocoder(geocodingService);

        utilisateur superviseur = persistUser("superviseur.regeocode@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Regeocode Test");
        commercant = commercantRepository.save(commercant);

        pdv pointVente = new pdv();
        pointVente.setCommercant(commercant);
        pointVente.setNomPDV("PDV a regeocoder");
        pointVente.setAdresse("12 rue Test");
        pointVente.setVille("Casablanca");
        pointVente.setDateCreation(LocalDate.now());
        pointVente = pdvRepository.save(pointVente);
        final Long pdvId = pointVente.getIdPDV();

        var response = service.regeocoderPdvsExistants("Bearer " + tokenFor(superviseur));

        assertThat(response.message()).contains("1 point(s) de vente géolocalisé(s) sur 1 tenté(s)");
        pdv reloaded = pdvRepository.findById(pdvId).orElseThrow();
        assertThat(reloaded.getLatitude()).isEqualTo(33.5731);
        assertThat(reloaded.getLongitude()).isEqualTo(-7.5898);
    }

    @Test
    void skipsPdvsAlreadyGeolocatedOrMissingAddress() {
        GeocodingService geocodingService = mock(GeocodingService.class);
        SupervisorManagementService service = buildServiceWithMockGeocoder(geocodingService);

        utilisateur superviseur = persistUser("superviseur.regeocode.skip@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Regeocode Skip Test");
        commercant = commercantRepository.save(commercant);

        pdv alreadyGeolocated = new pdv();
        alreadyGeolocated.setCommercant(commercant);
        alreadyGeolocated.setAdresse("1 rue Deja Geocode");
        alreadyGeolocated.setVille("Rabat");
        alreadyGeolocated.setLatitude(34.0209);
        alreadyGeolocated.setLongitude(-6.8416);
        alreadyGeolocated.setDateCreation(LocalDate.now());
        pdvRepository.save(alreadyGeolocated);

        pdv missingAddress = new pdv();
        missingAddress.setCommercant(commercant);
        missingAddress.setVille("Rabat");
        missingAddress.setDateCreation(LocalDate.now());
        pdvRepository.save(missingAddress);

        var response = service.regeocoderPdvsExistants("Bearer " + tokenFor(superviseur));

        assertThat(response.message()).contains("0 point(s) de vente géolocalisé(s) sur 0 tenté(s)");
    }
}
