package com.smartintern.backend.dto.risque;

import lombok.Data;

import java.util.List;

/**
 * Payload envoyé au microservice Python pour l'analyse de risque d'un stage.
 * Sérialisé en JSON (camelCase) — compatible Jackson par défaut.
 */
@Data
public class RisqueAnalyseRequest {

    private StagePayload stage;
    private EtudiantPayload etudiant;
    private RapportsPayload rapports;
    private List<TachePayload> taches;
    private ActivitePayload activite;

    // ── Sous-structures ───────────────────────────────────────────────────────

    @Data
    public static class StagePayload {
        /** Identifiant du stage */
        private Long id;
        /** Date de début au format ISO-8601 (yyyy-MM-dd) */
        private String dateDebut;
        /** Date de fin au format ISO-8601 (yyyy-MM-dd) */
        private String dateFin;
        /**
         * Pourcentage de la durée totale déjà écoulée (0-100).
         * Calculé en Java : (joursDepuisDebut / dureeTotaleJours) × 100.
         */
        private double progressionTemporelle;
    }

    @Data
    public static class EtudiantPayload {
        private Long id;
        private String nom;
        private String prenom;
    }

    @Data
    public static class RapportsPayload {
        /** Nombre total de rapports attendus selon la durée du stage */
        private int total;
        /** Rapports BROUILLON avec semaineDebut dépassée de plus de 3 jours */
        private int enRetard;
        /** Rapports jamais créés pour des semaines passées */
        private int nonSoumis;
    }

    @Data
    public static class TachePayload {
        private String titre;
        /**
         * Statut réel de la tâche : A_FAIRE, EN_COURS, TERMINEE_PAR_ETUDIANT,
         * VALIDEE, REFUSEE.
         * A_FAIRE est équivalent à NON_COMMENCEE dans la logique métier AD-04.
         */
        private String statut;
        /**
         * Nombre de jours de dépassement par rapport à la date prévue.
         * 0 si la tâche n'est pas en retard ou est dans un état terminal.
         */
        private int joursInactifs;
    }

    @Data
    public static class ActivitePayload {
        /**
         * Date/heure de dernière connexion ISO-8601 (nullable si jamais connecté).
         * Issu de User.derniereConnexion.
         */
        private String derniereConnexion;
        /**
         * Fréquence de connexion calculée sur les 4 dernières semaines
         * (nb connexions / 4.0 semaines).
         */
        private double frequenceConnexionSemaine;
    }
}
