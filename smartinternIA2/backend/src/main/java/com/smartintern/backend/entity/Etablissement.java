package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Diagramme : Etablissement
 * Ex : Iteam University. Les encadrants académiques et les étudiants appartiennent à un établissement.
 */
@Entity
@Table(name = "etablissements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Etablissement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_etablissement")
    private Long id;

    @Column(nullable = false)
    private String nom;

    private String adresse;

    @Column(unique = true)
    private String identifiant;
}
