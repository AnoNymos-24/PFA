package com.smartintern.backend.service;

import com.smartintern.backend.dto.SprintDto;
import com.smartintern.backend.entity.*;
import com.smartintern.backend.entity.LogActivite.TypeAction;
import com.smartintern.backend.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Service de gestion des Sprints.
 * <p>
 * Machine à états : PLANIFIE → EN_COURS → CLOTURE
 * <p>
 * Règles de modification par état :
 * <ul>
 *   <li>PLANIFIE : tous les champs modifiables (sauf numero).</li>
 *   <li>EN_COURS : titre, description, dateDebut, dateFin seulement.</li>
 *   <li>CLOTURE : observationEncadrant seulement.</li>
 * </ul>
 * <p>
 * Préalable à la création : le CDC du stage doit exister et être en statut VALIDE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintRepository           sprintRepository;
    private final TacheRepository            tacheRepository;
    private final StageRepository            stageRepository;
    private final CahierDesChargesRepository cdcRepository;
    private final LogActiviteService         logActiviteService;

    // ── Création ──────────────────────────────────────────────────────────────

    /**
     * Crée un nouveau sprint pour un stage.
     * <p>
     * Précondition : le CDC du stage existe et est VALIDE.
     */
    @Transactional
    public Sprint creer(Long stageId,
                        SprintDto.CreationRequest dto,
                        User encadrant,
                        HttpServletRequest request) {

        Stage stage = trouverStage(stageId);
        verifierEncadrant(stage, encadrant);

        // Vérifier que le CDC est validé
        CahierDesCharges cdc = cdcRepository.findByStageId(stageId)
                .orElseThrow(() -> new IllegalStateException(
                        "Aucun cahier des charges pour ce stage. "
                        + "Uploadez et validez le CDC avant de créer des sprints."));
        if (cdc.getStatut() != CahierDesCharges.Statut.VALIDE) {
            throw new IllegalStateException(
                    "Le cahier des charges doit être VALIDE avant de créer des sprints "
                    + "(statut actuel : " + cdc.getStatut() + ")");
        }

        // Unicité applicative (stage_id, numero) parmi les non supprimés
        if (sprintRepository.existsByStageIdAndNumeroAndDeletedAtIsNull(stageId, dto.getNumero())) {
            throw new IllegalStateException(
                    "Un sprint numéro " + dto.getNumero() + " existe déjà pour ce stage");
        }

        Sprint sprint = Sprint.builder()
                .stage(stage)
                .numero(dto.getNumero())
                .titre(dto.getTitre())
                .description(dto.getDescription())
                .dateDebut(dto.getDateDebut())
                .dateFin(dto.getDateFin())
                .statut(Sprint.Statut.PLANIFIE)
                .build();

        Sprint saved = sprintRepository.save(sprint);

        logActiviteService.loguer(encadrant, TypeAction.CREATION_SPRINT,
                "sprints", saved.getId(),
                "Création sprint #" + dto.getNumero() + " : " + dto.getTitre(), request);

        log.info("Sprint créé — stage={}, sprint={}, num={}", stageId, saved.getId(), dto.getNumero());
        return saved;
    }

    // ── Modification ──────────────────────────────────────────────────────────

    /**
     * Modifie les champs autorisés selon l'état du sprint.
     *
     * <pre>
     * PLANIFIE  : tous les champs (sauf numero).
     * EN_COURS  : titre, description, dateDebut, dateFin.
     * CLOTURE   : observationEncadrant.
     * </pre>
     */
    @Transactional
    public Sprint modifier(Long idSprint,
                           SprintDto.ModificationRequest dto,
                           User encadrant,
                           HttpServletRequest request) {

        Sprint sprint = trouverSprint(idSprint);
        verifierEncadrant(sprint.getStage(), encadrant);

        Sprint.Statut statut = sprint.getStatut();

        switch (statut) {
            case PLANIFIE -> {
                // Tous les champs modifiables sauf numero
                if (dto.getTitre() != null && !dto.getTitre().isBlank())
                    sprint.setTitre(dto.getTitre());
                if (dto.getDescription() != null) sprint.setDescription(dto.getDescription());
                if (dto.getDateDebut() != null)   sprint.setDateDebut(dto.getDateDebut());
                if (dto.getDateFin() != null)     sprint.setDateFin(dto.getDateFin());
                if (dto.getObservationEncadrant() != null)
                    sprint.setObservationEncadrant(dto.getObservationEncadrant());
            }
            case EN_COURS -> {
                // Titre, description, dates seulement
                if (dto.getTitre() != null && !dto.getTitre().isBlank())
                    sprint.setTitre(dto.getTitre());
                if (dto.getDescription() != null) sprint.setDescription(dto.getDescription());
                if (dto.getDateDebut() != null)   sprint.setDateDebut(dto.getDateDebut());
                if (dto.getDateFin() != null)     sprint.setDateFin(dto.getDateFin());
            }
            case CLOTURE -> {
                // Observation seulement
                if (dto.getObservationEncadrant() != null)
                    sprint.setObservationEncadrant(dto.getObservationEncadrant());
            }
        }

        Sprint saved = sprintRepository.save(sprint);
        logActiviteService.loguer(encadrant, TypeAction.MODIFICATION_SPRINT,
                "sprints", idSprint,
                "Modification sprint #" + sprint.getNumero(), request);

        return saved;
    }

    // ── Suppression (soft-delete) ─────────────────────────────────────────────

    /**
     * Soft-delete du sprint.
     * <p>
     * Interdit si au moins une tâche est en statut VALIDEE (non supprimée).
     * Les tâches non-VALIDEE sont soft-deletées en cascade.
     */
    @Transactional
    public void supprimer(Long idSprint, User encadrant, HttpServletRequest request) {

        Sprint sprint = trouverSprint(idSprint);
        verifierEncadrant(sprint.getStage(), encadrant);

        // Refuser si tâche VALIDEE présente
        long nbValidees = tacheRepository.countBySprintIdAndStatutAndDeletedAtIsNull(
                idSprint, Tache.Statut.VALIDEE);
        if (nbValidees > 0) {
            throw new IllegalStateException(
                    "Impossible de supprimer ce sprint : il contient "
                    + nbValidees + " tâche(s) VALIDÉE(S)");
        }

        // Cascade soft-delete sur les tâches non-VALIDEE (toutes les actives restantes)
        List<Tache> tachesActives = tacheRepository
                .findBySprintIdAndDeletedAtIsNullOrderByDateDebutPrevueAsc(idSprint);
        LocalDateTime maintenant = LocalDateTime.now();
        tachesActives.forEach(t -> {
            t.setDeletedAt(maintenant);
            t.setDeletedByUserId(encadrant.getId());
        });
        tacheRepository.saveAll(tachesActives);

        // Soft-delete du sprint
        sprint.setDeletedAt(maintenant);
        sprint.setDeletedByUserId(encadrant.getId());
        sprintRepository.save(sprint);

        logActiviteService.loguer(encadrant, TypeAction.SUPPRESSION_SPRINT,
                "sprints", idSprint,
                "Suppression sprint #" + sprint.getNumero(), request);

        log.info("Sprint supprimé (soft) — sprint={}", idSprint);
    }

    // ── Clôture ───────────────────────────────────────────────────────────────

    /**
     * Clôture un sprint.
     * <p>
     * Préconditions :
     * <ul>
     *   <li>Toutes les tâches non supprimées sont en VALIDEE ou REFUSEE.</li>
     *   <li>L'observation n'est pas vide.</li>
     * </ul>
     */
    @Transactional
    public Sprint cloturer(Long idSprint,
                           String observation,
                           User encadrant,
                           HttpServletRequest request) {

        Sprint sprint = trouverSprint(idSprint);
        verifierEncadrant(sprint.getStage(), encadrant);

        if (sprint.getStatut() == Sprint.Statut.CLOTURE) {
            throw new IllegalStateException("Le sprint est déjà clôturé");
        }

        // Observation obligatoire
        if (observation == null || observation.isBlank()) {
            throw new IllegalStateException(
                    "L'observation est obligatoire pour clôturer un sprint");
        }

        // Vérifier que toutes les tâches non supprimées sont VALIDEE ou REFUSEE
        long totalActives = tacheRepository.countBySprintIdAndDeletedAtIsNull(idSprint);
        long terminees = tacheRepository.countBySprintIdAndStatutInAndDeletedAtIsNull(
                idSprint, Set.of(Tache.Statut.VALIDEE, Tache.Statut.REFUSEE));

        if (totalActives != terminees) {
            long enAttente = totalActives - terminees;
            throw new IllegalStateException(
                    "Impossible de clôturer : " + enAttente
                    + " tâche(s) ne sont pas encore VALIDÉE ou REFUSÉE");
        }

        sprint.setStatut(Sprint.Statut.CLOTURE);
        sprint.setDateClotureReelle(LocalDateTime.now());
        sprint.setObservationEncadrant(observation);
        Sprint saved = sprintRepository.save(sprint);

        logActiviteService.loguer(encadrant, TypeAction.CLOTURE_SPRINT,
                "sprints", idSprint,
                "Clôture sprint #" + sprint.getNumero(), request);

        log.info("Sprint clôturé — sprint={}", idSprint);
        return saved;
    }

    // ── Consultation ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Sprint> listerParStage(Long stageId) {
        return sprintRepository.findByStageIdAndDeletedAtIsNullOrderByNumeroAsc(stageId);
    }

    @Transactional(readOnly = true)
    public Sprint obtenir(Long idSprint) {
        return trouverSprint(idSprint);
    }

    // ── Transition interne (appelée par TacheService) ─────────────────────────

    /**
     * Passe le sprint en EN_COURS si c'est la première tâche démarrée.
     * Appelé par TacheService.demarrer().
     */
    @Transactional
    public void transitionnerEnCours(Sprint sprint) {
        if (sprint.getStatut() == Sprint.Statut.PLANIFIE) {
            sprint.setStatut(Sprint.Statut.EN_COURS);
            sprintRepository.save(sprint);
            log.info("Sprint {} passé EN_COURS automatiquement", sprint.getId());
        }
    }

    // ── Mapping DTO ───────────────────────────────────────────────────────────

    public SprintDto.Response toDto(Sprint sprint) {
        SprintDto.Response dto = new SprintDto.Response();
        dto.setId(sprint.getId());
        dto.setStageId(sprint.getStage() != null ? sprint.getStage().getId() : null);
        dto.setNumero(sprint.getNumero());
        dto.setTitre(sprint.getTitre());
        dto.setDescription(sprint.getDescription());
        dto.setDateDebut(sprint.getDateDebut());
        dto.setDateFin(sprint.getDateFin());
        dto.setDateClotureReelle(sprint.getDateClotureReelle());
        dto.setObservationEncadrant(sprint.getObservationEncadrant());
        dto.setStatut(sprint.getStatut() != null ? sprint.getStatut().name() : null);
        dto.setCreatedAt(sprint.getCreatedAt());
        dto.setUpdatedAt(sprint.getUpdatedAt());
        return dto;
    }

    // ── Helpers privés ────────────────────────────────────────────────────────

    private Stage trouverStage(Long stageId) {
        return stageRepository.findById(stageId)
                .orElseThrow(() -> new RuntimeException("Stage introuvable id=" + stageId));
    }

    private Sprint trouverSprint(Long idSprint) {
        return sprintRepository.findByIdAndDeletedAtIsNull(idSprint)
                .orElseThrow(() -> new RuntimeException("Sprint introuvable id=" + idSprint));
    }

    private void verifierEncadrant(Stage stage, User encadrant) {
        if (stage.getEncadrantEntreprise() == null
                || !stage.getEncadrantEntreprise().getId().equals(encadrant.getId())) {
            throw new RuntimeException(
                    "Accès refusé : vous n'êtes pas l'encadrant entreprise de ce stage");
        }
    }
}
