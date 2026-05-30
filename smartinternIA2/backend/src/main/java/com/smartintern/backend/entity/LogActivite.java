package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Journal d'activité applicatif.
 * Trace chaque action significative réalisée par un utilisateur.
 *
 * Indexes :
 *   - (user_id, created_at)       → historique par utilisateur trié par date
 *   - (entite_cible, entite_id)   → traçabilité par entité métier
 */
@Entity
@Table(
    name = "logs_activite",
    indexes = {
        @Index(name = "idx_log_user_date",   columnList = "user_id, created_at"),
        @Index(name = "idx_log_entite",      columnList = "entite_cible, entite_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogActivite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_log")
    private Long id;

    // ── Relations ────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ── Champs métier ────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(100)")
    private TypeAction action;

    /** Nom de la table/entité cible (ex : "sprints", "taches", "documents") */
    @Column(name = "entite_cible", length = 50)
    private String entiteCible;

    /** Identifiant de l'entité cible (nullable si action globale) */
    @Column(name = "entite_id")
    private Long entiteId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "adresse_ip", length = 50)
    private String adresseIp;

    @Column(name = "appareil")
    private String appareil;

    // ── Audit ────────────────────────────────────────────────────────────────

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // ── Énumérations ─────────────────────────────────────────────────────────

    public enum TypeAction {
        // Authentification
        CONNEXION,
        DECONNEXION,
        // Documents / Cahier des charges
        UPLOAD_CDC,
        VALIDATION_CDC,
        UPLOAD_DOCUMENT,
        GENERATION_DOCUMENT,
        // Sprints
        CREATION_SPRINT,
        MODIFICATION_SPRINT,
        SUPPRESSION_SPRINT,
        CLOTURE_SPRINT,
        // Tâches
        CREATION_TACHE,
        MODIFICATION_TACHE,
        SUPPRESSION_TACHE,
        VALIDATION_TACHE,
        REFUS_TACHE,
        // IA
        GENERATION_IA_PLAN,
        // Soutenance / évaluation
        VALIDATION_AUTORISATION_SOUTENANCE,
        REFUS_AUTORISATION_SOUTENANCE,
        CLOTURE_EVALUATION_STAGE,
        // Administration
        ATTRIBUTION_ROLE,
        CREATION_COMPTE,
        SUPPRESSION_COMPTE,
        DESACTIVATION_COMPTE,
        // Analyse de risque (AD-04)
        ANALYSE_RISQUE
    }
}
