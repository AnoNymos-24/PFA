package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Candidature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CandidatureRepository extends JpaRepository<Candidature, Long> {
    List<Candidature> findByEtudiantId(Long etudiantId);
    List<Candidature> findByOffreId(Long offreId);
    boolean existsByEtudiantIdAndOffreId(Long etudiantId, Long offreId);
}
