package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Diagramme : EncadrantAcademique
 * Attributs : disponibilite, domaine
 * Appartient à un Etablissement
 */
@Entity
@Table(name = "encadrants_academiques")
@PrimaryKeyJoinColumn(name = "user_id")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class EncadrantAcademique extends User {

    private Boolean disponibilite;

    private String domaine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etablissement_id")
    private Etablissement etablissement;
}
