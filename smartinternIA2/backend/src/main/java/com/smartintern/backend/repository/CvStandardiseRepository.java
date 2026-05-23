package com.smartintern.backend.repository;

import com.smartintern.backend.entity.CvStandardise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CvStandardiseRepository extends JpaRepository<CvStandardise, Long> {
    Optional<CvStandardise> findByEtudiantId(Long etudiantId);
    Optional<CvStandardise> findByEtudiantEmail(String email);
    boolean existsByEtudiantId(Long etudiantId);
}