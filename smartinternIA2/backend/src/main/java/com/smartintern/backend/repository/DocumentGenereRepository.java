package com.smartintern.backend.repository;

import com.smartintern.backend.entity.DocumentGenere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentGenereRepository extends JpaRepository<DocumentGenere, Long> {

    Optional<DocumentGenere> findByDocUuid(String docUuid);

    List<DocumentGenere> findByDocumentId(Long documentId);

    List<DocumentGenere> findByDocumentUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT d FROM DocumentGenere d WHERE d.expiredAt < CURRENT_TIMESTAMP AND d.statut = 'VALIDE'")
    List<DocumentGenere> findExpiredDocuments();

    @Query("SELECT d FROM DocumentGenere d WHERE d.document.user.id = :userId ORDER BY d.createdAt DESC")
    List<DocumentGenere> findHistoriqueByUserId(@Param("userId") Long userId);
}