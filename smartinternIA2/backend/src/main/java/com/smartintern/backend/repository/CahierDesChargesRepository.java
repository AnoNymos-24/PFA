package com.smartintern.backend.repository;

import com.smartintern.backend.entity.CahierDesCharges;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CahierDesChargesRepository extends JpaRepository<CahierDesCharges, Long> {

    Optional<CahierDesCharges> findByStageId(Long stageId);

    Optional<CahierDesCharges> findByDocumentId(Long documentId);
}
