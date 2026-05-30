package com.smartintern.backend.service;

import com.smartintern.backend.dto.TacheDto;
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

/**
 * Service de gestion des Tâches.
 *
 * <h3>Machine à états</h3>
 * <pre>
 * A_FAIRE ──[étudiant: demarrer]──→ EN_COURS
 *                                       │
 *                         [étudiant: marquerTerminee]
 *                                       ↓
 *                           TERMINEE_PAR_ETUDIANT
 *                             ╱               ╲
 *          [encadrant: valider]             [encadrant: refuser]
 *                 ↓                                 ↓
 *              VALIDEE                           REFUSEE
 *                                                   │
 *                                      [étudiant: reprendre]
 *                                                   ↓
 *                                               EN_COURS
 * </pre>
 *
 * Toute transition invalide → {@link IllegalStateException}.
 *
 * <h3>Autorisations</h3>
 * <ul>
 *   <li>Création / modification structure / suppression / valider / refuser : encadrant entreprise du stage.</li>
 *   <li>demarrer / marquerTerminee / reprendre : étudiant du stage.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TacheService {

    private final TacheRepository    tacheRepository;
    private final SprintRepository   sprintRepository;
    private final LogActiviteService logActiviteService;
    private final SprintService      sprintService;

    // ── Création ──────────────────────────────────────────────────────────────

    @Transactional
    public Tache creer(Long sprintId,
                       TacheDto.CreationRequest dto,
                       User encadrant,
                       HttpServletRequest request) {

        Sprint sprint = trouverSprint(sprintId);
        verifierEncadrant(sprint.getStage(), encadrant);

        if (sprint.getStatut() == Sprint.Statut.CLOTURE) {
            throw new IllegalStateException("Impossible d'ajouter une tâche à un sprint clôturé");
        }

        Tache tache = Tache.builder()
                .sprint(sprint)
                .titre(dto.getTitre())
                .description(dto.getDescription())
                .dateDebutPrevue(dto.getDateDebutPrevue())
                .dateFinPrevue(dto.getDateFinPrevue())
                .statut(Tache.Statut.A_FAIRE)
                .decisionValidation(Tache.DecisionValidation.NON_TRAITE)
                .genereParIa(false)
                .build();

        Tache saved = tacheRepository.save(tache);

        logActiviteService.loguer(encadrant, TypeAction.CREATION_TACHE,
                "taches", saved.getId(),
                "Création tâche : " + dto.getTitre() + " (sprint " + sprintId + ")", request);

        log.info("Tâche créée — sprint={}, tache={}", sprintId, saved.getId());
        return saved;
    }

    // ── Modification ──────────────────────────────────────────────────────────

    /**
     * Modifie les champs autorisés selon l'état de la tâche.
     *
     * <pre>
     * A_FAIRE                     : tous les champs.
     * EN_COURS / TERMINEE         : description + dateFinPrevue seulement (par l'encadrant).
     * VALIDEE                     : aucune modification.
     * REFUSEE                     : observationEncadrant seulement.
     * </pre>
     */
    @Transactional
    public Tache modifier(Long idTache,
                          TacheDto.ModificationRequest dto,
                          User encadrant,
                          HttpServletRequest request) {

        Tache tache = trouverTache(idTache);
        verifierEncadrant(tache.getSprint().getStage(), encadrant);

        switch (tache.getStatut()) {
            case A_FAIRE -> {
                if (dto.getTitre() != null && !dto.getTitre().isBlank())
                    tache.setTitre(dto.getTitre());
                if (dto.getDescription() != null)   tache.setDescription(dto.getDescription());
                if (dto.getDateDebutPrevue() != null) tache.setDateDebutPrevue(dto.getDateDebutPrevue());
                if (dto.getDateFinPrevue() != null)   tache.setDateFinPrevue(dto.getDateFinPrevue());
                if (dto.getObservationEncadrant() != null)
                    tache.setObservationEncadrant(dto.getObservationEncadrant());
            }
            case EN_COURS, TERMINEE_PAR_ETUDIANT -> {
                // Description et dateFinPrevue seulement
                if (dto.getDescription() != null)   tache.setDescription(dto.getDescription());
                if (dto.getDateFinPrevue() != null) tache.setDateFinPrevue(dto.getDateFinPrevue());
            }
            case VALIDEE ->
                throw new IllegalStateException("Impossible de modifier une tâche VALIDÉE");
            case REFUSEE -> {
                // Observation encadrant seulement
                if (dto.getObservationEncadrant() != null)
                    tache.setObservationEncadrant(dto.getObservationEncadrant());
            }
        }

        Tache saved = tacheRepository.save(tache);
        logActiviteService.loguer(encadrant, TypeAction.MODIFICATION_TACHE,
                "taches", idTache,
                "Modification tâche : " + tache.getTitre(), request);

        return saved;
    }

    // ── Suppression (soft-delete) ─────────────────────────────────────────────

    @Transactional
    public void supprimer(Long idTache, User encadrant, HttpServletRequest request) {

        Tache tache = trouverTache(idTache);
        verifierEncadrant(tache.getSprint().getStage(), encadrant);

        if (tache.getStatut() == Tache.Statut.VALIDEE) {
            throw new IllegalStateException("Impossible de supprimer une tâche VALIDÉE");
        }
        if (tache.getStatut() == Tache.Statut.TERMINEE_PAR_ETUDIANT) {
            throw new IllegalStateException(
                    "Impossible de supprimer une tâche en attente de validation (TERMINEE_PAR_ETUDIANT)");
        }

        tache.setDeletedAt(LocalDateTime.now());
        tache.setDeletedByUserId(encadrant.getId());
        tacheRepository.save(tache);

        logActiviteService.loguer(encadrant, TypeAction.SUPPRESSION_TACHE,
                "taches", idTache,
                "Suppression tâche : " + tache.getTitre(), request);

        log.info("Tâche supprimée (soft) — tache={}", idTache);
    }

    // ── Transitions étudiant ──────────────────────────────────────────────────

    /**
     * L'étudiant démarre une tâche (A_FAIRE → EN_COURS).
     * Déclenche aussi la transition du sprint en EN_COURS si celui-ci est encore PLANIFIE.
     */
    @Transactional
    public Tache demarrer(Long idTache, User etudiant, HttpServletRequest request) {

        Tache tache = trouverTache(idTache);
        verifierEtudiant(tache.getSprint().getStage(), etudiant);

        if (tache.getStatut() != Tache.Statut.A_FAIRE) {
            throw new IllegalStateException(
                    "Transition " + tache.getStatut() + " → EN_COURS interdite. "
                    + "Une tâche ne peut être démarrée que depuis A_FAIRE.");
        }

        tache.setStatut(Tache.Statut.EN_COURS);
        Tache saved = tacheRepository.save(tache);

        // Transition automatique du sprint en EN_COURS
        sprintService.transitionnerEnCours(tache.getSprint());

        log.info("Tâche démarrée — tache={}, etudiant={}", idTache, etudiant.getId());
        return saved;
    }

    /**
     * L'étudiant marque la tâche comme terminée (EN_COURS → TERMINEE_PAR_ETUDIANT).
     */
    @Transactional
    public Tache marquerTerminee(Long idTache,
                                  String noteEtudiant,
                                  User etudiant,
                                  HttpServletRequest request) {

        Tache tache = trouverTache(idTache);
        verifierEtudiant(tache.getSprint().getStage(), etudiant);

        if (tache.getStatut() != Tache.Statut.EN_COURS) {
            throw new IllegalStateException(
                    "Transition " + tache.getStatut() + " → TERMINEE_PAR_ETUDIANT interdite. "
                    + "La tâche doit être EN_COURS.");
        }

        tache.setStatut(Tache.Statut.TERMINEE_PAR_ETUDIANT);
        tache.setDateRealisationEtudiant(LocalDateTime.now());
        if (noteEtudiant != null && !noteEtudiant.isBlank()) {
            tache.setNoteEtudiant(noteEtudiant);
        }
        Tache saved = tacheRepository.save(tache);

        log.info("Tâche marquée terminée — tache={}, etudiant={}", idTache, etudiant.getId());
        return saved;
    }

    /**
     * L'étudiant reprend une tâche refusée (REFUSEE → EN_COURS).
     */
    @Transactional
    public Tache reprendre(Long idTache, User etudiant, HttpServletRequest request) {

        Tache tache = trouverTache(idTache);
        verifierEtudiant(tache.getSprint().getStage(), etudiant);

        if (tache.getStatut() != Tache.Statut.REFUSEE) {
            throw new IllegalStateException(
                    "Transition " + tache.getStatut() + " → EN_COURS interdite. "
                    + "La reprise n'est possible que depuis REFUSEE.");
        }

        tache.setStatut(Tache.Statut.EN_COURS);
        tache.setDecisionValidation(Tache.DecisionValidation.NON_TRAITE);
        Tache saved = tacheRepository.save(tache);

        log.info("Tâche reprise — tache={}, etudiant={}", idTache, etudiant.getId());
        return saved;
    }

    // ── Transitions encadrant ─────────────────────────────────────────────────

    /**
     * L'encadrant valide une tâche (TERMINEE_PAR_ETUDIANT → VALIDEE).
     */
    @Transactional
    public Tache valider(Long idTache,
                         TacheDto.ValidationRequest dto,
                         User encadrant,
                         HttpServletRequest request) {

        Tache tache = trouverTache(idTache);
        verifierEncadrant(tache.getSprint().getStage(), encadrant);

        if (tache.getStatut() != Tache.Statut.TERMINEE_PAR_ETUDIANT) {
            throw new IllegalStateException(
                    "Transition " + tache.getStatut() + " → VALIDEE interdite. "
                    + "La validation n'est possible que depuis TERMINEE_PAR_ETUDIANT.");
        }

        tache.setStatut(Tache.Statut.VALIDEE);
        tache.setDecisionValidation(Tache.DecisionValidation.VALIDEE);
        tache.setDateTraitementEncadrant(LocalDateTime.now());
        if (dto != null && dto.getObservation() != null)
            tache.setObservationEncadrant(dto.getObservation());
        if (dto != null && dto.getNote() != null)
            tache.setNote(dto.getNote());

        Tache saved = tacheRepository.save(tache);

        logActiviteService.loguer(encadrant, TypeAction.VALIDATION_TACHE,
                "taches", idTache,
                "Validation tâche : " + tache.getTitre(), request);

        log.info("Tâche validée — tache={}, encadrant={}", idTache, encadrant.getId());
        return saved;
    }

    /**
     * L'encadrant refuse une tâche (TERMINEE_PAR_ETUDIANT → REFUSEE).
     * L'observation est obligatoire.
     */
    @Transactional
    public Tache refuser(Long idTache,
                         String observation,
                         User encadrant,
                         HttpServletRequest request) {

        Tache tache = trouverTache(idTache);
        verifierEncadrant(tache.getSprint().getStage(), encadrant);

        if (tache.getStatut() != Tache.Statut.TERMINEE_PAR_ETUDIANT) {
            throw new IllegalStateException(
                    "Transition " + tache.getStatut() + " → REFUSEE interdite. "
                    + "Le refus n'est possible que depuis TERMINEE_PAR_ETUDIANT.");
        }

        if (observation == null || observation.isBlank()) {
            throw new IllegalStateException(
                    "L'observation est obligatoire pour refuser une tâche");
        }

        tache.setStatut(Tache.Statut.REFUSEE);
        tache.setDecisionValidation(Tache.DecisionValidation.REFUSEE);
        tache.setDateTraitementEncadrant(LocalDateTime.now());
        tache.setObservationEncadrant(observation);
        Tache saved = tacheRepository.save(tache);

        logActiviteService.loguer(encadrant, TypeAction.REFUS_TACHE,
                "taches", idTache,
                "Refus tâche : " + tache.getTitre() + " — " + observation, request);

        log.info("Tâche refusée — tache={}, encadrant={}", idTache, encadrant.getId());
        return saved;
    }

    // ── Consultation ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Tache> listerParSprint(Long sprintId) {
        return tacheRepository.findBySprintIdAndDeletedAtIsNullOrderByDateDebutPrevueAsc(sprintId);
    }

    @Transactional(readOnly = true)
    public Tache obtenir(Long idTache) {
        return trouverTache(idTache);
    }

    // ── Mapping DTO ───────────────────────────────────────────────────────────

    public TacheDto.Response toDto(Tache tache) {
        TacheDto.Response dto = new TacheDto.Response();
        dto.setId(tache.getId());
        dto.setSprintId(tache.getSprint() != null ? tache.getSprint().getId() : null);
        dto.setTitre(tache.getTitre());
        dto.setDescription(tache.getDescription());
        dto.setStatut(tache.getStatut() != null ? tache.getStatut().name() : null);
        dto.setDecisionValidation(tache.getDecisionValidation() != null
                ? tache.getDecisionValidation().name() : null);
        dto.setDateDebutPrevue(tache.getDateDebutPrevue());
        dto.setDateFinPrevue(tache.getDateFinPrevue());
        dto.setDateRealisationEtudiant(tache.getDateRealisationEtudiant());
        dto.setDateTraitementEncadrant(tache.getDateTraitementEncadrant());
        dto.setNoteEtudiant(tache.getNoteEtudiant());
        dto.setObservationEncadrant(tache.getObservationEncadrant());
        dto.setNote(tache.getNote());
        dto.setGenereParIa(tache.isGenereParIa());
        dto.setCreatedAt(tache.getCreatedAt());
        dto.setUpdatedAt(tache.getUpdatedAt());
        return dto;
    }

    // ── Helpers privés ────────────────────────────────────────────────────────

    private Tache trouverTache(Long idTache) {
        return tacheRepository.findByIdAndDeletedAtIsNull(idTache)
                .orElseThrow(() -> new RuntimeException("Tâche introuvable id=" + idTache));
    }

    private Sprint trouverSprint(Long sprintId) {
        return sprintRepository.findByIdAndDeletedAtIsNull(sprintId)
                .orElseThrow(() -> new RuntimeException("Sprint introuvable id=" + sprintId));
    }

    private void verifierEncadrant(Stage stage, User encadrant) {
        if (stage.getEncadrantEntreprise() == null
                || !stage.getEncadrantEntreprise().getId().equals(encadrant.getId())) {
            throw new RuntimeException(
                    "Accès refusé : vous n'êtes pas l'encadrant entreprise de ce stage");
        }
    }

    private void verifierEtudiant(Stage stage, User etudiant) {
        if (stage.getEtudiant() == null
                || !stage.getEtudiant().getId().equals(etudiant.getId())) {
            throw new RuntimeException(
                    "Accès refusé : vous n'êtes pas l'étudiant de ce stage");
        }
    }
}
