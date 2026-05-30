package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Tache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TacheRepository extends JpaRepository<Tache, Long> {

    /** Tâches actives d'un sprint, triées par date de début prévue */
    List<Tache> findBySprintIdAndDeletedAtIsNullOrderByDateDebutPrevueAsc(Long sprintId);

    /** Une tâche active par son id */
    Optional<Tache> findByIdAndDeletedAtIsNull(Long id);

    /** Nombre de tâches actives d'un sprint dans un statut donné */
    long countBySprintIdAndStatutAndDeletedAtIsNull(Long sprintId, Tache.Statut statut);

    /** Toutes les tâches actives d'un sprint (tous statuts) */
    long countBySprintIdAndDeletedAtIsNull(Long sprintId);

    /** Tâches actives d'un sprint dans un statut donné — liste complète (ex : cascade soft-delete) */
    List<Tache> findBySprintIdAndStatutAndDeletedAtIsNull(Long sprintId, Tache.Statut statut);

    /** Tâches actives d'un sprint exclues d'un statut — pour cascade soft-delete des non-VALIDEE */
    List<Tache> findBySprintIdAndStatutNotAndDeletedAtIsNull(Long sprintId, Tache.Statut statut);

    /** Tâches actives dans une liste de statuts — pour clôture (VALIDEE + REFUSEE) */
    long countBySprintIdAndStatutInAndDeletedAtIsNull(Long sprintId,
            java.util.Collection<Tache.Statut> statuts);
}
