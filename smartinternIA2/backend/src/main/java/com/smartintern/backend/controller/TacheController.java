package com.smartintern.backend.controller;

import com.smartintern.backend.dto.TacheDto;
import com.smartintern.backend.entity.Tache;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.UserRepository;
import com.smartintern.backend.service.TacheService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints Tâches.
 *
 * Côté encadrant entreprise : /api/encadrant-entreprise/taches/**
 * Côté étudiant              : /api/etudiant/taches/**
 */
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TacheController {

    private final TacheService   tacheService;
    private final UserRepository userRepository;

    // ── Encadrant — structure ─────────────────────────────────────────────────

    /**
     * T-01 — Créer une tâche.
     * POST /api/encadrant-entreprise/taches/sprint/{sprintId}
     */
    @PostMapping("/api/encadrant-entreprise/taches/sprint/{sprintId}")
    public ResponseEntity<TacheDto.Response> creer(
            @PathVariable Long sprintId,
            @Valid @RequestBody TacheDto.CreationRequest dto,
            Authentication authentication,
            HttpServletRequest request) {

        User encadrant = getUser(authentication);
        Tache tache = tacheService.creer(sprintId, dto, encadrant, request);
        return ResponseEntity.ok(tacheService.toDto(tache));
    }

    /**
     * T-02 — Modifier une tâche (par l'encadrant).
     * PUT /api/encadrant-entreprise/taches/{id}
     */
    @PutMapping("/api/encadrant-entreprise/taches/{id}")
    public ResponseEntity<TacheDto.Response> modifier(
            @PathVariable Long id,
            @RequestBody TacheDto.ModificationRequest dto,
            Authentication authentication,
            HttpServletRequest request) {

        User encadrant = getUser(authentication);
        Tache tache = tacheService.modifier(id, dto, encadrant, request);
        return ResponseEntity.ok(tacheService.toDto(tache));
    }

    /**
     * T-03 — Soft-delete une tâche.
     * DELETE /api/encadrant-entreprise/taches/{id}
     */
    @DeleteMapping("/api/encadrant-entreprise/taches/{id}")
    public ResponseEntity<Map<String, String>> supprimer(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest request) {

        User encadrant = getUser(authentication);
        tacheService.supprimer(id, encadrant, request);
        return ResponseEntity.ok(Map.of("message", "Tâche supprimée avec succès"));
    }

    // ── Encadrant — validation / refus ────────────────────────────────────────

    /**
     * T-04 — Valider une tâche (TERMINEE_PAR_ETUDIANT → VALIDEE).
     * POST /api/encadrant-entreprise/taches/{id}/valider
     * Body : { "observation": "...", "note": 18.5 }  (tous optionnels)
     */
    @PostMapping("/api/encadrant-entreprise/taches/{id}/valider")
    public ResponseEntity<TacheDto.Response> valider(
            @PathVariable Long id,
            @RequestBody(required = false) TacheDto.ValidationRequest dto,
            Authentication authentication,
            HttpServletRequest request) {

        User encadrant = getUser(authentication);
        Tache tache = tacheService.valider(id, dto, encadrant, request);
        return ResponseEntity.ok(tacheService.toDto(tache));
    }

    /**
     * T-05 — Refuser une tâche (TERMINEE_PAR_ETUDIANT → REFUSEE).
     * POST /api/encadrant-entreprise/taches/{id}/refuser
     * Body : { "observation": "Raison obligatoire" }
     */
    @PostMapping("/api/encadrant-entreprise/taches/{id}/refuser")
    public ResponseEntity<TacheDto.Response> refuser(
            @PathVariable Long id,
            @Valid @RequestBody TacheDto.RefusRequest dto,
            Authentication authentication,
            HttpServletRequest request) {

        User encadrant = getUser(authentication);
        Tache tache = tacheService.refuser(id, dto.getObservation(), encadrant, request);
        return ResponseEntity.ok(tacheService.toDto(tache));
    }

    /**
     * T-06 — Lister les tâches d'un sprint (encadrant).
     * GET /api/encadrant-entreprise/taches/sprint/{sprintId}
     */
    @GetMapping("/api/encadrant-entreprise/taches/sprint/{sprintId}")
    public ResponseEntity<List<TacheDto.Response>> listerParSprint(
            @PathVariable Long sprintId) {

        List<TacheDto.Response> list = tacheService.listerParSprint(sprintId).stream()
                .map(tacheService::toDto)
                .toList();
        return ResponseEntity.ok(list);
    }

    // ── Étudiant ──────────────────────────────────────────────────────────────

    /**
     * T-07 — Démarrer une tâche (A_FAIRE → EN_COURS).
     * POST /api/etudiant/taches/{id}/demarrer
     */
    @PostMapping("/api/etudiant/taches/{id}/demarrer")
    public ResponseEntity<TacheDto.Response> demarrer(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest request) {

        User etudiant = getUser(authentication);
        Tache tache = tacheService.demarrer(id, etudiant, request);
        return ResponseEntity.ok(tacheService.toDto(tache));
    }

    /**
     * T-08 — Marquer une tâche comme terminée (EN_COURS → TERMINEE_PAR_ETUDIANT).
     * POST /api/etudiant/taches/{id}/terminer
     * Body : { "noteEtudiant": "..." }  (optionnel)
     */
    @PostMapping("/api/etudiant/taches/{id}/terminer")
    public ResponseEntity<TacheDto.Response> terminer(
            @PathVariable Long id,
            @RequestBody(required = false) TacheDto.TerminerRequest body,
            Authentication authentication,
            HttpServletRequest request) {

        User etudiant = getUser(authentication);
        String note = (body != null) ? body.getNoteEtudiant() : null;
        Tache tache = tacheService.marquerTerminee(id, note, etudiant, request);
        return ResponseEntity.ok(tacheService.toDto(tache));
    }

    /**
     * T-09 — Reprendre une tâche refusée (REFUSEE → EN_COURS).
     * POST /api/etudiant/taches/{id}/reprendre
     */
    @PostMapping("/api/etudiant/taches/{id}/reprendre")
    public ResponseEntity<TacheDto.Response> reprendre(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest request) {

        User etudiant = getUser(authentication);
        Tache tache = tacheService.reprendre(id, etudiant, request);
        return ResponseEntity.ok(tacheService.toDto(tache));
    }

    /**
     * T-10 — Lister les tâches d'un sprint (étudiant, lecture seule).
     * GET /api/etudiant/taches/sprint/{sprintId}
     */
    @GetMapping("/api/etudiant/taches/sprint/{sprintId}")
    public ResponseEntity<List<TacheDto.Response>> listerParSprintEtudiant(
            @PathVariable Long sprintId) {

        List<TacheDto.Response> list = tacheService.listerParSprint(sprintId).stream()
                .map(tacheService::toDto)
                .toList();
        return ResponseEntity.ok(list);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private User getUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException(
                        "Utilisateur non trouvé : " + authentication.getName()));
    }
}
