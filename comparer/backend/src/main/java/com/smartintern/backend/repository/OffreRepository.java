package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Offre;
import com.smartintern.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OffreRepository extends JpaRepository<Offre, Long> {

    // Toutes les offres actives
    List<Offre> findByStatutOrderByCreatedAtDesc(Offre.StatutOffre statut);

    // Offres d'une entreprise
    List<Offre> findByEntrepriseOrderByCreatedAtDesc(User entreprise);

    // Recherche par mot-clé
    @Query("SELECT o FROM Offre o WHERE o.statut = 'ACTIVE' AND " +
           "(LOWER(o.titre) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(o.description) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(o.specialite) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<Offre> search(@Param("q") String query);

    // Filtres combinés
    @Query("SELECT o FROM Offre o WHERE o.statut = 'ACTIVE' " +
           "AND (:lieu IS NULL OR LOWER(o.lieu) LIKE LOWER(CONCAT('%', :lieu, '%'))) " +
           "AND (:type IS NULL OR o.type = :type) " +
           "AND (:duree IS NULL OR o.duree = :duree) " +
           "AND (:niveau IS NULL OR o.niveauRequis = :niveau)")
    List<Offre> filter(@Param("lieu") String lieu,
                       @Param("type") String type,
                       @Param("duree") String duree,
                       @Param("niveau") String niveau);
}