package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * Diagramme : DemandeStage
 * Envoyée par un Etudiant, reçue par une Entreprise.
 */
@Entity
@Table(name = "demandes_stage")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandeStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_demande")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Statut statut = Statut.EN_ATTENTE;

    @Column(name = "date_demande")
    private LocalDate dateDemande;

    @Column(name = "lettre_motivation", columnDefinition = "TEXT")
    private String lettreMotivation;

    // Diagramme : type_demande (ex: spontanée, sur offre)
    @Column(name = "type_demande")
    private String typeDemande;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etudiant_id", nullable = false)
    private Etudiant etudiant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entreprise_id", nullable = false)
    private Entreprise entreprise;

    public enum Statut {
        EN_ATTENTE, ACCEPTEE, REFUSEE
    }
}
