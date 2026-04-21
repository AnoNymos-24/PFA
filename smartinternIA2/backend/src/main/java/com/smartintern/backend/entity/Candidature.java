package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * Diagramme : Candidature
 * Lien entre un Etudiant et une OffreStage.
 * Peut "devenir" un Stage (statut ACCEPTEE).
 */
@Entity
@Table(name = "candidatures")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candidature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_candidature")
    private Long id;

    // Diagramme : statut (EN_ATTENTE, ACCEPTEE, REFUSEE, ANNULEE)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Statut statut = Statut.EN_ATTENTE;

    // Diagramme : statut_label (libellé affiché à l'étudiant)
    @Column(name = "statut_label")
    private String statutLabel;

    @Column(name = "date_candidature")
    private LocalDate dateCandidature;

    @Column(name = "date_reponse")
    private LocalDate dateReponse;

    @Column(name = "lettre_motivation", columnDefinition = "TEXT")
    private String lettreMotivation;

    // Diagramme : url_demande_stage (lien vers le document de demande)
    @Column(name = "url_demande_stage")
    private String urlDemandeStage;

    // Diagramme : score_matching (calculé par l'IA)
    @Column(name = "score_matching")
    private Float scoreMatching;

    @Column(name = "commentaire_entreprise", columnDefinition = "TEXT")
    private String commentaireEntreprise;

    @Column(name = "commentaire_administration", columnDefinition = "TEXT")
    private String commentaireAdministration;

    // Relations
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etudiant_id", nullable = false)
    private Etudiant etudiant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offre_id", nullable = false)
    private OffreStage offre;

    // Relation : Candidature peut devenir un Stage (0..1)
    @OneToOne(mappedBy = "candidature", fetch = FetchType.LAZY)
    private Stage stage;

    public enum Statut {
        EN_ATTENTE, ACCEPTEE, REFUSEE, ANNULEE
    }
}
