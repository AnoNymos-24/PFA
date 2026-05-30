package com.smartintern.backend.controller;

import com.smartintern.backend.dto.CahierDesChargesDto;
import com.smartintern.backend.entity.CahierDesCharges;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.UserRepository;
import com.smartintern.backend.service.CahierDesChargesService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoints Cahier des charges.
 *
 * Côté encadrant entreprise : /api/encadrant-entreprise/cahier-des-charges/**
 * Côté étudiant              : /api/etudiant/cahier-des-charges/**
 */
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CahierDesChargesController {

    private final CahierDesChargesService cdcService;
    private final UserRepository          userRepository;

    // ── Encadrant entreprise ──────────────────────────────────────────────────

    /**
     * CDC-01 — Upload du cahier des charges (multipart).
     * POST /api/encadrant-entreprise/cahier-des-charges/stage/{stageId}
     * Params multipart : file (required), titre (required)
     */
    @PostMapping(
        value = "/api/encadrant-entreprise/cahier-des-charges/stage/{stageId}",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<CahierDesChargesDto.Response> televerser(
            @PathVariable Long stageId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("titre") String titre,
            Authentication authentication,
            HttpServletRequest request) {

        User encadrant = getUser(authentication);
        CahierDesCharges cdc = cdcService.televerser(stageId, file, titre, encadrant, request);
        return ResponseEntity.ok(cdcService.toDto(cdc));
    }

    /**
     * CDC-02 — Validation du cahier des charges (BROUILLON → VALIDE).
     * POST /api/encadrant-entreprise/cahier-des-charges/{id}/valider
     */
    @PostMapping("/api/encadrant-entreprise/cahier-des-charges/{id}/valider")
    public ResponseEntity<CahierDesChargesDto.Response> valider(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest request) {

        User encadrant = getUser(authentication);
        CahierDesCharges cdc = cdcService.valider(id, encadrant, request);
        return ResponseEntity.ok(cdcService.toDto(cdc));
    }

    /**
     * CDC-03 — Détail du CDC par stage (encadrant).
     * GET /api/encadrant-entreprise/cahier-des-charges/stage/{stageId}
     */
    @GetMapping("/api/encadrant-entreprise/cahier-des-charges/stage/{stageId}")
    public ResponseEntity<CahierDesChargesDto.Response> obtenirParStage(
            @PathVariable Long stageId) {

        return ResponseEntity.ok(cdcService.toDto(cdcService.obtenirParStage(stageId)));
    }

    // ── Étudiant (lecture seule) ──────────────────────────────────────────────

    /**
     * CDC-04 — Consultation du CDC par stage (étudiant, lecture seule).
     * GET /api/etudiant/cahier-des-charges/stage/{stageId}
     */
    @GetMapping("/api/etudiant/cahier-des-charges/stage/{stageId}")
    public ResponseEntity<CahierDesChargesDto.Response> obtenirParStageEtudiant(
            @PathVariable Long stageId) {

        return ResponseEntity.ok(cdcService.toDto(cdcService.obtenirParStage(stageId)));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private User getUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException(
                        "Utilisateur non trouvé : " + authentication.getName()));
    }
}
