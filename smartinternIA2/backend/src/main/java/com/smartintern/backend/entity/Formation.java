package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "formations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Formation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_formation")
    private Long id;

    @Column(nullable = false)
    private String diplome;

    @Column(nullable = false)
    private String etablissement;

    private String periode;

    @Column(name = "date_debut")
    private String dateDebut;

    @Column(name = "date_fin")
    private String dateFin;

    private String specialite;
    private String lieu;

    @Builder.Default
    private int ordre = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cv_standardise_id", nullable = false)
    private CvStandardise cvStandardise;
}