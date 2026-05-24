package com.smartintern.backend.repository;

import com.smartintern.backend.entity.ModeleDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModeleDocumentRepository extends JpaRepository<ModeleDocument, Long> {

    List<ModeleDocument> findByTypeDocumentId(Long typeDocumentId);

    List<ModeleDocument> findByTypeDocumentCode(String code);

    List<ModeleDocument> findByStatut(ModeleDocument.Statut statut);

    /**
     * Trouve le modèle actif le plus récent pour un type de document donné.
     * Utilisé lors de la génération automatique après sélection du type de document.
     */
    Optional<ModeleDocument> findFirstByTypeDocumentIdAndStatutOrderByIdDesc(
            Long typeDocumentId, ModeleDocument.Statut statut);

    /**
     * Trouve tous les modèles actifs pour un type de document.
     */
    List<ModeleDocument> findByTypeDocumentIdAndStatut(
            Long typeDocumentId, ModeleDocument.Statut statut);
}
