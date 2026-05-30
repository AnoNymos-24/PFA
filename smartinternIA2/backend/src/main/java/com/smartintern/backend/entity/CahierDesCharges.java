package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Cahier des charges associé à un stage.
 * Lié à un Document (fichier PDF uploadé) et à un Stage (1:1 unique par stage).
 * Soft-delete non applicable ici — un CDC se révoque via statut.
 */
@Entity
@Table(name = "cahiers_des_charges")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CahierDesCharges {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cahier_des_charges")
    private Long id;

    // ── Relations ────────────────────────────────────────────────────────────

    /** Fichier PDF du cahier des charges (optionnel — peut être null avant upload) */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", unique = true)
    private Document document;

    /** Stage auquel ce CDC est attaché — unicité : un seul CDC actif par stage */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", unique = true, nullable = false)
    private Stage stage;

    // ── Champs métier ────────────────────────────────────────────────────────

    @Column(nullable = false)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String objectifs;

    @Column(columnDefinition = "TEXT")
    private String contraintes;

    @Column(columnDefinition = "TEXT")
    private String livrables;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Statut statut = Statut.BROUILLON;

    @Column(name = "date_validation")
    private LocalDateTime dateValidation;

    /** Chemin relatif du fichier uploadé (ex : uploads/cdc/cdc_1_123456.pdf) */
    @Column(name = "url_fichier")
    private String urlFichier;

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
        BROUILLON, VALIDE
    }
}
