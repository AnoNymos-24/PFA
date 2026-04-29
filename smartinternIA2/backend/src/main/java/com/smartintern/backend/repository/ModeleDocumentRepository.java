package com.smartintern.backend.repository;

import com.smartintern.backend.entity.ModeleDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ModeleDocumentRepository extends JpaRepository<ModeleDocument, Long> {
    List<ModeleDocument> findByTypeDocumentId(Long typeDocumentId);
    List<ModeleDocument> findByTypeDocumentCode(String code);
    List<ModeleDocument> findByStatut(ModeleDocument.Statut statut);
}