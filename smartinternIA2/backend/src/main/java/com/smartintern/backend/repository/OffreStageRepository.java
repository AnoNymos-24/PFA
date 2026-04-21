package com.smartintern.backend.repository;

import com.smartintern.backend.entity.OffreStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OffreStageRepository extends JpaRepository<OffreStage, Long> {

    List<OffreStage> findByEntrepriseId(Long entrepriseId);
    List<OffreStage> findByStatut(OffreStage.Statut statut);
    List<OffreStage> findByStatutValidation(OffreStage.StatutValidation statutValidation);

    @Query("SELECT o FROM OffreStage o WHERE o.statut = 'ACTIVE' " +
           "AND o.statutValidation = 'VALIDEE' " +
           "AND (:domaine IS NULL OR o.domaine LIKE %:domaine%) " +
           "AND (:localisation IS NULL OR o.localisation LIKE %:localisation%) " +
           "AND (:typeStage IS NULL OR o.typeStage = :typeStage) " +
           "AND (:niveauRequis IS NULL OR o.niveauRequis = :niveauRequis)")
    List<OffreStage> searchOffres(
            @Param("domaine") String domaine,
            @Param("localisation") String localisation,
            @Param("typeStage") String typeStage,
            @Param("niveauRequis") String niveauRequis);
}
