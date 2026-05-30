package com.smartintern.backend.controller;

import com.smartintern.backend.dto.OffreRequest;
import com.smartintern.backend.dto.OffreResponse;
import com.smartintern.backend.service.OffreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/offres")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class OffreController {

    private final OffreService offreService;

    // ── PUBLIC — Lister les offres actives ────────────────────
    @GetMapping
    public ResponseEntity<List<OffreResponse>> listerActives() {
        return ResponseEntity.ok(offreService.listerActives());
    }

    // ── PUBLIC — Détail d'une offre ───────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<OffreResponse> detail(@PathVariable Long id) {
        return ResponseEntity.ok(offreService.detail(id));
    }

    // ── PUBLIC — Recherche ────────────────────────────────────
    @GetMapping("/recherche")
    public ResponseEntity<List<OffreResponse>> rechercher(
            @RequestParam String q) {
        return ResponseEntity.ok(offreService.rechercher(q));
    }

    // ── PUBLIC — Filtres ──────────────────────────────────────
    @GetMapping("/filtrer")
    public ResponseEntity<List<OffreResponse>> filtrer(
            @RequestParam(required = false) String lieu,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String duree,
            @RequestParam(required = false) String niveau) {
        return ResponseEntity.ok(offreService.filtrer(lieu, type, duree, niveau));
    }

    // ── ENTREPRISE — Mes offres ───────────────────────────────
   @GetMapping("/mes-offres")
public ResponseEntity<?> mesOffres(@AuthenticationPrincipal UserDetails user) {

    if (user == null) {
        return ResponseEntity.status(401).body("USER NULL");
    }

    return ResponseEntity.ok(offreService.listerParEntreprise(user.getUsername()));
}

    // ── ENTREPRISE — Publier ──────────────────────────────────
    @PostMapping
    public ResponseEntity<OffreResponse> publier(
            @RequestBody OffreRequest req,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(offreService.publier(req, user.getUsername()));
    }

    // ── ENTREPRISE — Modifier ─────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<OffreResponse> modifier(
            @PathVariable Long id,
            @RequestBody OffreRequest req,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(offreService.modifier(id, req, user.getUsername()));
    }

    // ── ENTREPRISE — Clôturer ─────────────────────────────────
    @PutMapping("/{id}/cloturer")
    public ResponseEntity<Void> cloturer(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        offreService.cloturer(id, user.getUsername());
        return ResponseEntity.ok().build();
    }

    // ── ENTREPRISE — Supprimer ────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        offreService.supprimer(id, user.getUsername());
        return ResponseEntity.ok().build();
    }
}