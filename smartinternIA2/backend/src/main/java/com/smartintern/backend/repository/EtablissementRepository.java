package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Etablissement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EtablissementRepository extends JpaRepository<Etablissement, Long> {
    Optional<Etablissement> findByIdentifiant(String identifiant);
}
