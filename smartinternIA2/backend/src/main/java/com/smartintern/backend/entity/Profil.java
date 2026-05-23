package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "profils")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Profil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_profil")
    private Long id;

    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private String adresse;
    private String nationalite;

    @Column(name = "date_naissance")
    private String dateNaissance;

    @Column(name = "titre_professionnel")
    private String titreProfessionnel;

    @Column(columnDefinition = "TEXT")
    private String resume;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cv_standardise_id", nullable = false)
    private CvStandardise cvStandardise;
}