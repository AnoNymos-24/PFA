package com.smartintern.backend.repository;

import com.smartintern.backend.entity.SessionConnexion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionConnexionRepository extends JpaRepository<SessionConnexion, Long> {

    /** Sessions d'un utilisateur dans un statut donné (ex : ACTIVE) */
    List<SessionConnexion> findByUserIdAndStatut(Long userId, SessionConnexion.Statut statut);

    /** Dernière session enregistrée pour un utilisateur */
    Optional<SessionConnexion> findFirstByUserIdOrderByDateConnexionDesc(Long userId);

    /** Toutes les sessions d'un utilisateur — liste simple (ex : scheduler) */
    List<SessionConnexion> findByUserIdOrderByDateConnexionDesc(Long userId);

    /** Toutes les sessions d'un utilisateur — paginée (admin) */
    Page<SessionConnexion> findByUserIdOrderByDateConnexionDesc(Long userId, Pageable pageable);

    /** Retrouver une session par le hash du JWT (fermeture) */
    Optional<SessionConnexion> findByTokenHash(String tokenHash);

    /** Toutes les sessions actives — liste (admin dashboard) */
    List<SessionConnexion> findByStatut(SessionConnexion.Statut statut);
}
