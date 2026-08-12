package com.example.demo.services;

import com.example.demo.entities.pdv;
import com.example.demo.repositories.PdvRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Géocode un point de vente en arrière-plan, hors du thread de la requête HTTP.
 *
 * L'appel a Nominatim (via {@link GeocodingService}) est un best-effort qui peut
 * enchaîner plusieurs tentatives espacées de ~1,1s chacune (limite de débit
 * imposée par Nominatim) : le faire de façon synchrone pendant la création ou
 * la correction d'une demande de prospection (surtout avec plusieurs points de
 * vente) ralentissait fortement ces traitements, alors que les coordonnées GPS
 * ne sont jamais bloquantes pour la sauvegarde du dossier.
 */
@Service
public class PdvGeocodingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdvGeocodingService.class);

    private final GeocodingService geocodingService;
    private final PdvRepository pdvRepository;

    public PdvGeocodingService(GeocodingService geocodingService, PdvRepository pdvRepository) {
        this.geocodingService = geocodingService;
        this.pdvRepository = pdvRepository;
    }

    @Async
    @Transactional
    public void geocoderEtMettreAJour(Long idPointVente, String adresse, String quartier, String ville, String codePostal) {
        if (idPointVente == null) {
            return;
        }
        geocodingService
            .geocoder(adresse, quartier, ville, codePostal)
            .ifPresentOrElse(
                coordonnees -> pdvRepository.findById(idPointVente).ifPresent(pointVente -> {
                    pointVente.setLatitude(coordonnees.latitude());
                    pointVente.setLongitude(coordonnees.longitude());
                    pdvRepository.save(pointVente);
                }),
                () -> LOGGER.info("Géocodage différé sans résultat pour le point de vente #{}.", idPointVente)
            );
    }
}
