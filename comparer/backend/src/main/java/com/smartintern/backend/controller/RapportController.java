package com.smartintern.backend.controller;

import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.*;
import com.smartintern.backend.service.RapportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/rapports")
@RequiredArgsConstructor
public class RapportController {

    private final RapportService rapportService;
    private final UserRepository userRepository;
    private final StageRepository stageRepository;
    private final RapportRepository rapportRepository;

    // DÉPOSER UN RAPPORT (avec fichiers potentiellement)
    @PostMapping("/deposer")
    public ResponseEntity<?> deposerRapport(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam Long stageId,
            @RequestParam String type,
            @RequestParam(required = false) Integer semaine,
            @RequestParam String titre,
            @RequestParam String contenu,
            @RequestParam(required = false) List<MultipartFile> fichiers) {
        
        try {
            Map<String, Object> rapport = rapportService.deposer(
                user.getUsername(), stageId, type, semaine, titre, contenu);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Rapport déposé avec succès");
            response.put("rapport", rapport);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // MES RAPPORTS
    @GetMapping("/mes-rapports")
    public ResponseEntity<?> mesRapports(@AuthenticationPrincipal UserDetails user) {
        try {
            List<Map<String, Object>> rapports = rapportService.mesRapports(user.getUsername());
            
            if (rapports == null || rapports.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }
            return ResponseEntity.ok(rapports);
            
        } catch (Exception e) {
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    // RAPPORTS REÇUS (entreprise) - VERSION CORRIGÉE
    @GetMapping("/recus")
    public ResponseEntity<?> rapportsRecus(@AuthenticationPrincipal UserDetails user) {
        List<Map<String, Object>> rapports = rapportService.rapportsEntreprise(user.getUsername());
        
        if (rapports == null || rapports.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(rapports);
    }

    // ✅ NOUVEL ENDPOINT - RAPPORTS REÇUS PAR ENCADRANT
    @GetMapping("/recus/encadrant")
    public ResponseEntity<?> rapportsRecusParEncadrant(@AuthenticationPrincipal UserDetails user) {
        try {
            User encadrant = userRepository.findByEmail(user.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
            
            // Récupérer les stages où cet utilisateur est encadrant entreprise
            List<Stage> stages = stageRepository.findByEncadrantEntrepriseId(encadrant.getId());
            
            List<Map<String, Object>> tousRapports = new ArrayList<>();
            
            for (Stage stage : stages) {
                // Récupérer les rapports de ce stage
                List<Rapport> rapports = rapportRepository.findByStageId(stage.getId());
                
                for (Rapport rapport : rapports) {
                    Map<String, Object> r = new HashMap<>();
                    r.put("id", rapport.getId());
                    r.put("titre", rapport.getTitre());
                    r.put("contenu", rapport.getContenu());
                    r.put("dateDepot", rapport.getDateDepot());
                    r.put("statut", rapport.getStatut());
                    r.put("semaine", rapport.getSemaine());
                    r.put("type", rapport.getType());
                    r.put("commentaireEntreprise", rapport.getCommentaireEncadrantEntreprise());
                    r.put("stageId", stage.getId());
                    
                    // Ajouter les infos de l'étudiant
                    if (stage.getCandidature() != null && stage.getCandidature().getEtudiant() != null) {
                        r.put("etudiantPrenom", stage.getCandidature().getEtudiant().getPrenom());
                        r.put("etudiantNom", stage.getCandidature().getEtudiant().getNom());
                    }
                    
                    // Ajouter le titre de l'offre
                    if (stage.getCandidature() != null && stage.getCandidature().getOffre() != null) {
                        r.put("offreTitre", stage.getCandidature().getOffre().getTitre());
                    }
                    
                    tousRapports.add(r);
                }
            }
            
            return ResponseEntity.ok(tousRapports);
            
        } catch (Exception e) {
            System.err.println("Erreur: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    // RAPPORTS À VALIDER
    @GetMapping("/a-valider")
    public ResponseEntity<?> rapportsAValider(@AuthenticationPrincipal UserDetails user) {
        List<Map<String, Object>> rapports = rapportService.rapportsEncadrantAcademique(user.getUsername());
        
        List<Map<String, Object>> pending = rapports.stream()
            .filter(r -> "PENDING".equals(r.get("statut")))
            .toList();
        
        return ResponseEntity.ok(pending);
    }

    // VALIDER UN RAPPORT
    @PutMapping("/{id}/valider")
    public ResponseEntity<?> validerRapport(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails user) {
        
        try {
            String commentaire = body.getOrDefault("commentaire", "");
            Map<String, Object> rapport = rapportService.valider(id, commentaire, user.getUsername());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Rapport validé avec succès");
            response.put("rapport", rapport);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // COMMENTER UN RAPPORT
    @PutMapping("/{id}/commenter")
    public ResponseEntity<?> commenterRapport(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails user) {
        
        try {
            String commentaire = body.get("commentaire");
            Map<String, Object> rapport = rapportService.commenter(id, commentaire, user.getUsername());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Commentaire ajouté");
            response.put("rapport", rapport);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // REJETER UN RAPPORT
    @PutMapping("/{id}/rejeter")
    public ResponseEntity<?> rejeterRapport(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails user) {
        
        try {
            String commentaire = body.getOrDefault("commentaire", "");
            Map<String, Object> rapport = rapportService.rejeter(id, commentaire, user.getUsername());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Rapport rejeté");
            response.put("rapport", rapport);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    // RAPPORTS REÇUS PAR ENCADRANT ACADÉMIQUE
@GetMapping("/recus/encadrant-academique")
public ResponseEntity<?> rapportsRecusEncadrantAcademique(@AuthenticationPrincipal UserDetails user) {
    try {
        User encadrant = userRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        
        // Stages où cet utilisateur est encadrant académique
        List<Stage> stages = stageRepository.findByEncadrantAcademiqueId(encadrant.getId());
        
        List<Map<String, Object>> tousRapports = new ArrayList<>();
        
        for (Stage stage : stages) {
            List<Rapport> rapports = rapportRepository.findByStageId(stage.getId());
            
            for (Rapport rapport : rapports) {
                Map<String, Object> r = new HashMap<>();
                r.put("id", rapport.getId());
                r.put("titre", rapport.getTitre());
                r.put("contenu", rapport.getContenu());
                r.put("dateDepot", rapport.getDateDepot());
                r.put("statut", rapport.getStatut());
                r.put("semaine", rapport.getSemaine());
                r.put("type", rapport.getType());
                r.put("commentaireEncadrantAcademique", rapport.getCommentaireEncadrantAcademique());
                r.put("stageId", stage.getId());
                
                if (stage.getCandidature() != null && stage.getCandidature().getEtudiant() != null) {
                    r.put("etudiantPrenom", stage.getCandidature().getEtudiant().getPrenom());
                    r.put("etudiantNom", stage.getCandidature().getEtudiant().getNom());
                }
                
                if (stage.getCandidature() != null && stage.getCandidature().getOffre() != null) {
                    r.put("offreTitre", stage.getCandidature().getOffre().getTitre());
                }
                
                tousRapports.add(r);
            }
        }
        
        return ResponseEntity.ok(tousRapports);
        
    } catch (Exception e) {
        System.err.println("Erreur: " + e.getMessage());
        return ResponseEntity.ok(Collections.emptyList());
    }
}
}