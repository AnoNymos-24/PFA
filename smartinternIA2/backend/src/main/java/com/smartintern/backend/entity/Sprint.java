package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Sprint agile lié à un Stage.
 *
 * Contrainte métier : (stage_id, numero) doit être unique parmi les sprints
 * non supprimés. Cette unicité est gérée en applicatif (pas en DB) car les
 * lignes soft-deleted doivent pouvoir conserver leur numéro.
 *
 * Soft-delete : deletedAt / deletedByUserId.
 * Filtrage : toujours passer par SprintRepository#findByStageIdAndDeletedAtIsNull…
 */
@Entity
@Table(name = "sprints")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sprint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sprint")
    private Long id;

    // ── Relations ────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private Stage stage;

    // ── Champs métier ────────────────────────────────────────────────────────

    // Unicité composite (stage_id, numero) parmi les non supprimés — filtrage applicatif
    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "date_debut")
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(name = "date_cloture_reelle")
    private LocalDateTime dateClotureReelle;

    @Column(name = "observation_encadrant", columnDefinition = "TEXT")
    private String observationEncadrant;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Statut statut = Statut.PLANIFIE;

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
        PLANIFIE, EN_COURS, CLOTURE
    }
}
