package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "missions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Mission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String titre;
    
    @Column(length = 2000)
    private String description;
    
    @Column(length = 1000)
    private String objectifs;
    
    @Column(name = "date_creation")
    private LocalDateTime dateCreation;
    
    @Column(name = "date_echeance")
    private LocalDateTime dateEcheance;
    
    @Column(name = "statut")
    @Enumerated(EnumType.STRING)
    private MissionStatut statut;
    
    @ManyToOne
    @JoinColumn(name = "stage_id")
    @JsonIgnore  // ✅ ICI — à l'intérieur de la classe, juste au-dessus du champ
    private Stage stage;
    
    @OneToMany(mappedBy = "mission", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<CompteRendu> compteRendus = new ArrayList<>();
    
    public enum MissionStatut {
        A_FAIRE,
        EN_COURS,
        TERMINEE,
        ANNULEE
    }
}