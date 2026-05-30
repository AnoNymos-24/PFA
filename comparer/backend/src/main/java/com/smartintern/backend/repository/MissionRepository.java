package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Mission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MissionRepository extends JpaRepository<Mission, Long> {
    
    List<Mission> findByStageId(Long stageId);
    
    // ✅ VERSION NATIVE SQL (celle qui fonctionne)
    @Query(value = "SELECT m.* FROM missions m " +
           "INNER JOIN stages s ON m.stage_id = s.id " +
           "INNER JOIN candidatures c ON s.candidature_id = c.id " +
           "WHERE c.etudiant_id = :etudiantId", nativeQuery = true)
    List<Mission> findByEtudiantId(@Param("etudiantId") Long etudiantId);
    
    @Query("SELECT m FROM Mission m WHERE m.stage.encadrantEntreprise.id = :encadrantId")
    List<Mission> findByEncadrantEntrepriseId(@Param("encadrantId") Long encadrantId);
    
    @Query("SELECT m FROM Mission m WHERE m.stage.encadrantAcademique.id = :encadrantId")
    List<Mission> findByEncadrantAcademiqueId(@Param("encadrantId") Long encadrantId);
    
    // ✅ Méthode pour charger un stage avec ses missions ET compteRendus
    @Query("SELECT m FROM Mission m LEFT JOIN FETCH m.compteRendus WHERE m.stage.id = :stageId")
    List<Mission> findByStageIdWithRendus(@Param("stageId") Long stageId);
}