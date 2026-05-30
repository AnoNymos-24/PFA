package com.smartintern.backend.repository;

import com.smartintern.backend.entity.CompteRendu;
import com.smartintern.backend.entity.Mission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CompteRenduRepository extends JpaRepository<CompteRendu, Long> {
    List<CompteRendu> findByMission(Mission mission);
    List<CompteRendu> findByEtudiantId(Long etudiantId);
    List<CompteRendu> findByMissionStageEncadrantEntrepriseId(Long encadrantId);
    List<CompteRendu> findByStatut(CompteRendu.CompteRenduStatut statut);
}