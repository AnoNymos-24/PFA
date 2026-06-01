package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Candidature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface CandidatureRepository extends JpaRepository<Candidature, Long> {
    List<Candidature> findByEtudiantId(Long etudiantId);
    List<Candidature> findByOffreId(Long offreId);
    boolean existsByEtudiantIdAndOffreId(Long etudiantId, Long offreId);
    long countByStatut(Candidature.Statut statut);

    @Query("SELECT COUNT(c) FROM Candidature c WHERE c.dateCandidature >= :since")
    long countSince(@Param("since") LocalDate since);
}
