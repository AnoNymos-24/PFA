package com.smartintern.backend.repository;

import com.smartintern.backend.entity.ResultatRisque;
import com.smartintern.backend.entity.Stage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResultatRisqueRepository extends JpaRepository<ResultatRisque, Long> {

    /**
     * Dernier résultat disponible pour un stage (un seul résultat par stage).
     */
    Optional<ResultatRisque> findByStage(Stage stage);

    /**
     * Tous les stages ayant un résultat d'un niveau de risque donné.
     */
    List<ResultatRisque> findByNiveauRisque(ResultatRisque.NiveauRisque niveauRisque);

    /**
     * Stages dont le score d'engagement est inférieur à un seuil (monitoring).
     */
    List<ResultatRisque> findByScoreEngagementLessThan(int seuil);

    /**
     * Vrai si un résultat existe déjà pour ce stage (pour décider upsert vs insert).
     */
    boolean existsByStage(Stage stage);

    /**
     * Résultats ordonnés par score croissant (les plus à risque en premier).
     */
    List<ResultatRisque> findAllByOrderByScoreEngagementAsc();
}
