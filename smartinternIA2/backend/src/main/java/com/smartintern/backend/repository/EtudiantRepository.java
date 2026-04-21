package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Etudiant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EtudiantRepository extends JpaRepository<Etudiant, Long> {
    Optional<Etudiant> findByEmail(String email);
    Optional<Etudiant> findByCodeEtudiant(String codeEtudiant);
    boolean existsByEmail(String email);
}
