package com.smartintern.backend.service;

import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.*;
import com.smartintern.backend.entity.Stage; // ← AJOUTEZ CET IMPORT
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class StageService {

    private final StageRepository stageRepo;
    private final UserRepository userRepo;
    private final RapportRepository rapportRepo;

    // ── MON STAGE ─────────────────────────────
   public Map<String, Object> monStage(String emailEtudiant) {
    List<Stage> stages = stageRepo.findAllByCandidature_Etudiant_Email(emailEtudiant);
    
    if (stages.isEmpty()) {
        return null;
    }
    
    // Prendre le premier (normalement il n'y en a qu'un, mais la jointure a créé un doublon)
    Stage stage = stages.get(0);
    return stageToMap(stage);
}

    // ── ENTREPRISE ─────────────────────────────
    public List<Map<String, Object>> stagesEntreprise(String emailEntreprise) {
        return stageRepo.findByCandidature_Offre_Entreprise_Email(emailEntreprise)
                .stream()
                .map(this::stageToMap)
                .toList();
    }

    // ── ENCADRANT ENTREPRISE ───────────────────
    public List<Map<String, Object>> stagesEncadrantEntreprise(String email) {
        return stageRepo.findByEncadrantEntreprise_Email(email)
                .stream()
                .map(this::stageToMap)
                .toList();
    }

    // ── ENCADRANT ACADÉMIQUE ───────────────────
    public List<Map<String, Object>> stagesEncadrantAcademique(String email) {
        return stageRepo.findByEncadrantAcademique_Email(email)
                .stream()
                .map(this::stageToMap)
                .toList();
    }

    // ── SIGNER CONVENTION ENTREPRISE ───────────
    public Map<String, Object> signerConventionEntreprise(Long id, String email) {
        Stage stage = stageRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Stage introuvable"));

        stage.setConventionSigneeEntreprise(true);
        stage.setDateSignatureConvention(LocalDateTime.now());

        return stageToMap(stageRepo.save(stage));
    }

    // ── SIGNER CONVENTION ENCADRANT ────────────
    public Map<String, Object> signerConventionEncadrant(Long id, String email) {
        Stage stage = stageRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Stage introuvable"));

        stage.setConventionSigneeEncadrantEntreprise(true);

        return stageToMap(stageRepo.save(stage));
    }

    // ── MISSION ────────────────────────────────
    public Map<String, Object> definirMission(Long id, String desc, String obj, String email) {
        Stage stage = stageRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Stage introuvable"));

        stage.setMissionDescription(desc);
        stage.setObjectifs(obj);
        stage.setMissionDefinie(true);

        return stageToMap(stageRepo.save(stage));
    }

    // ── MAPPING ────────────────────────────────
    public Map<String, Object> stageToMap(Stage s) {
    Map<String, Object> map = new LinkedHashMap<>();

    map.put("hasStage", true);  // ← AJOUT
    map.put("id", s.getId());
    map.put("statut", s.getStatut().name());
    map.put("dateDebut", s.getDateDebut());
    map.put("dateFin", s.getDateFin());
    map.put("semaineActuelle", s.getSemaineActuelle());

    // ← AJOUT : calcul durée en semaines
    long dureeSemaines = 0;
    if (s.getDateDebut() != null && s.getDateFin() != null) {
        dureeSemaines = java.time.temporal.ChronoUnit.WEEKS.between(
            s.getDateDebut(), s.getDateFin()
        );
    }
    map.put("dureeSemaines", dureeSemaines == 0 ? 12 : dureeSemaines);

    map.put("missionDefinie", s.isMissionDefinie());
    map.put("missionDescription", s.getMissionDescription());
    map.put("objectifs", s.getObjectifs());

    map.put("conventionSigneeEntreprise", s.isConventionSigneeEntreprise());
    map.put("conventionSigneeEncadrantEntreprise", s.isConventionSigneeEncadrantEntreprise());

    User e = s.getCandidature().getEtudiant();
    map.put("etudiantNom", e.getNom());
    map.put("etudiantPrenom", e.getPrenom());

    Offre o = s.getCandidature().getOffre();
    map.put("offreTitre", o.getTitre());
    map.put("entrepriseNom", o.getEntreprise().getRaisonSociale() != null
        ? o.getEntreprise().getRaisonSociale()
        : o.getEntreprise().getNom());

    // Encadrants (null-safe)
    if (s.getEncadrantEntreprise() != null) {
        map.put("encadrantEntrepriseNom",    s.getEncadrantEntreprise().getNom());
        map.put("encadrantEntreprisePrenom", s.getEncadrantEntreprise().getPrenom());
    }
    if (s.getEncadrantAcademique() != null) {
        map.put("encadrantAcademiqueNom",    s.getEncadrantAcademique().getNom());
        map.put("encadrantAcademiquePrenom", s.getEncadrantAcademique().getPrenom());
    }

    return map;
}
    public List<Map<String, Object>> mesConventionsEntreprise(String email) {
    return stageRepo.findByCandidature_Offre_Entreprise_Email(email)
            .stream()
            .map(s -> {
                Map<String, Object> map = new LinkedHashMap<>();

                map.put("id", s.getId());
                map.put("etudiantNom", s.getCandidature().getEtudiant().getNom());
                map.put("etudiantPrenom", s.getCandidature().getEtudiant().getPrenom());
                map.put("offreTitre", s.getCandidature().getOffre().getTitre());

                map.put("dateDebut", s.getDateDebut());
                map.put("dateFin", s.getDateFin());

                map.put("conventionSigneeEntreprise", s.isConventionSigneeEntreprise());
                map.put("conventionSigneeEncadrantEntreprise", s.isConventionSigneeEncadrantEntreprise());

                return map;
            })
            .toList();
}
   public List<Map<String, Object>> getAllStages() {
    return stageRepo.findAll().stream()
            .map(s -> {
                Map<String, Object> m = stageToMap(s);
                m.put("etudiantId", s.getCandidature().getEtudiant().getId());
                return m;
            })
            .toList();
}
   public Map<String, Object> affecterEncadrantEntreprise(Long stageId, Long encadrantId, String email) {
    Stage stage = stageRepo.findById(stageId)
            .orElseThrow(() -> new RuntimeException("Stage introuvable"));
    User encadrant = userRepo.findById(encadrantId)
            .orElseThrow(() -> new RuntimeException("Encadrant introuvable"));
    stage.setEncadrantEntreprise(encadrant);
    return stageToMap(stageRepo.save(stage));
}

public Map<String, Object> affecterEncadrantAcademique(Long stageId, Long encadrantId, String email) {
    Stage stage = stageRepo.findById(stageId)
            .orElseThrow(() -> new RuntimeException("Stage introuvable"));
    User encadrant = userRepo.findById(encadrantId)
            .orElseThrow(() -> new RuntimeException("Encadrant introuvable"));
    stage.setEncadrantAcademique(encadrant);
    return stageToMap(stageRepo.save(stage));
}
// Dans StageService.java - Ajoutez cette méthode
public long countStagesEnCours() {
    /// ✅ Correct - si countByStatut attend StatutStage
return stageRepo.countByStatut(Stage.StatutStage.EN_COURS);
}
}