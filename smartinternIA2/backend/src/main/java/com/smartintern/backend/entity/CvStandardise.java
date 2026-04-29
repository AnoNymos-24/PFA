package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "cv_standardises")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CvStandardise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cv")
    private Long id;

    @Column(name = "fichier_original")
    private String fichierOriginal;

    @Column(name = "fichier_standardise")
    private String fichierStandardise;

    // Score de complétude calculé par le microservice Python (0–100)
    @Column(name = "score_completude")
    private Float scoreCompletude;

    @Column(name = "date_extraction")
    private LocalDateTime dateExtraction;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "statut_extraction")
    private StatutExtraction statutExtraction = StatutExtraction.EN_COURS;

    @Column(name = "donnees_json_brutes", columnDefinition = "TEXT")
    private String donneesJsonBrutes;

    // INSUFFISANT / MOYEN / BON / EXCELLENT
    @Column(name = "niveau_qualite")
    private String niveauQualite;

    @Column(name = "recommandations", columnDefinition = "TEXT")
    private String recommandations;

    // ── Relations ──────────────────────────────────────────────────────────

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etudiant_id", nullable = false)
    private Etudiant etudiant;

    @OneToOne(mappedBy = "cvStandardise", cascade = CascadeType.ALL,
              fetch = FetchType.LAZY, orphanRemoval = true)
    private Profil profil;

    @OneToMany(mappedBy = "cvStandardise", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<Experience> experiences;

    @OneToMany(mappedBy = "cvStandardise", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<Formation> formations;

    @OneToMany(mappedBy = "cvStandardise", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Competence> competences;

    @OneToMany(mappedBy = "cvStandardise", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Langue> langues;

    @PrePersist
    protected void onCreate() {
        dateExtraction = LocalDateTime.now();
    }

    public enum StatutExtraction {
        EN_COURS, EXTRAIT, ERREUR
    }
}