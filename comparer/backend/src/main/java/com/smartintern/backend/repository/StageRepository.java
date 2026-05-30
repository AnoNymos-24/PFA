package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Stage;
import com.smartintern.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface StageRepository extends JpaRepository<Stage, Long> {

    // étudiant
    List<Stage> findAllByCandidature_Etudiant_Email(String email);
    Optional<Stage> findByCandidature_Etudiant_Email(String email);

    // entreprise
    List<Stage> findByCandidature_Offre_Entreprise_Email(String email);

    // encadrants par email
    List<Stage> findByEncadrantEntreprise_Email(String email);
    List<Stage> findByEncadrantAcademique_Email(String email);
    
    // encadrants par objet User
    List<Stage> findByEncadrantEntreprise(User encadrant);
    List<Stage> findByEncadrantAcademiqueId(Long id);
    
    // encadrants par ID (une seule fois !)
    @Query("SELECT s FROM Stage s WHERE s.encadrantEntreprise.id = :encadrantId")
    List<Stage> findByEncadrantEntrepriseId(@Param("encadrantId") Long encadrantId);

    // candidature
    Optional<Stage> findByCandidature_Id(Long id);
    
    List<Stage> findByCandidatureEtudiantId(Long etudiantId);
    
    long countByStatut(Stage.StatutStage statut);  
    
        long countByConventionSigneeEntrepriseTrue();

}