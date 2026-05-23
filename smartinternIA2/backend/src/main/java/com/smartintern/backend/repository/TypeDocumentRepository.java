package com.smartintern.backend.repository;

import com.smartintern.backend.entity.TypeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TypeDocumentRepository extends JpaRepository<TypeDocument, Long> {
    Optional<TypeDocument> findByCode(String code);
    Optional<TypeDocument> findByNom(String nom);
    boolean existsByCode(String code);
}