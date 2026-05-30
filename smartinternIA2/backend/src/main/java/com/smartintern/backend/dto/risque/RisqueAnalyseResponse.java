package com.smartintern.backend.dto.risque;

import lombok.Data;

import java.util.List;

/**
 * Réponse du microservice Python pour l'analyse de risque d'engagement.
 * Désérialisée depuis JSON (camelCase) — compatible Jackson.
 */
@Data
public class RisqueAnalyseResponse {

    private boolean success;

    /** Score d'engagement de l'étudiant : entier entre 0 (abandon) et 100 (optimal). */
    private int scoreEngagement;

    /**
     * Niveau de risque calculé depuis le score :
     *   80-100 → EXCELLENT
     *   60-79  → STABLE
     *   40-59  → A_SURVEILLER
     *   0-39   → A_RISQUE
     */
    private String niveauRisque;

    /** Indicateurs booléens détaillant les causes du score. */
    private AnalyseDetail analyse;

    /** Liste des alertes générées, triées par niveau décroissant. */
    private List<Alerte> alertes;

    /** Actions recommandées pour améliorer le score (encadrant / admin). */
    private List<String> recommandations;

    /** Message d'erreur non vide uniquement si success=false. */
    private String erreur;

    // ── Sous-structures ───────────────────────────────────────────────────────

    @Data
    public static class AnalyseDetail {
        /** Vrai si au moins 2 rapports sont en retard ou non soumis. */
        private boolean retardsImportants;
        /** Vrai si la dernière connexion est > 7 jours. */
        private boolean inactiviteDetectee;
        /** Vrai si des tâches A_FAIRE ont leur date de début prévue dépassée. */
        private boolean tachesNonCommencees;
        /** Vrai si fréquence de connexion < 2 fois/semaine. */
        private boolean faibleFrequenceConnexion;
    }

    @Data
    public static class Alerte {
        /** Catégorie de l'alerte (ex : RAPPORT, TACHE, INACTIVITE, RETARD_GLOBAL). */
        private String type;
        /** Gravité : INFO, AVERTISSEMENT, CRITIQUE. */
        private String niveau;
        private String message;
    }
}
