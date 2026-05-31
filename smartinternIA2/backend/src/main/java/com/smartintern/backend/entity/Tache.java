package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tâche associée à un Sprint.
 * Soft-delete : deletedAt / deletedByUserId.
 * Filtrage : toujours passer par TacheRepository#findBySprintIdAndDeletedAtIsNull…
 */
@Entity
@Table(name = "taches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tache")
    private Long id;

    // ── Relations ────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sprint_id", nullable = false)
    private Sprint sprint;

    // ── Champs métier ────────────────────────────────────────────────────────

    @Column(nullable = false)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Statut statut = Statut.A_FAIRE;

    @Column(name = "date_debut_prevue")
    private LocalDate dateDebutPrevue;

    @Column(name = "date_fin_prevue")
    private LocalDate dateFinPrevue;

    /** Date à laquelle l'étudiant a marqué la tâche comme terminée */
    @Column(name = "date_realisation_etudiant")
    private LocalDateTime dateRealisationEtudiant;

    /** Date à laquelle l'encadrant a traité la tâche (validation ou refus) */
    @Column(name = "date_traitement_encadrant")
    private LocalDateTime dateTraitementEncadrant;

    /** Note / commentaire rédigé par l'étudiant */
    @Column(name = "note_etudiant", columnDefinition = "TEXT")
    private String noteEtudiant;

    /** Observation de l'encadrant lors du traitement */
    @Column(name = "observation_encadrant", columnDefinition = "TEXT")
    private String observationEncadrant;

    /** Note numérique attribuée par l'encadrant (nullable) */
    @Column(name = "note")
    private Float note;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "decision_validation")
    private DecisionValidation decisionValidation = DecisionValidation.NON_TRAITE;

    /** Vrai si la tâche a été générée automatiquement par l'IA */
    @Builder.Default
    @Column(name = "generee_par_ia")
    private Boolean genereParIa = false;

    // ── Soft-delete ──────────────────────────────────────────────────────────

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by_user_id")
    private Long deletedByUserId;

    // ── Audit ────────────────────────────────────────────────────────────────

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Énumérations ─────────────────────────────────────────────────────────

    public enum Statut {
        A_FAIRE, EN_COURS, TERMINEE_PAR_ETUDIANT, VALIDEE, REFUSEE
    }

    public enum DecisionValidation {
        NON_TRAITE, VALIDEE, REFUSEE
    }
}
