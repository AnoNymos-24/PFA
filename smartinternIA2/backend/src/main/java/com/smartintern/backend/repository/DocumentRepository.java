package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByDocUuid(String docUuid);

    List<Document> findByUserId(Long userId);

    List<Document> findByUserIdAndTypeDocument(Long userId, String typeDocument);

    List<Document> findByModeleDocumentId(Long modeleId);

    /**
     * Vérifie si un utilisateur possède déjà un document VALIDE du même type.
     * Utilisé pour empêcher les étudiants de générer deux fois le même document.
     */
    @Query("SELECT COUNT(d) > 0 FROM Document d " +
           "WHERE d.user.id = :userId " +
           "AND d.typeDocument = :typeDocument " +
           "AND d.statut = com.smartintern.backend.entity.Document.Statut.VALIDE")
    boolean existsDocumentValideByUserIdAndTypeDocument(
            @Param("userId") Long userId,
            @Param("typeDocument") String typeDocument);
}
