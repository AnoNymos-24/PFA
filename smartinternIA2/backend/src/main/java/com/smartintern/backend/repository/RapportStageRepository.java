package com.smartintern.backend.repository;

import com.smartintern.backend.entity.RapportStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RapportStageRepository extends JpaRepository<RapportStage, Long> {
    List<RapportStage> findByEtudiantIdOrderByDateCreationDesc(Long etudiantId);
    List<RapportStage> findByStageIdOrderByDateCreationDesc(Long stageId);
    List<RapportStage> findByStageIdAndType(Long stageId, RapportStage.TypeRapport type);
}
