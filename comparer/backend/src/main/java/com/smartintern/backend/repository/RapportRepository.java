package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Rapport;
import com.smartintern.backend.entity.Stage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface RapportRepository extends JpaRepository<Rapport, Long> {
    List<Rapport> findByStageOrderByDateDepotDesc(Stage stage);

    @Query("SELECT r FROM Rapport r WHERE r.stage.candidature.offre.entreprise.email = :email ORDER BY r.dateDepot DESC")
    List<Rapport> findByEntrepriseEmail(@Param("email") String email);

    @Query("SELECT r FROM Rapport r WHERE r.stage.encadrantAcademique.email = :email ORDER BY r.dateDepot DESC")
    List<Rapport> findByEncadrantAcademiqueEmail(@Param("email") String email);
    
    // Dans RapportRepository.java
@Query("SELECT r FROM Rapport r WHERE r.stage.candidature.etudiant.email = :email ORDER BY r.dateDepot DESC")
List<Rapport> findAllByStage_Candidature_Etudiant_Email(@Param("email") String email);

List<Rapport> findByStageId(Long stageId);
}