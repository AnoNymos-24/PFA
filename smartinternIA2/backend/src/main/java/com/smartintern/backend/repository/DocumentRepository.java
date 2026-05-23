package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    Optional<Document> findByDocUuid(String docUuid);
    List<Document> findByUserId(Long userId);
    List<Document> findByUserIdAndTypeDocument(Long userId, String typeDocument);
    List<Document> findByModeleDocumentId(Long modeleId);
}