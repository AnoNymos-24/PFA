package com.smartintern.backend.controller;

import com.smartintern.backend.dto.StageDto;
import com.smartintern.backend.service.StageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StageController {

    private final StageService stageService;

    // ── Admin : créer un stage depuis une candidature acceptée ────────────────
    @PostMapping("/api/admin/stages/depuis-candidature/{candidatureId}")
    public ResponseEntity<StageDto.StageResponse> creerDepuisCandidature(
            @PathVariable Long candidatureId) {
        return ResponseEntity.ok(stageService.creerDepuisCandidature(candidatureId));
    }

    // ── Admin : assigner les encadrants à un stage ────────────────────────────
    @PatchMapping("/api/admin/stages/{id}/encadrants")
    public ResponseEntity<StageDto.StageResponse> assignerEncadrants(
            @PathVariable Long id,
            @RequestBody StageDto.StageRequest request) {
        return ResponseEntity.ok(stageService.assignerEncadrants(id, request));
    }

    // ── Étudiant : mon/mes stages ─────────────────────────────────────────────
    @GetMapping("/api/etudiant/stages")
    public ResponseEntity<List<StageDto.StageResponse>> getMesStages(
            Authentication authentication) {
        return ResponseEntity.ok(stageService.getMesStages(authentication.getName()));
    }

    // ── Encadrant académique : ses stagiaires ─────────────────────────────────
    @GetMapping("/api/encadrant-academique/stages")
    public ResponseEntity<List<StageDto.StageResponse>> getStagesEncadrantAcademique(
            Authentication authentication) {
        return ResponseEntity.ok(stageService.getStagesEncadrantAcademique(authentication.getName()));
    }

    // ── Encadrant entreprise : ses stagiaires ─────────────────────────────────
    @GetMapping("/api/encadrant-entreprise/stages")
    public ResponseEntity<List<StageDto.StageResponse>> getStagesEncadrantEntreprise(
            Authentication authentication) {
        return ResponseEntity.ok(stageService.getStagesEncadrantEntreprise(authentication.getName()));
    }
}
