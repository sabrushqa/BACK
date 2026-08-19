package com.example.demo.repositories;

import com.example.demo.entities.Reclamation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReclamationRepository extends JpaRepository<Reclamation, Long> {

    // Deduplication : v2 (agent/tools/ticket_tool.py) et le portail marchand
    // (CommercantReclamationsPage.tsx) peuvent tous les deux essayer de
    // persister le MEME ticket (meme referenceChat) — l'un cote serveur des
    // qu'escalate_to_ticket est appele, l'autre cote client des que la
    // reponse contenant `ticket` arrive. Sans cette recherche, chaque appel
    // creerait sa propre ligne (2 tickets pour 1 seul probleme signale).
    Optional<Reclamation> findByReferenceChat(String referenceChat);

    List<Reclamation> findByTpeReference(String tpeReference);

    List<Reclamation> findByCommercant_IdCommercantOrderByDateCreationDesc(Long commercantId);

    // Un sous-commerçant ne doit voir que les réclamations liées à un TPE de
    // SON PDV, pas tout l'historique du commerçant parent (même principe que
    // TpeRepository::findBySubMerchantUserIdOrderByIdDesc / TransactionsRepository
    // ::findTop8ByTpe_Pdv_SousCommercant_Utilisateur_Id...).
    List<Reclamation> findByTpe_Pdv_SousCommercant_Utilisateur_IdOrderByDateCreationDesc(Long utilisateurId);

    long countByCommercant_IdCommercant(Long commercantId);

    long countByCommercant_IdCommercantAndStatut(Long commercantId, String statut);

    // ── Backoffice queries ────────────────────────────────────────────────────

    List<Reclamation> findAllByOrderByDateCreationDescIdReclamationDesc();

    List<Reclamation> findByStatutOrderByDateCreationDescIdReclamationDesc(String statut);

    List<Reclamation> findByPrioriteOrderByDateCreationDescIdReclamationDesc(String priorite);

    List<Reclamation> findByTypeProblemeOrderByDateCreationDescIdReclamationDesc(String typeProbleme);

    long countByStatut(String statut);

    long countByPriorite(String priorite);

    long countByTypeProbleme(String typeProbleme);
}
