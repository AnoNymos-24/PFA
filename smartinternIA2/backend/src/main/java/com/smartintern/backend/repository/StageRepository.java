package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Stage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StageRepository extends JpaRepository<Stage, Long> {
    List<Stage> findByEtudiantId(Long etudiantId);
    List<Stage> findByEntrepriseId(Long entrepriseId);
    List<Stage> findByEncadrantAcademiqueId(Long encadrantId);
    List<Stage> findByEncadrantEntrepriseId(Long encadrantId);
    Optional<Stage> findByCandidatureId(Long candidatureId);
}
