package com.smartintern.backend.controller;

import com.smartintern.backend.dto.SessionConnexionDto;
import com.smartintern.backend.service.SessionConnexionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de consultation des sessions de connexion (ADMIN uniquement).
 *
 * Tous les endpoints sont sous /api/admin/sessions — la sécurité est gérée
 * par SecurityConfig (règle requestMatchers("/api/admin/**").hasRole("ADMIN")).
 */
@RestController
@RequestMapping("/api/admin/sessions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SessionConnexionController {

    private final SessionConnexionService sessionConnexionService;

    /**
     * SC-01 — Toutes les sessions paginées (historique global).
     *
     * GET /api/admin/sessions?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<Page<SessionConnexionDto.Response>> findAll(
            @PageableDefault(size = 20, sort = "dateConnexion") Pageable pageable) {
        return ResponseEntity.ok(sessionConnexionService.findAll(pageable));
    }

    /**
     * SC-02 — Sessions actives en ce moment (dashboard temps-réel).
     *
     * GET /api/admin/sessions/actives
     */
    @GetMapping("/actives")
    public ResponseEntity<List<SessionConnexionDto.Response>> findActives() {
        return ResponseEntity.ok(sessionConnexionService.findActives());
    }

    /**
     * SC-03 — Historique des sessions d'un utilisateur spécifique.
     *
     * GET /api/admin/sessions/user/{userId}?page=0&size=20
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<SessionConnexionDto.Response>> findByUser(
            @PathVariable Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(sessionConnexionService.findByUserId(userId, pageable));
    }
}
