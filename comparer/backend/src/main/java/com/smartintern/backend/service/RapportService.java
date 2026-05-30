package com.smartintern.backend.service;

import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RapportService {

    private final RapportRepository rapportRepo;
    private final StageRepository   stageRepo;
    private final UserRepository    userRepo;

    // ── DÉPOSER UN RAPPORT ────────────────────────────────────
    // Dans RapportService.java

public Map<String, Object> deposer(String emailEtudiant, Long stageId,
                                    String type, Integer semaine,
                                    String titre, String contenu) {
    User etudiant = userRepo.findByEmail(emailEtudiant)
            .orElseThrow(() -> new RuntimeException("Introuvable."));
    Stage stage = stageRepo.findById(stageId)
            .orElseThrow(() -> new RuntimeException("Stage introuvable."));

    if (!stage.getCandidature().getEtudiant().getId().equals(etudiant.getId()))
        throw new RuntimeException("Accès non autorisé.");

    Rapport r = new Rapport();
    r.setStage(stage);
    r.setType(Rapport.TypeRapport.valueOf(type));
    r.setSemaine(semaine);
    r.setTitre(titre != null ? titre : "Rapport semaine " + semaine);
    r.setContenu(contenu);
    r.setStatut(Rapport.StatutRapport.EN_ATTENTE);  // ← Ajoutez cette ligne
    r.setDateDepot(LocalDateTime.now());            // ← Ajoutez cette ligne
    
    rapportRepo.save(r);

    // Mettre à jour la semaine actuelle du stage
    if (semaine != null && semaine > stage.getSemaineActuelle()) {
        stage.setSemaineActuelle(semaine);
        stageRepo.save(stage);
    }

    return rapportToMap(r);
}

    // ── MES RAPPORTS (étudiant) ───────────────────────────────
 public List<Map<String, Object>> mesRapports(String email) {
    return rapportRepo.findAllByStage_Candidature_Etudiant_Email(email)
            .stream()
            .map(this::rapportToMap)
            .toList();
}

    // ── RAPPORTS REÇUS (entreprise) ───────────────────────────
    public List<Map<String, Object>> rapportsEntreprise(String email) {
        return rapportRepo.findByEntrepriseEmail(email)
                .stream().map(this::rapportToMap).toList();
    }

    // ── RAPPORTS ENCADRANT ACADÉMIQUE ─────────────────────────
    public List<Map<String, Object>> rapportsEncadrantAcademique(String email) {
        return rapportRepo.findByEncadrantAcademiqueEmail(email)
                .stream().map(this::rapportToMap).toList();
    }

    // ── VALIDER UN RAPPORT ────────────────────────────────────
    public Map<String, Object> valider(Long rapportId, String commentaire,
                                        String emailValidateur) {
        Rapport r = rapportRepo.findById(rapportId)
                .orElseThrow(() -> new RuntimeException("Rapport introuvable."));
        r.setStatut(Rapport.StatutRapport.VALIDE);
        r.setDateValidation(LocalDateTime.now());

        // Détecter si c'est l'encadrant entreprise ou académique
        Stage stage = r.getStage();
        if (stage.getEncadrantEntreprise() != null &&
            stage.getEncadrantEntreprise().getEmail().equals(emailValidateur)) {
            r.setCommentaireEncadrantEntreprise(commentaire);
        } else {
            r.setCommentaireEncadrantAcademique(commentaire);
        }
        return rapportToMap(rapportRepo.save(r));
    }

    // ── COMMENTER ─────────────────────────────────────────────
    public Map<String, Object> commenter(Long rapportId, String commentaire,
                                          String emailValidateur) {
        Rapport r = rapportRepo.findById(rapportId)
                .orElseThrow(() -> new RuntimeException("Rapport introuvable."));
        Stage stage = r.getStage();
        if (stage.getEncadrantEntreprise() != null &&
            stage.getEncadrantEntreprise().getEmail().equals(emailValidateur)) {
            r.setCommentaireEncadrantEntreprise(commentaire);
        } else {
            r.setCommentaireEncadrantAcademique(commentaire);
        }
        return rapportToMap(rapportRepo.save(r));
    }

    // ── UTILITAIRES ───────────────────────────────────────────
   public Map<String, Object> rapportToMap(Rapport r) {
    Map<String, Object> map = new HashMap<>();
    map.put("id",          r.getId());
    map.put("type",        r.getType().name());
    map.put("statut",      r.getStatut().name());  // Retournera "EN_ATTENTE", "VALIDE" ou "REJETE"
    map.put("semaine",     r.getSemaine());
    map.put("titre",       r.getTitre());
    map.put("contenu",     r.getContenu() != null ? r.getContenu() : "");
    map.put("dateDepot",   r.getDateDepot().toString());
    map.put("dateValidation", r.getDateValidation() != null ? r.getDateValidation().toString() : null);
    map.put("commentaireEntreprise",        r.getCommentaireEncadrantEntreprise() != null ? r.getCommentaireEncadrantEntreprise() : "");
    map.put("commentaireEncadrantAcademique", r.getCommentaireEncadrantAcademique() != null ? r.getCommentaireEncadrantAcademique() : "");

    // Infos étudiant
    User etudiant = r.getStage().getCandidature().getEtudiant();
    map.put("etudiantNom",    etudiant.getNom());
    map.put("etudiantPrenom", etudiant.getPrenom());
    map.put("offreTitre",     r.getStage().getCandidature().getOffre().getTitre());
    map.put("stageId",        r.getStage().getId());
    return map;
}
    // Ajoutez cette méthode pour rejeter un rapport
public Map<String, Object> rejeter(Long rapportId, String commentaire,
                                    String emailValidateur) {
    Rapport r = rapportRepo.findById(rapportId)
            .orElseThrow(() -> new RuntimeException("Rapport introuvable."));
    r.setStatut(Rapport.StatutRapport.REJETE);
    r.setDateValidation(LocalDateTime.now());

    Stage stage = r.getStage();
    if (stage.getEncadrantEntreprise() != null &&
        stage.getEncadrantEntreprise().getEmail().equals(emailValidateur)) {
        r.setCommentaireEncadrantEntreprise(commentaire);
    } else {
        r.setCommentaireEncadrantAcademique(commentaire);
    }
    return rapportToMap(rapportRepo.save(r));
}
}