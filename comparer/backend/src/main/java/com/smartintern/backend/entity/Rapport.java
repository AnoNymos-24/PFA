package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "rapports")
@Data
public class Rapport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "stage_id", nullable = false)
    private Stage stage;

    @Enumerated(EnumType.STRING)
    private TypeRapport type = TypeRapport.HEBDOMADAIRE;

    public enum TypeRapport {
        HEBDOMADAIRE, FINAL
    }

    @Enumerated(EnumType.STRING)
    private StatutRapport statut = StatutRapport.EN_ATTENTE;

    public enum StatutRapport {
        EN_ATTENTE, VALIDE, REJETE
    }

    private Integer semaine;
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String contenu;

    private String commentaireEncadrantEntreprise;
    private String commentaireEncadrantAcademique;

    private LocalDateTime dateDepot = LocalDateTime.now();
    private LocalDateTime dateValidation;
}