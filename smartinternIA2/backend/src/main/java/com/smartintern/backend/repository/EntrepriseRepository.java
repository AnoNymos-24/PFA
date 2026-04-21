package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Entreprise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EntrepriseRepository extends JpaRepository<Entreprise, Long> {
    Optional<Entreprise> findByIdentifiant(String identifiant);
    boolean existsByIdentifiant(String identifiant);
}
