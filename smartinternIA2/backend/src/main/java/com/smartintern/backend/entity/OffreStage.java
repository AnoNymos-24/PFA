package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Diagramme : OffreStage
 * CORRECTIONS par rapport à l'ancienne classe Offre :
 * - Renommage : Offre → OffreStage
 * - Ajout : type_stage, theme, niveau_requis, date_publication, nombre_places, remuneration, statut_validation
 * - Renommage : dateLimite → dateExpiration, ville → localisation
 * - Relation : user (AdministrateurEntreprise) → entreprise (Entreprise)
 */
@Entity
@Table(name = "offres_stage")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OffreStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_offre")
    private Long id;

    @Column(nullable = false)
    private String titre;

    // Diagramme : type_stage (ex: PFE, PFA, Stage d'été)
    @Column(name = "type_stage")
    private String typeStage;

    private String domaine;

    @Column(name = "duree_mois")
    private int dureeMois;

    // Statut de l'offre (ACTIVE, FERMEE, EXPIREE)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Statut statut = Statut.ACTIVE;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Diagramme : theme
    private String theme;

    // Diagramme : localisation (remplace ville)
    private String localisation;

    // Diagramme : niveau_requis (ex: Bac+3, Bac+5)
    @Column(name = "niveau_requis")
    private String niveauRequis;

    // Diagramme : date_publication
    @Column(name = "date_publication")
    private LocalDate datePublication;

    // Diagramme : date_expiration (anciennement dateLimite)
    @Column(name = "date_expiration")
    private LocalDate dateExpiration;

    // Diagramme : nombre_places
    @Column(name = "nombre_places")
    private Integer nombrePlaces;

    // Diagramme : remuneration (boolean)
    @Builder.Default
    private Boolean remuneration = false;

    // Diagramme : statut_validation (EN_ATTENTE, VALIDEE, REJETEE)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "statut_validation")
    private StatutValidation statutValidation = StatutValidation.EN_ATTENTE;

    // Relation vers l'Entreprise (remplace le champ String entreprise)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entreprise_id", nullable = false)
    private Entreprise entreprise;

    // Relation vers l'utilisateur qui a créé l'offre (AdministrateurEntreprise)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "createur_id")
    private User createur;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (datePublication == null) {
            datePublication = LocalDate.now();
        }
    }

    public enum Statut {
        ACTIVE, FERMEE, EXPIREE
    }

    public enum StatutValidation {
        EN_ATTENTE, VALIDEE, REJETEE
    }
}
