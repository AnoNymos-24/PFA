package com.smartintern.backend.controller;

import com.smartintern.backend.dto.LogActiviteDto;
import com.smartintern.backend.service.LogActiviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints de consultation des logs d'activité (ADMIN uniquement).
 *
 * Tous les endpoints sont sous /api/admin/logs — la sécurité est gérée
 * par SecurityConfig (règle requestMatchers("/api/admin/**").hasRole("ADMIN")).
 */
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LogActiviteController {

    private final LogActiviteService logActiviteService;

    /**
     * AL-01 — Tous les logs paginés (tri par date décroissante par défaut via repo).
     *
     * GET /api/admin/logs?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<Page<LogActiviteDto.Response>> findAll(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(logActiviteService.findAll(pageable));
    }

    /**
     * AL-02 — Logs filtrés par utilisateur.
     *
     * GET /api/admin/logs/user/{userId}?page=0&size=20
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<LogActiviteDto.Response>> findByUser(
            @PathVariable Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(logActiviteService.findByUserId(userId, pageable));
    }

    /**
     * AL-03 — Logs filtrés par entité cible (ex : sprints, taches, documents).
     *
     * GET /api/admin/logs/entite/{entiteCible}/{entiteId}?page=0&size=20
     */
    @GetMapping("/entite/{entiteCible}/{entiteId}")
    public ResponseEntity<Page<LogActiviteDto.Response>> findByEntite(
            @PathVariable String entiteCible,
            @PathVariable Long entiteId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(logActiviteService.findByEntite(entiteCible, entiteId, pageable));
    }
}
