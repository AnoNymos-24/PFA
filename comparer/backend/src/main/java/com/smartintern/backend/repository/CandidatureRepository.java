package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Candidature;
import com.smartintern.backend.entity.Offre;
import com.smartintern.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CandidatureRepository extends JpaRepository<Candidature, Long> {
    List<Candidature> findByEtudiantOrderByDatePostulationDesc(User etudiant);
    List<Candidature> findByOffre_EntrepriseOrderByDatePostulationDesc(User entreprise);
    List<Candidature> findByOffre(Offre offre);
    Optional<Candidature> findByEtudiantAndOffre(User etudiant, Offre offre);
    boolean existsByEtudiantAndOffre(User etudiant, Offre offre);
    // ✅ Plus rien d'autre
}