package com.smartintern.backend.controller;

import com.smartintern.backend.service.StageService;
import com.smartintern.backend.repository.StageRepository;
import lombok.RequiredArgsConstructor;
import com.smartintern.backend.entity.Stage;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.*;


@RestController
@RequestMapping("/api/stages")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class StageController {

    private final StageService service;
     private final StageRepository stageRepository;

    // ── MON STAGE ─────────────────────────────
   @GetMapping("/mon-stage")
public ResponseEntity<?> monStage(@AuthenticationPrincipal UserDetails user) {
    if (user == null) {
        return ResponseEntity.status(401).body("Token invalide ou manquant");
    }

    Map<String, Object> stage = service.monStage(user.getUsername());

    if (stage == null) {
        Map<String, Object> empty = new HashMap<>();
        empty.put("hasStage", false);
        return ResponseEntity.ok(empty);
    }

    // stage vient de stageToMap() qui est un LinkedHashMap → mutable ✅
    stage.put("hasStage", true);
    return ResponseEntity.ok(stage);
}

    // ── STAGES ENTREPRISE ──────────────────────
    @GetMapping("/mes-stagiaires")
    public ResponseEntity<List<Map<String, Object>>> mesStagiaires(
            @AuthenticationPrincipal UserDetails user) {

        if (user == null) {
            return ResponseEntity.status(401).body(List.of());
        }

        return ResponseEntity.ok(service.stagesEntreprise(user.getUsername()));
    }

    // ── ENCADRANT ENTREPRISE ───────────────────
    @GetMapping("/encadrant-entreprise")
    public ResponseEntity<List<Map<String, Object>>> stagesEncadrantEntreprise(
            @AuthenticationPrincipal UserDetails user) {

        if (user == null) {
            return ResponseEntity.status(401).body(List.of());
        }

        return ResponseEntity.ok(service.stagesEncadrantEntreprise(user.getUsername()));
    }

    // ── ENCADRANT ACADÉMIQUE ───────────────────
    @GetMapping("/encadrant-academique")
    public ResponseEntity<List<Map<String, Object>>> stagesEncadrantAcademique(
            @AuthenticationPrincipal UserDetails user) {

        if (user == null) {
            return ResponseEntity.status(401).body(List.of());
        }

        return ResponseEntity.ok(service.stagesEncadrantAcademique(user.getUsername()));
    }

    // ── SIGNATURE ENTREPRISE ───────────────────
    @PutMapping("/{id}/signer-convention-entreprise")
    public ResponseEntity<?> signerEntreprise(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(
                service.signerConventionEntreprise(id, user.getUsername())
        );
    }

    // ── SIGNATURE ENCADRANT ────────────────────
    @PutMapping("/{id}/signer-convention-encadrant")
    public ResponseEntity<?> signerEncadrant(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(
                service.signerConventionEncadrant(id, user.getUsername())
        );
    }

    // ── MISSION ────────────────────────────────
    @PutMapping("/{id}/mission")
    public ResponseEntity<?> definirMission(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(
                service.definirMission(
                        id,
                        body.get("description"),
                        body.get("objectifs"),
                        user.getUsername()
                )
        );
    }

    // ── CONVENTIONS ────────────────────────────
    @GetMapping("/mes-conventions")
    public ResponseEntity<?> mesConventions(
            @AuthenticationPrincipal UserDetails user) {

        if (user == null) {
            return ResponseEntity.status(401).body("Non authentifié");
        }

        return ResponseEntity.ok(
                service.mesConventionsEntreprise(user.getUsername())
        );
    }
  
// Entreprise affecte un encadrant entreprise à un stage
@PutMapping("/{id}/affecter-encadrant-entreprise")
public ResponseEntity<?> affecterEncadrantEntreprise(
        @PathVariable Long id,
        @RequestBody Map<String, Long> body,
        @AuthenticationPrincipal UserDetails user) {
    return ResponseEntity.ok(
        service.affecterEncadrantEntreprise(id, body.get("encadrantId"), user.getUsername())
    );
}

// Admin affecte un encadrant académique à un stage
@PutMapping("/{id}/affecter-encadrant-academique")
public ResponseEntity<?> affecterEncadrantAcademique(
        @PathVariable Long id,
        @RequestBody Map<String, Long> body,
        @AuthenticationPrincipal UserDetails user) {
    return ResponseEntity.ok(
        service.affecterEncadrantAcademique(id, body.get("encadrantId"), user.getUsername())
    );
}
@PostMapping("/{stageId}/definir-mission")
public ResponseEntity<?> definirMissionSimple(
        @PathVariable Long stageId,
        @RequestBody Map<String, String> body,
        @AuthenticationPrincipal UserDetails user) {
    
    String description = body.get("description");
    String objectifs = body.get("objectifs");
    
    Stage stage = stageRepository.findById(stageId).orElseThrow();
    stage.setMissionDescription(description);
    stage.setObjectifs(objectifs);
    stage.setMissionDefinie(true);
    stageRepository.save(stage);
    
    return ResponseEntity.ok(Map.of("message", "Mission définie avec succès"));
}
// Récupérer les stages éligibles à la soutenance
@GetMapping("/soutenances")
public ResponseEntity<?> stagesEligiblesSoutenance(
        @AuthenticationPrincipal UserDetails user) {
    
    List<Stage> stages = stageRepository.findByEncadrantAcademique_Email(user.getUsername());
    
    List<Map<String, Object>> result = stages.stream().map(s -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("etudiantPrenom", s.getCandidature().getEtudiant().getPrenom());
        m.put("etudiantNom", s.getCandidature().getEtudiant().getNom());
        m.put("offreTitre", s.getCandidature().getOffre().getTitre());
        m.put("entrepriseNom", s.getCandidature().getOffre().getEntreprise().getRaisonSociale());
        m.put("dateDebut", s.getDateDebut());
        m.put("dateFin", s.getDateFin());
        m.put("semaineActuelle", s.getSemaineActuelle());
        m.put("soutenanceAutorisee", s.isSoutenanceAutorisee());
        m.put("dateSoutenance", s.getDateSoutenance());
        return m;
    }).toList();
    
    return ResponseEntity.ok(result);
}

// Autoriser la soutenance
@PutMapping("/{id}/autoriser-soutenance")
public ResponseEntity<?> autoriserSoutenance(
        @PathVariable Long id,
        @RequestBody Map<String, String> body,
        @AuthenticationPrincipal UserDetails user) {
    
    Stage stage = stageRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Stage non trouvé"));
    
    stage.setSoutenanceAutorisee(true);
    stage.setDateSoutenance(body.get("dateSoutenance") != null ? 
        java.time.LocalDate.parse(body.get("dateSoutenance")) : null);
    stage.setCommentaireSoutenance(body.getOrDefault("commentaire", ""));
    stageRepository.save(stage);
    
    return ResponseEntity.ok(Map.of("message", "Soutenance autorisée avec succès"));
}
}