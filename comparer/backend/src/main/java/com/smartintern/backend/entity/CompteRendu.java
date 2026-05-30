package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "comptes_rendus")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompteRendu {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(length = 5000)
    private String contenu;
    
    @Column(name = "commentaire_etudiant", length = 1000)
    private String commentaireEtudiant;
    
    @Column(name = "commentaire_encadrant", length = 1000)
    private String commentaireEncadrant;
    
    @Column(name = "date_soumission")
    private LocalDateTime dateSoumission;
    
    @Column(name = "date_validation")
    private LocalDateTime dateValidation;
    
    @Column(name = "statut")
    @Enumerated(EnumType.STRING)
    private CompteRenduStatut statut;
    
    @ManyToOne
    @JoinColumn(name = "mission_id")
    @JsonIgnore  // ✅ évite la boucle Mission → CompteRendu → Mission
    private Mission mission;
    
    @ManyToOne
    @JoinColumn(name = "etudiant_id")
    @JsonIgnore  // ✅ évite de sérialiser tout l'objet User
    private User etudiant;
    
    public enum CompteRenduStatut {
        BROUILLON,
        EN_ATTENTE,
        VALIDE,
        REJETE
    }
}