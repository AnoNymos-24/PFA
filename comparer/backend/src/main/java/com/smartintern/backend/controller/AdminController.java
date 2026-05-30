package com.smartintern.backend.controller;

import com.smartintern.backend.entity.Role;
import com.smartintern.backend.entity.Stage;
import com.smartintern.backend.entity.Offre;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.OffreRepository;
import com.smartintern.backend.repository.UserRepository;
import com.smartintern.backend.repository.StageRepository;
import com.smartintern.backend.repository.RapportRepository;
import com.smartintern.backend.service.StageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.*;
import java.util.stream.Collectors;
import com.smartintern.backend.service.AuthService;
import com.smartintern.backend.repository.CandidatureRepository;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository  userRepo;
    private final OffreRepository offreRepo;
    private final AuthService     authService;
  private final CandidatureRepository candidatureRepo;
private final StageService stageService;
private final StageRepository stageRepo;
private final RapportRepository rapportRepo;

    // ── STATS GLOBALES ────────────────────────────────────────
@GetMapping("/stats")
public ResponseEntity<Map<String, Object>> stats() {
    
    long conventionsSignees = stageRepo.countByConventionSigneeEntrepriseTrue();
  
    
    return ResponseEntity.ok(Map.of(
        "totalUsers", userRepo.count(),
        "etudiants", userRepo.countByRoleName(Role.RoleName.ROLE_ETUDIANT),
        "entreprises", userRepo.countByRoleName(Role.RoleName.ROLE_ENTREPRISE),
        "encadrants", userRepo.countByRoleName(Role.RoleName.ROLE_ENCADRANT_ENTREPRISE)
                     + userRepo.countByRoleName(Role.RoleName.ROLE_ENCADRANT_ACADEMIQUE),
        "offres", offreRepo.count(),
        "candidatures", candidatureRepo.count(),
        "stagesEnCours", stageRepo.countByStatut(Stage.StatutStage.EN_COURS),
        "conventions", conventionsSignees,  // ← Ajout
        "rapports", rapportRepo.count()         // ← Ajout
    ));
}
    // ── TOUS LES UTILISATEURS ─────────────────────────────────
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> users() {
        return ResponseEntity.ok(
            userRepo.findAll().stream()
                .map(this::userToMap)
                .collect(Collectors.toList())
        );
    }

    // ── PAR RÔLE ──────────────────────────────────────────────
    @GetMapping("/users/role/{role}")
    public ResponseEntity<List<Map<String, Object>>> usersByRole(
            @PathVariable String role) {
        try {
            Role.RoleName roleName = Role.RoleName.valueOf("ROLE_" + role.toUpperCase());
            return ResponseEntity.ok(
                userRepo.findByRoleName(roleName).stream()
                    .map(this::userToMap)
                    .collect(Collectors.toList())
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ── TOUS LES ÉTUDIANTS ────────────────────────────────────
    @GetMapping("/etudiants")
    public ResponseEntity<List<Map<String, Object>>> etudiants() {
        return ResponseEntity.ok(
            userRepo.findByRoleName(Role.RoleName.ROLE_ETUDIANT).stream()
                .map(this::userToMap)
                .collect(Collectors.toList())
        );
    }

    // ── TOUTES LES ENTREPRISES ────────────────────────────────
    @GetMapping("/entreprises")
    public ResponseEntity<List<Map<String, Object>>> entreprises() {
        return ResponseEntity.ok(
            userRepo.findByRoleName(Role.RoleName.ROLE_ENTREPRISE).stream()
                .map(this::userToMap)
                .collect(Collectors.toList())
        );
    }

    // ── ÉTUDIANTS À RISQUE (IA simple) ────────────────────────
    @GetMapping("/etudiants/risque")
    public ResponseEntity<List<Map<String, Object>>> etudiantsARisque() {
        // Logique IA Sprint 4 — pour l'instant retourne liste vide
        return ResponseEntity.ok(List.of());
    }

    // ── VALIDER UN UTILISATEUR ────────────────────────────────
    @PutMapping("/users/{id}/validate")
    public ResponseEntity<Map<String, String>> validate(@PathVariable Long id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable."));
        user.setEnabled(true);
        user.setEmailVerified(true);
        userRepo.save(user);
        return ResponseEntity.ok(Map.of("message", "Utilisateur validé."));
    }

    // ── ACTIVER ───────────────────────────────────────────────
    @PutMapping("/users/{id}/enable")
    public ResponseEntity<Map<String, String>> enable(@PathVariable Long id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable."));
        user.setEnabled(true);
        userRepo.save(user);
        return ResponseEntity.ok(Map.of("message", "Compte activé."));
    }

    // ── DÉSACTIVER ────────────────────────────────────────────
    @PutMapping("/users/{id}/disable")
    public ResponseEntity<Map<String, String>> disable(@PathVariable Long id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable."));
        user.setEnabled(false);
        userRepo.save(user);
        return ResponseEntity.ok(Map.of("message", "Compte désactivé."));
    }

    // ── STAGES (Sprint 2) ─────────────────────────────────────
  @GetMapping("/stages")
public ResponseEntity<List<Map<String, Object>>> getAllStages() {
    return ResponseEntity.ok(stageService.getAllStages());
}

    @PutMapping("/stages/{id}/validate")
    public ResponseEntity<Map<String, String>> validateStage(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("message", "À implémenter Sprint 2."));
    }

    // ── VALIDER ENTREPRISE ────────────────────────────────────
    @PutMapping("/entreprises/{id}/validate")
    public ResponseEntity<Map<String, String>> validateEntreprise(@PathVariable Long id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Entreprise introuvable."));
        user.setEnabled(true);
        user.setEmailVerified(true);
        userRepo.save(user);
        return ResponseEntity.ok(Map.of("message", "Entreprise validée."));
    }

    // ── UTILITAIRE ────────────────────────────────────────────
    private Map<String, Object> userToMap(User u) {
        String role = u.getRoles().isEmpty() ? "—"
                    : u.getRoles().iterator().next().getName().name();
        Map<String, Object> map = new HashMap<>();
        map.put("id",            u.getId());
        map.put("nom",           u.getNom());
        map.put("prenom",        u.getPrenom());
        map.put("email",         u.getEmail());
        map.put("enabled",       u.isEnabled());
        map.put("emailVerified", u.isEmailVerified());
        map.put("role",          role);
        map.put("raisonSociale", u.getRaisonSociale() != null ? u.getRaisonSociale() : "");
        map.put("universite",    u.getUniversite()    != null ? u.getUniversite()    : "");
        map.put("createdAt",     u.getCreatedAt()     != null ? u.getCreatedAt().toString() : "");
        return map;
    }
   @PostMapping("/encadrants")
public ResponseEntity<?> creerEncadrant(
        @RequestBody Map<String, String> body) {

    String nom    = body.get("nom");
    String prenom = body.get("prenom");
    String email  = body.get("email");
    String role   = "ENCADRANT_ACADEMIQUE";

    if (nom == null || prenom == null || email == null ||
        nom.isBlank() || prenom.isBlank() || email.isBlank()) {
        Map<String, String> err = new HashMap<>();
        err.put("message", "Nom, prénom et email sont obligatoires.");
        return ResponseEntity.badRequest().body(err);
    }

    try {
        Map<String, Object> result = authService.registerByAdmin(nom, prenom, email, role);
        return ResponseEntity.ok(result);
    } catch (Exception e) {
        Map<String, String> err = new HashMap<>();
        err.put("message", e.getMessage());
        return ResponseEntity.badRequest().body(err);
    }
}
@GetMapping("/stages/{id}")
public ResponseEntity<Map<String, Object>> getStageDetails(@PathVariable Long id) {
    Stage stage = stageRepo.findById(id)
        .orElseThrow(() -> new RuntimeException("Stage introuvable"));
    
    Map<String, Object> details = new HashMap<>();
    details.put("id", stage.getId());
    details.put("statut", stage.getStatut().name());
    details.put("dateDebut", stage.getDateDebut());
    details.put("dateFin", stage.getDateFin());
    
    // Étudiant
    User etudiant = stage.getCandidature().getEtudiant();
    details.put("etudiantNom", etudiant.getNom());
    details.put("etudiantPrenom", etudiant.getPrenom());
    details.put("etudiantEmail", etudiant.getEmail());
    
    // Offre
    Offre offre = stage.getCandidature().getOffre();
    details.put("poste", offre.getTitre());
    details.put("entrepriseNom", offre.getEntreprise().getRaisonSociale());
    
    // Mission
    details.put("missionDescription", stage.getMissionDescription());
    details.put("objectifs", stage.getObjectifs());
    
    return ResponseEntity.ok(details);
}
}