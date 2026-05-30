package com.smartintern.backend.repository;

import com.smartintern.backend.entity.LogActivite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface LogActiviteRepository extends JpaRepository<LogActivite, Long> {

    /** Historique paginé d'un utilisateur, du plus récent au plus ancien */
    Page<LogActivite> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Logs liés à une entité métier précise (ex : sprint id=42) */
    Page<LogActivite> findByEntiteCibleAndEntiteId(String entiteCible, Long entiteId, Pageable pageable);

    /** Logs liés à une entité (toutes instances) */
    Page<LogActivite> findByEntiteCible(String entiteCible, Pageable pageable);

    /**
     * Nombre de connexions d'un utilisateur dans une plage horaire.
     * Utilisé par RisqueStageService pour calculer la fréquence de connexion.
     */
    long countByUserIdAndActionAndCreatedAtBetween(
            Long userId,
            LogActivite.TypeAction action,
            LocalDateTime debut,
            LocalDateTime fin);
}
