package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Diagramme : Entreprise
 * Entité indépendante (pas sous-classe de User).
 * Un AdministrateurEntreprise appartient à une Entreprise.
 */
@Entity
@Table(name = "entreprises")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Entreprise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_entreprise")
    private Long id;

    @Column(nullable = false)
    private String nom;

    private String adresse;

    @Column(unique = true)
    private String identifiant;  // SIRET, matricule fiscal, etc.

    @Column(name = "domaine_activite")
    private String domaineActivite;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String logo;

    @Column(name = "site_web")
    private String siteWeb;

    @Column(name = "page_sociale_1")
    private String pageSociale1;

    @Column(name = "page_sociale_2")
    private String pageSociale2;

    @Column(name = "page_sociale_3")
    private String pageSociale3;
}
