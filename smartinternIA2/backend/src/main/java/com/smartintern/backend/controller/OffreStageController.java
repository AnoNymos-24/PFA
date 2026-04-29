package com.smartintern.backend.controller;

import com.smartintern.backend.dto.OffreStageDto;
import com.smartintern.backend.service.OffreStageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OffreStageController {

    private final OffreStageService offreStageService;

    // ── Entreprise ────────────────────────────────────────────────────────────

    @PostMapping("/api/entreprise/offres")
    public ResponseEntity<OffreStageDto.OffreStageResponse> createOffre(
            @Valid @RequestBody OffreStageDto.OffreStageRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(offreStageService.createOffre(request, authentication.getName()));
    }

    @GetMapping("/api/entreprise/offres")
    public ResponseEntity<List<OffreStageDto.OffreStageResponse>> getMesOffres(
            Authentication authentication) {
        return ResponseEntity.ok(offreStageService.getMesOffres(authentication.getName()));
    }

    @PutMapping("/api/entreprise/offres/{id}")
    public ResponseEntity<OffreStageDto.OffreStageResponse> updateOffre(
            @PathVariable Long id,
            @Valid @RequestBody OffreStageDto.OffreStageRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(offreStageService.updateOffre(id, request, authentication.getName()));
    }

    @DeleteMapping("/api/entreprise/offres/{id}")
    public ResponseEntity<?> deleteOffre(
            @PathVariable Long id, Authentication authentication) {
        offreStageService.deleteOffre(id, authentication.getName());
        return ResponseEntity.ok(Map.of("message", "Offre supprimée"));
    }

    // ── Étudiant ──────────────────────────────────────────────────────────────

    @GetMapping("/api/etudiant/offres")
    public ResponseEntity<?> getAllOffresActives(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "datePublication") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        if (size == 0) {
            // Rétrocompatibilité : retourne toute la liste
            return ResponseEntity.ok(offreStageService.getAllOffresActives());
        }
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Page<OffreStageDto.OffreStageResponse> result =
                offreStageService.getAllOffresActivesPaginees(PageRequest.of(page, size, sort));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/etudiant/offres/search")
    public ResponseEntity<List<OffreStageDto.OffreStageResponse>> searchOffres(
            @RequestParam(required = false) String domaine,
            @RequestParam(required = false) String localisation,
            @RequestParam(required = false) String typeStage,
            @RequestParam(required = false) String niveauRequis) {
        return ResponseEntity.ok(offreStageService.searchOffres(
                domaine, localisation, typeStage, niveauRequis));
    }

    // ── Admin : validation des offres ─────────────────────────────────────────

    @GetMapping("/api/admin/offres/en-attente")
    public ResponseEntity<List<OffreStageDto.OffreStageResponse>> getOffresEnAttente() {
        return ResponseEntity.ok(offreStageService
                .getAllOffresParStatutValidation("EN_ATTENTE"));
    }

    @PatchMapping("/api/admin/offres/{id}/valider")
    public ResponseEntity<OffreStageDto.OffreStageResponse> validerOffre(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        return ResponseEntity.ok(offreStageService.validerOffre(id, body.get("approuve")));
    }
}