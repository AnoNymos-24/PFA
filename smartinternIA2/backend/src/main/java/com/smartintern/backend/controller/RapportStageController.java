package com.smartintern.backend.controller;

import com.smartintern.backend.dto.RapportStageDto;
import com.smartintern.backend.service.RapportStageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RapportStageController {

    private final RapportStageService rapportStageService;

    // ── ET-07 + ET-08 : Étudiant crée un rapport (hebdo ou final) ────────────
    @PostMapping("/api/etudiant/stages/{stageId}/rapports")
    public ResponseEntity<RapportStageDto.RapportResponse> creerRapport(
            @PathVariable Long stageId,
            @RequestBody RapportStageDto.RapportRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                rapportStageService.creerRapport(stageId, request, authentication.getName()));
    }

    // ── Étudiant soumet un rapport ────────────────────────────────────────────
    @PatchMapping("/api/etudiant/rapports/{id}/soumettre")
    public ResponseEntity<RapportStageDto.RapportResponse> soumettre(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(
                rapportStageService.soumettre(id, authentication.getName()));
    }

    // ── Étudiant : mes rapports pour un stage ─────────────────────────────────
    @GetMapping("/api/etudiant/stages/{stageId}/rapports")
    public ResponseEntity<List<RapportStageDto.RapportResponse>> getMesRapports(
            @PathVariable Long stageId, Authentication authentication) {
        return ResponseEntity.ok(
                rapportStageService.getMesRapports(stageId, authentication.getName()));
    }

    // ── EA-03 : Encadrant académique voit les rapports d'un stage ────────────
    @GetMapping("/api/encadrant-academique/stages/{stageId}/rapports")
    public ResponseEntity<List<RapportStageDto.RapportResponse>> getRapportsParStage(
            @PathVariable Long stageId, Authentication authentication) {
        return ResponseEntity.ok(
                rapportStageService.getRapportsParStage(stageId, authentication.getName()));
    }

    // ── EA-01 : Encadrant académique valide un rapport ────────────────────────
    @PatchMapping("/api/encadrant-academique/rapports/{id}/valider")
    public ResponseEntity<RapportStageDto.RapportResponse> validerRapport(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(
                rapportStageService.validerRapport(id, authentication.getName()));
    }

    // ── EA-02 : Encadrant académique commente / rejette un rapport ────────────
    @PatchMapping("/api/encadrant-academique/rapports/{id}/commenter")
    public ResponseEntity<RapportStageDto.RapportResponse> commenterRapport(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        return ResponseEntity.ok(
                rapportStageService.commenterRapport(id, body.get("commentaire"),
                        authentication.getName()));
    }
}
