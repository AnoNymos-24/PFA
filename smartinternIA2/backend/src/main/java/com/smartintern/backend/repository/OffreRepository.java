/* package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Offre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OffreRepository extends JpaRepository<Offre, Long> {
    List<Offre> findByUserId(Long userId);
    List<Offre> findByStatut(Offre.Statut statut);

    @Query("SELECT o FROM Offre o WHERE o.statut = 'ACTIVE' AND " +
           "(:domaine IS NULL OR o.domaine LIKE %:domaine%) AND " +
           "(:ville IS NULL OR o.ville LIKE %:ville%)")
    List<Offre> searchOffres(@Param("domaine") String domaine,
                              @Param("ville") String ville);
} */