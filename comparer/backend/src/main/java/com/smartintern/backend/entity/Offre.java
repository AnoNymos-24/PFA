package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "offres")
@Data
public class Offre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── INFOS PRINCIPALES ─────────────────────────────────────
    @Column(nullable = false)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String type;         // PFE, Stage d'été, Perfectionnement
    private String duree;        // 3 mois, 6 mois...
    private String lieu;
    private String niveauRequis; // Master 2, Licence 3...
    private String specialite;
    private String competences;  // séparées par virgules

    // ── STATUT ────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    private StatutOffre statut = StatutOffre.ACTIVE;

    public enum StatutOffre {
        ACTIVE, CLOTUREE, ARCHIVEE
    }

    // ── RELATION ENTREPRISE ───────────────────────────────────
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "entreprise_id", nullable = false)
    private User entreprise;

    // ── DATES ─────────────────────────────────────────────────
    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDate dateExpiration;

    // ── STATS ─────────────────────────────────────────────────
    private int nombreCandidatures = 0;
}