package com.smartintern.backend.controller;

import com.smartintern.backend.dto.SprintDto;
import com.smartintern.backend.entity.Sprint;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.UserRepository;
import com.smartintern.backend.service.SprintService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints Sprints.
 *
 * Côté encadrant entreprise : /api/encadrant-entreprise/sprints/**
 * Côté étudiant              : /api/etudiant/sprints/**
 */
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SprintController {

    private final SprintService  sprintService;
    private final UserRepository userRepository;

    // ── Encadrant entreprise ──────────────────────────────────────────────────

    /**
     * SP-01 — Créer un sprint.
     * POST /api/encadrant-entreprise/sprints/stage/{stageId}
     */
    @PostMapping("/api/encadrant-entreprise/sprints/stage/{stageId}")
    public ResponseEntity<SprintDto.Response> creer(
            @PathVariable Long stageId,
            @Valid @RequestBody SprintDto.CreationRequest dto,
            Authentication authentication,
            HttpServletRequest request) {

        User encadrant = getUser(authentication);
        Sprint sprint = sprintService.creer(stageId, dto, encadrant, request);
        return ResponseEntity.ok(sprintService.toDto(sprint));
    }

    /**
     * SP-02 — Modifier un sprint.
     * PUT /api/encadrant-entreprise/sprints/{id}
     */
    @PutMapping("/api/encadrant-entreprise/sprints/{id}")
    public ResponseEntity<SprintDto.Response> modifier(
            @PathVariable Long id,
            @RequestBody SprintDto.ModificationRequest dto,
            Authentication authentication,
            HttpServletRequest request) {

        User encadrant = getUser(authentication);
        Sprint sprint = sprintService.modifier(id, dto, encadrant, request);
        return ResponseEntity.ok(sprintService.toDto(sprint));
    }

    /**
     * SP-03 — Soft-delete un sprint.
     * DELETE /api/encadrant-entreprise/sprints/{id}
     */
    @DeleteMapping("/api/encadrant-entreprise/sprints/{id}")
    public ResponseEntity<Map<String, String>> supprimer(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest request) {

        User encadrant = getUser(authentication);
        sprintService.supprimer(id, encadrant, request);
        return ResponseEntity.ok(Map.of("message", "Sprint supprimé avec succès"));
    }

    /**
     * SP-04 — Clôturer un sprint.
     * POST /api/encadrant-entreprise/sprints/{id}/cloturer
     */
    @PostMapping("/api/encadrant-entreprise/sprints/{id}/cloturer")
    public ResponseEntity<SprintDto.Response> cloturer(
            @PathVariable Long id,
            @Valid @RequestBody SprintDto.CloturerRequest body,
            Authentication authentication,
            HttpServletRequest request) {

        User encadrant = getUser(authentication);
        Sprint sprint = sprintService.cloturer(id, body.getObservation(), encadrant, request);
        return ResponseEntity.ok(sprintService.toDto(sprint));
    }

    /**
     * SP-05 — Lister les sprints d'un stage (encadrant).
     * GET /api/encadrant-entreprise/sprints/stage/{stageId}
     */
    @GetMapping("/api/encadrant-entreprise/sprints/stage/{stageId}")
    public ResponseEntity<List<SprintDto.Response>> listerParStage(
            @PathVariable Long stageId) {

        List<SprintDto.Response> list = sprintService.listerParStage(stageId).stream()
                .map(sprintService::toDto)
                .toList();
        return ResponseEntity.ok(list);
    }

    // ── Étudiant (lecture seule) ──────────────────────────────────────────────

    /**
     * SP-06 — Lister les sprints d'un stage (étudiant, lecture seule).
     * GET /api/etudiant/sprints/stage/{stageId}
     */
    @GetMapping("/api/etudiant/sprints/stage/{stageId}")
    public ResponseEntity<List<SprintDto.Response>> listerParStageEtudiant(
            @PathVariable Long stageId) {

        List<SprintDto.Response> list = sprintService.listerParStage(stageId).stream()
                .map(sprintService::toDto)
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
