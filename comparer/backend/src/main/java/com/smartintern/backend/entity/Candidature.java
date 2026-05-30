package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "candidatures")
@Data
public class Candidature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "etudiant_id", nullable = false)
    private User etudiant;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "offre_id", nullable = false)
    private Offre offre;

    @Enumerated(EnumType.STRING)
    private StatutCandidature statut = StatutCandidature.EN_ATTENTE;

       public enum StatutCandidature {
        EN_ATTENTE,   // Candidature soumise, pas encore traitée
        EN_EXAMEN,    // L'entreprise examine le dossier
        ENTRETIEN,    // Étudiant convoqué en entretien  ← AJOUTÉ
        ACCEPTEE,     // Candidature acceptée → crée un Stage
        REFUSEE       // Candidature refusée
    }


        private LocalDateTime datePostulation = LocalDateTime.now();
    private String messageMotivation;
    private String commentaireEntreprise;
 
    // Date de l'entretien (optionnel, utilisé quand statut = ENTRETIEN)
    private LocalDateTime dateEntretien;
    private String lieuEntretien;

}