package com.smartintern.backend.controller;

import com.smartintern.backend.dto.CompteRenduDTO;
import com.smartintern.backend.dto.MissionDTO;
import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/missions")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class MissionController {

    private final MissionRepository missionRepository;
    private final CompteRenduRepository compteRenduRepository;
    private final StageRepository stageRepository;
    private final UserRepository userRepository;

    // ==================== ENCADRANT ENTREPRISE ====================
    
    // Créer une nouvelle mission (utilise votre endpoint existant)
    @PostMapping("/stage/{stageId}")
    public ResponseEntity<?> creerMission(
            @PathVariable Long stageId,
            @RequestBody MissionDTO dto,
            @AuthenticationPrincipal UserDetails user) {
        
        try {
            Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new RuntimeException("Stage non trouvé"));
            
            Mission mission = new Mission();
            mission.setTitre(dto.getTitre());
            mission.setDescription(dto.getDescription());
            mission.setObjectifs(dto.getObjectifs());
            mission.setDateCreation(LocalDateTime.now());
            mission.setDateEcheance(dto.getDateEcheance());
            mission.setStatut(Mission.MissionStatut.A_FAIRE);
            mission.setStage(stage);
            
            missionRepository.save(mission);
            
            // Mettre à jour le stage pour indiquer qu'une mission est définie
            stage.setMissionDefinie(true);
            stageRepository.save(stage);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Mission créée avec succès");
            response.put("missionId", mission.getId());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    // Récupérer toutes les missions d'un stage
    @GetMapping("/stage/{stageId}")
    public ResponseEntity<?> getMissionsByStage(@PathVariable Long stageId) {
        List<Mission> missions = missionRepository.findByStageId(stageId);
        return ResponseEntity.ok(missions);
    }
    
  
@GetMapping("/encadrant/mes-missions")
@Transactional  // ← AJOUTE
public ResponseEntity<?> getMissionsPourEncadrant(@AuthenticationPrincipal UserDetails user) {
    try {
        String email = user.getUsername();
        System.out.println("=== RÉCUPÉRATION MISSIONS ===");
        System.out.println("Email: " + email);
        
        List<Stage> stages = stageRepository.findByEncadrantEntreprise_Email(email);
        System.out.println("Stages trouvés: " + stages.size());
        
        List<Mission> toutesMissions = new ArrayList<>();
        for (Stage stage : stages) {
            System.out.println("Stage ID: " + stage.getId());
            List<Mission> missions = missionRepository.findByStageId(stage.getId());
            missions.forEach(m -> m.getCompteRendus().size()); // ← AJOUTE
            System.out.println("Missions: " + missions.size());
            toutesMissions.addAll(missions);
        }
        
        return ResponseEntity.ok(toutesMissions);
        
    } catch (Exception e) {
        System.err.println("Erreur: " + e.getMessage());
        e.printStackTrace();
        return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
    }
}
    // Valider un compte-rendu
    @PutMapping("/compte-rendu/{compteRenduId}/valider")
@PreAuthorize("hasRole('ENCADRANT_ENTREPRISE')")
public ResponseEntity<?> validerCompteRendu(
        @PathVariable Long compteRenduId,
        @RequestBody Map<String, String> body) {
    
    CompteRendu compteRendu = compteRenduRepository.findById(compteRenduId)
        .orElseThrow(() -> new RuntimeException("Compte-rendu non trouvé"));
    
    compteRendu.setStatut(CompteRendu.CompteRenduStatut.VALIDE);
    compteRendu.setCommentaireEncadrant(body.get("commentaire"));
    compteRendu.setDateValidation(LocalDateTime.now());
    compteRenduRepository.save(compteRendu);
    
    return ResponseEntity.ok(Map.of("message", "Compte-rendu validé"));
}
    
    // Rejeter un compte-rendu
   @PutMapping("/compte-rendu/{compteRenduId}/rejeter")
@PreAuthorize("hasRole('ENCADRANT_ENTREPRISE')")
public ResponseEntity<?> rejeterCompteRendu(
        @PathVariable Long compteRenduId,
        @RequestBody Map<String, String> body) {
    
    CompteRendu compteRendu = compteRenduRepository.findById(compteRenduId)
        .orElseThrow(() -> new RuntimeException("Compte-rendu non trouvé"));
    
    compteRendu.setStatut(CompteRendu.CompteRenduStatut.REJETE);
    compteRendu.setCommentaireEncadrant(body.get("commentaire"));
    compteRenduRepository.save(compteRendu);
    
    return ResponseEntity.ok(Map.of("message", "Compte-rendu rejeté"));
}

    // ==================== ÉTUDIANT ====================
    
    // Récupérer les missions de l'étudiant connecté
 @GetMapping("/etudiant/mes-missions")
@Transactional
public ResponseEntity<?> getMesMissions(@AuthenticationPrincipal UserDetails user) {
    User etudiant = userRepository.findByEmail(user.getUsername())
        .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    
    System.out.println("🟢 Recherche missions pour étudiant ID: " + etudiant.getId());
    
    List<Mission> missions = missionRepository.findByEtudiantId(etudiant.getId());
    
    System.out.println("🟢 Missions trouvées: " + missions.size());
    for (Mission m : missions) {
        System.out.println("   - Mission: " + m.getTitre() + " (ID: " + m.getId() + ")");
    }
    
    return ResponseEntity.ok(missions);
}
@PutMapping("/{missionId}/demarrer")
public ResponseEntity<?> demarrerMission(@PathVariable Long missionId) {
    Mission mission = missionRepository.findById(missionId)
        .orElseThrow(() -> new RuntimeException("Mission non trouvée"));
    
    mission.setStatut(Mission.MissionStatut.EN_COURS);
    missionRepository.save(mission);
    
    return ResponseEntity.ok(Map.of("message", "Mission démarrée avec succès"));
}
@GetMapping("/{missionId}/comptes-rendus")
public ResponseEntity<?> getComptesRendusByMission(@PathVariable Long missionId) {
    Mission mission = missionRepository.findById(missionId)
        .orElseThrow(() -> new RuntimeException("Mission non trouvée"));
    
    List<CompteRendu> comptesRendus = compteRenduRepository.findByMission(mission);
    return ResponseEntity.ok(comptesRendus);
}
    
    // Soumettre un compte-rendu
    @PostMapping("/{missionId}/compte-rendu")
public ResponseEntity<?> soumettreCompteRendu(
        @PathVariable Long missionId,
        @RequestBody CompteRenduDTO dto,
        @AuthenticationPrincipal UserDetails user){
        
        try {
            Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new RuntimeException("Mission non trouvée"));
            
            User etudiant = userRepository.findByEmail(user.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
            
            // Vérifier si un compte-rendu est déjà en attente
            boolean hasPending = mission.getCompteRendus().stream()
                .anyMatch(cr -> cr.getStatut() == CompteRendu.CompteRenduStatut.EN_ATTENTE);
            
            if (hasPending) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Vous avez déjà un compte-rendu en attente pour cette mission"));
            }
            
            CompteRendu compteRendu = new CompteRendu();
            compteRendu.setContenu(dto.getContenu());
            compteRendu.setCommentaireEtudiant(dto.getCommentaire());
            compteRendu.setDateSoumission(LocalDateTime.now());
            compteRendu.setStatut(CompteRendu.CompteRenduStatut.EN_ATTENTE);
            compteRendu.setMission(mission);
            compteRendu.setEtudiant(etudiant);
            
            compteRenduRepository.save(compteRendu);
            
            // Mettre à jour le statut de la mission
            if (mission.getStatut() == Mission.MissionStatut.A_FAIRE) {
                mission.setStatut(Mission.MissionStatut.EN_COURS);
                missionRepository.save(mission);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Compte-rendu soumis avec succès");
            response.put("compteRenduId", compteRendu.getId());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
}