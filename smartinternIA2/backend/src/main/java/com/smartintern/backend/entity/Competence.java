package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "competences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Competence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_competence")
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeCompetence type;

    private String niveau;

    @Column(name = "annees_experience")
    private Integer anneesExperience;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cv_standardise_id", nullable = false)
    private CvStandardise cvStandardise;

    public enum TypeCompetence {
        TECHNIQUE, SOFT_SKILL, OUTIL, AUTRE
    }
}