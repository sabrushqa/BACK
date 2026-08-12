package com.example.demo.dto;

import java.util.List;

/**
 * Profil commerçant exposé au chatbot (endpoint interne, X-Internal-Token).
 *
 * Sert à répondre à la question "avec qui le chatbot parle" avant de traiter
 * un message : un commerçant en E_COMMERCE pur n'a ni PDV ni TPE au sens
 * encaissement physique, donc un message "problème TPE" ne le concerne pas
 * — l'agent doit le détecter plutôt que de dérouler un diagnostic TPE
 * générique. supportsTpe / supportsEcommerce sont dérivés de typeAffiliation
 * une fois pour toutes ici, pour ne pas dupliquer cette logique côté Python.
 */
public record ChatbotMerchantProfileResponse(
    Long commercantId,
    String typeAffiliation,
    boolean supportsTpe,
    boolean supportsEcommerce,
    List<PdvItem> pdvs,
    List<TpeItem> tpes
) {
    public record PdvItem(
        Long id,
        String nom,
        String ville,
        String adresse
    ) {
    }

    public record TpeItem(
        String numeroSerie,
        String modele,
        Long pdvId,
        String pdvNom
    ) {
    }
}
