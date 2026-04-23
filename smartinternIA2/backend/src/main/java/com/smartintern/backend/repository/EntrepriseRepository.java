package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Entreprise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EntrepriseRepository extends JpaRepository<Entreprise, Long> {

    Optional<Entreprise> findByNom(String nom);

    Optional<Entreprise> findByIdentifiant(String identifiant);

    boolean existsByIdentifiant(String identifiant);

    /**
     * Trouve l'entreprise liée à un utilisateur via ses offres créées.
     * Utilisé dans OffreStageService pour associer un créateur à son entreprise.
     */
    @Query("SELECT o.entreprise FROM OffreStage o WHERE o.createur.email = :email")
    Optional<Entreprise> findByAdminEmail(@Param("email") String email);
}