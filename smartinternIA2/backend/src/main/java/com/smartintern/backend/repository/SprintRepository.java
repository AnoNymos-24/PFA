package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Sprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SprintRepository extends JpaRepository<Sprint, Long> {

    /** Tous les sprints actifs d'un stage, triés par numéro croissant */
    List<Sprint> findByStageIdAndDeletedAtIsNullOrderByNumeroAsc(Long stageId);

    /** Un sprint actif par son id */
    Optional<Sprint> findByIdAndDeletedAtIsNull(Long id);

    /** Vérifie l'unicité applicative (stage_id, numero) parmi non supprimés */
    boolean existsByStageIdAndNumeroAndDeletedAtIsNull(Long stageId, Integer numero);
}
