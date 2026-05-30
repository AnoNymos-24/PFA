package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Résultat d'une analyse de risque d'engagement réalisée par le microservice IA.
 * <p>
 * Un seul résultat est conservé par stage (upsert) :
 * l'analyse la plus récente écrase la précédente.
 * <p>
 * Table : {@code resultats_risque}
 */
@Entity
@Table(
    name = "resultats_risque",
    indexes = {
        @Index(name = "idx_risque_stage",  columnList = "stage_id"),
        @Index(name = "idx_risque_niveau", columnList = "niveau_risque")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultatRisque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_resultat_risque")
    private Long id;

    // ── Relation ──────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private Stage stage;

    // ── Résultat principal ────────────────────────────────────────────────────

    /** Score d'engagement entre 0 et 100. */
    @Column(name = "score_engagement", nullable = false)
    private int scoreEngagement;

    @Enumerated(EnumType.STRING)
    @Column(name = "niveau_risque", nullable = false, length = 20)
    private NiveauRisque niveauRisque;

    // ── Détails sérialisés en JSON ─────────────────────────────────────────────
    // Stockés en TEXT pour éviter de multiplier les tables.
    // Désérialisés côté service avant d'être renvoyés au client.

    /** JSON : List<RisqueAnalyseResponse.Alerte> */
    @Column(name = "alertes_json", columnDefinition = "TEXT")
    private String alertesJson;

    /** JSON : List<String> */
    @Column(name = "recommandations_json", columnDefinition = "TEXT")
    private String recommandationsJson;

    /** JSON : RisqueAnalyseResponse.AnalyseDetail */
    @Column(name = "analyse_json", columnDefinition = "TEXT")
    private String analyseJson;

    // ── Audit ──────────────────────────────────────────────────────────────────

    /** Horodatage de l'analyse. */
    @Column(name = "analyse_le", nullable = false)
    private LocalDateTime analyseLe;

    @PrePersist
    protected void onCreate() {
        if (analyseLe == null) {
            analyseLe = LocalDateTime.now();
        }
    }

    // ── Énumération ────────────────────────────────────────────────────────────

    public enum NiveauRisque {
        /** 80-100 — engagement excellent, aucune action requise. */
        EXCELLENT,
        /** 60-79 — situation normale, suivi standard. */
        STABLE,
        /** 40-59 — signaux faibles, surveillance renforcée. */
        A_SURVEILLER,
        /** 0-39  — risque élevé d'abandon ou d'échec, intervention nécessaire. */
        A_RISQUE
    }
}
