package com.smartintern.backend.controller;

import com.smartintern.backend.dto.OffreDto;
import com.smartintern.backend.service.OffreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OffreController {

    private final OffreService offreService;

    // Entreprise — créer une offre
    @PostMapping("/api/entreprise/offres")
    public ResponseEntity<OffreDto.OffreResponse> createOffre(
            @Valid @RequestBody OffreDto.OffreRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(offreService.createOffre(request, authentication.getName()));
    }

    // Entreprise — mes offres
    @GetMapping("/api/entreprise/offres")
    public ResponseEntity<List<OffreDto.OffreResponse>> getMesOffres(Authentication authentication) {
        return ResponseEntity.ok(offreService.getMesOffres(authentication.getName()));
    }

    // Entreprise — modifier une offre
    @PutMapping("/api/entreprise/offres/{id}")
    public ResponseEntity<OffreDto.OffreResponse> updateOffre(
            @PathVariable Long id,
            @Valid @RequestBody OffreDto.OffreRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(offreService.updateOffre(id, request, authentication.getName()));
    }

    // Entreprise — supprimer une offre
    @DeleteMapping("/api/entreprise/offres/{id}")
    public ResponseEntity<?> deleteOffre(@PathVariable Long id, Authentication authentication) {
        offreService.deleteOffre(id, authentication.getName());
        return ResponseEntity.ok().body("Offre supprimée");
    }

    // Etudiant — voir toutes les offres actives
    @GetMapping("/api/etudiant/offres")
    public ResponseEntity<List<OffreDto.OffreResponse>> getAllOffres() {
        return ResponseEntity.ok(offreService.getAllOffresActives());
    }
}
