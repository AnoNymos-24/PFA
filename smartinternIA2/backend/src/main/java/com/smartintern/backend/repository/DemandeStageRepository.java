package com.smartintern.backend.repository;

import com.smartintern.backend.entity.DemandeStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DemandeStageRepository extends JpaRepository<DemandeStage, Long> {
    List<DemandeStage> findByEtudiantId(Long etudiantId);
    List<DemandeStage> findByEntrepriseId(Long entrepriseId);
    List<DemandeStage> findByStatut(DemandeStage.Statut statut);
}
