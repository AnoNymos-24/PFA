package com.smartintern.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AD-04 — DTO agrégé pour le tableau de bord des risques.
 *
 * <p>Regroupe les statistiques globales sur l'ensemble des stages analysés
 * ainsi que la liste ordonnée (du plus risqué au moins risqué).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardRisqueDto {

    // ── Compteurs par niveau ───────────────────────────────────────────────────

    /** Nombre total de stages ayant au moins un résultat d'analyse. */
    private int totalStagesAnalyses;

    /** Nombre de stages au niveau EXCELLENT (score ≥ 80). */
    private int nbExcellent;

    /** Nombre de stages au niveau STABLE (score 60-79). */
    private int nbStable;

    /** Nombre de stages au niveau A_SURVEILLER (score 40-59). */
    private int nbASurveiller;

    /** Nombre de stages au niveau A_RISQUE (score < 40). */
    private int nbARisque;

    // ── Statistiques globales ──────────────────────────────────────────────────

    /** Score moyen d'engagement (arrondi à 1 décimale). */
    private double scoreMoyen;

    /** Score d'engagement le plus bas parmi tous les stages analysés. */
    private int scoreMin;

    /** Score d'engagement le plus élevé parmi tous les stages analysés. */
    private int scoreMax;

    /** Nombre total d'alertes de niveau CRITIQUE sur l'ensemble des stages. */
    private int nbAlertesCritiques;

    // ── Liste détaillée ────────────────────────────────────────────────────────

    /**
     * Liste résumée de tous les stages, triée par score croissant
     * (les cas les plus urgents en premier).
     */
    private List<ResultatRisqueDto.Resume> stagesParRisque;
}
