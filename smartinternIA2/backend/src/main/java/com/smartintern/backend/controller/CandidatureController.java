package com.smartintern.backend.controller;

import com.smartintern.backend.dto.CandidatureDto;
import com.smartintern.backend.service.CandidatureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CandidatureController {

    private final CandidatureService candidatureService;

    // ── Étudiant : postuler ───────────────────────────────────────────────────
    @PostMapping("/api/etudiant/candidatures")
    public ResponseEntity<CandidatureDto.CandidatureResponse> postuler(
            @Valid @RequestBody CandidatureDto.CandidatureRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(candidatureService.postuler(request, authentication.getName()));
    }

    // ── Étudiant : mes candidatures ───────────────────────────────────────────
    @GetMapping("/api/etudiant/candidatures")
    public ResponseEntity<List<CandidatureDto.CandidatureResponse>> getMesCandidatures(
            Authentication authentication) {
        return ResponseEntity.ok(candidatureService.getMesCandidatures(authentication.getName()));
    }

    // ── Entreprise : candidatures pour une offre ──────────────────────────────
    @GetMapping("/api/entreprise/offres/{offreId}/candidatures")
    public ResponseEntity<List<CandidatureDto.CandidatureResponse>> getCandidaturesParOffre(
            @PathVariable Long offreId) {
        return ResponseEntity.ok(candidatureService.getCandidaturesParOffre(offreId));
    }

    // ── Entreprise : décision sur une candidature ─────────────────────────────
    @PatchMapping("/api/entreprise/candidatures/{id}/decision")
    public ResponseEntity<CandidatureDto.CandidatureResponse> decider(
            @PathVariable Long id,
            @RequestBody CandidatureDto.CandidatureDecisionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(candidatureService.decider(id, request, authentication.getName()));
    }
}
