package com.smartintern.backend.repository;

import com.smartintern.backend.entity.EncadrantEntreprise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EncadrantEntrepriseRepository extends JpaRepository<EncadrantEntreprise, Long> {
    List<EncadrantEntreprise> findByEntrepriseId(Long entrepriseId);
}
