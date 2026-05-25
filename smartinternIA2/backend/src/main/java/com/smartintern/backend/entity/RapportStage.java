package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "rapports_stage")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RapportStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private Stage stage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etudiant_id", nullable = false)
    private Etudiant etudiant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeRapport type;

    private String titre;

    @Column(columnDefinition = "TEXT")
    private String contenu;

    // Semaine concernée (pour les rapports hebdomadaires)
    @Column(name = "semaine_debut")
    private LocalDate semaineDebut;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Statut statut = Statut.BROUILLON;

    @Column(name = "commentaire_encadrant", columnDefinition = "TEXT")
    private String commentaireEncadrant;

    @Column(name = "date_creation")
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();

    @Column(name = "date_soumission")
    private LocalDateTime dateSoumission;

    public enum TypeRapport {
        HEBDOMADAIRE, FINAL
    }

    public enum Statut {
        BROUILLON, SOUMIS, VALIDE, REJETE
    }
}
