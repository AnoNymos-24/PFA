package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Diagramme : Stage
 * Créé quand une Candidature est acceptée.
 * Relations : Etudiant (1), Entreprise (1), EncadrantAcademique (1), EncadrantEntreprise (1)
 */
@Entity
@Table(name = "stages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_stage")
    private Long id;

    @Column(name = "date_debut")
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(name = "duree_mois")
    private int dureeMois;

    /** Semaine courante du stage — mise à jour chaque lundi par StageProgressionScheduler */
    @Builder.Default
    @Column(name = "semaine_actuelle")
    private Integer semaineActuelle = 1;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Statut statut = Statut.EN_COURS;

    private String sujet;

    @Column(columnDefinition = "TEXT")
    private String mission;

    @Column(name = "evaluation_finale")
    private String evaluationFinale;

    @Builder.Default
    @Column(name = "convention_signee_entreprise")
    private boolean conventionSigneeEntreprise = false;

    @Column(name = "date_signature_entreprise")
    private java.time.LocalDate dateSignatureEntreprise;

    @Builder.Default
    @Column(name = "convention_signee_encadrant")
    private boolean conventionSigneeEncadrant = false;

    @Column(name = "date_signature_encadrant")
    private java.time.LocalDate dateSignatureEncadrant;

    // La candidature dont est issu ce stage
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidature_id", unique = true)
    private Candidature candidature;

    // Concerne un étudiant
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etudiant_id", nullable = false)
    private Etudiant etudiant;

    // Se déroule dans une entreprise
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entreprise_id", nullable = false)
    private Entreprise entreprise;

    // Suivi par un encadrant académique
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encadrant_academique_id")
    private EncadrantAcademique encadrantAcademique;

    // Encadré par un encadrant entreprise
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encadrant_entreprise_id")
    private EncadrantEntreprise encadrantEntreprise;

    // ── Champs v7 : évaluation finale ────────────────────────────────────────

    /** Observation libre rédigée par l'entreprise en fin de stage */
    @Column(name = "observation_finale_entreprise", columnDefinition = "TEXT")
    private String observationFinaleEntreprise;

    /** Date à laquelle l'évaluation finale a été clôturée */
    @Column(name = "date_cloture_evaluation")
    private LocalDateTime dateClotureEvaluation;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "statut_evaluation")
    private StatutEvaluation statutEvaluation = StatutEvaluation.NON_DEMARREE;

    // ── Énumérations ─────────────────────────────────────────────────────────

    public enum Statut {
        EN_COURS, TERMINE, ABANDONNE, SUSPENDU
    }

    public enum StatutEvaluation {
        NON_DEMARREE, EN_COURS, CLOTUREE
    }
}
