package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stages")
@Data
public class Stage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "candidature_id", nullable = false)
    private Candidature candidature;

    @Enumerated(EnumType.STRING)
    private StatutStage statut = StatutStage.EN_COURS;

    public enum StatutStage {
        EN_COURS, TERMINE, SUSPENDU
    }

    // Dates
    private LocalDate dateDebut;
    private LocalDate dateFin;

    // Convention
    private boolean conventionSigneeEntreprise = false;
    private boolean conventionSigneeEncadrantEntreprise = false;
    private LocalDateTime dateSignatureConvention;

    // Mission
    @Column(length = 5000)
    private String missionDescription;
    @Column(length = 5000)
    private String objectifs;
    private boolean missionDefinie = false;

    // Progression
    private int semaineActuelle = 1;
    private int dureeSemaines = 12;
    private LocalDateTime createdAt = LocalDateTime.now();

    // Encadrants
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "encadrant_entreprise_id")
    private User encadrantEntreprise;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "encadrant_academique_id")
    private User encadrantAcademique;
    
    private boolean soutenanceAutorisee = false;
private LocalDate dateSoutenance;

@Column(length = 1000)
private String commentaireSoutenance;
}