package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "experiences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Experience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_experience")
    private Long id;

    @Column(nullable = false)
    private String poste;

    @Column(nullable = false)
    private String entreprise;

    private String periode;

    @Column(name = "date_debut")
    private String dateDebut;

    @Column(name = "date_fin")
    private String dateFin;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String lieu;

    @Builder.Default
    private int ordre = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cv_standardise_id", nullable = false)
    private CvStandardise cvStandardise;
}