package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Diagramme : EncadrantEntreprise
 * Attributs : matricule, disponibilite, domaine, departement
 * Appartient à une Entreprise
 */
@Entity
@Table(name = "encadrants_entreprise")
@PrimaryKeyJoinColumn(name = "user_id")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class EncadrantEntreprise extends User {

    private String matricule;

    private Boolean disponibilite;

    private String domaine;

    private String departement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entreprise_id")
    private Entreprise entreprise;
}
