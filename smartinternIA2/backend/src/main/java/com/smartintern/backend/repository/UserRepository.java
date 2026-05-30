package com.smartintern.backend.repository;

import com.smartintern.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    long countByRole(User.Role role);

    /**
     * Met à jour uniquement la date de dernière connexion — évite le merge complet
     * de l'entité User (problème avec JOINED inheritance pour les ADMIN sans sous-entité).
     */
    @Modifying
    @Query("UPDATE User u SET u.derniereConnexion = :date WHERE u.id = :id")
    void updateDerniereConnexion(@Param("id") Long id, @Param("date") LocalDateTime date);
}