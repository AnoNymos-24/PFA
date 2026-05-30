package com.smartintern.backend.service;

import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.UserRepository;
import java.time.LocalDateTime;



import java.util.UUID;



@Service
@RequiredArgsConstructor
public class CandidatureService {

    private final CandidatureRepository candidatureRepo;
    private final UserRepository        userRepo;
    private final OffreRepository       offreRepo;
    private final StageRepository       stageRepo;
    private final NotificationService   notificationService;

    // ── POSTULER ──────────────────────────────────────────────
    public Map<String, Object> postuler(String emailEtudiant, Long offreId,
                                        String messageMotivation) {
        User etudiant = userRepo.findByEmail(emailEtudiant)
                .orElseThrow(() -> new RuntimeException("Étudiant introuvable."));
        Offre offre = offreRepo.findById(offreId)
                .orElseThrow(() -> new RuntimeException("Offre introuvable."));

        if (offre.getStatut() != Offre.StatutOffre.ACTIVE)
            throw new RuntimeException("Cette offre n'est plus active.");

        if (candidatureRepo.existsByEtudiantAndOffre(etudiant, offre))
            throw new RuntimeException("Vous avez déjà postulé à cette offre.");

        Candidature c = new Candidature();
        c.setEtudiant(etudiant);
        c.setOffre(offre);
        c.setMessageMotivation(messageMotivation);
        candidatureRepo.save(c);
        notificationService.creerNotification(
    offre.getEntreprise(),
    "Nouvelle candidature reçue",
    c.getEtudiant().getPrenom() + " " + c.getEtudiant().getNom()
    + " a postulé à votre offre \"" + offre.getTitre() + "\""
);

        // Incrémenter le nombre de candidatures de l'offre
        offre.setNombreCandidatures(offre.getNombreCandidatures() + 1);
        offreRepo.save(offre);
        notificationService.creerNotification(
    offre.getEntreprise(),
    "Nouvelle candidature reçue",
    c.getEtudiant().getPrenom() + " " + c.getEtudiant().getNom()
    + " a postulé à votre offre \"" + offre.getTitre() + "\""
);

        return candidatureToMap(c);
    }

    // ── MES CANDIDATURES (étudiant) ───────────────────────────
    public List<Map<String, Object>> mesCandidatures(String emailEtudiant) {
        User etudiant = userRepo.findByEmail(emailEtudiant)
                .orElseThrow(() -> new RuntimeException("Introuvable."));
        return candidatureRepo.findByEtudiantOrderByDatePostulationDesc(etudiant)
                .stream().map(this::candidatureToMap).toList();
    }

    // ── CANDIDATURES REÇUES (entreprise) ─────────────────────
    public List<Map<String, Object>> candidaturesRecues(String emailEntreprise) {
        User entreprise = userRepo.findByEmail(emailEntreprise)
                .orElseThrow(() -> new RuntimeException("Introuvable."));
        return candidatureRepo.findByOffre_EntrepriseOrderByDatePostulationDesc(entreprise)
                .stream().map(this::candidatureToMap).toList();
    }

    // ── RÉPONDRE (accepter/refuser) ───────────────────────────
    public Map<String, Object> repondre(Long candidatureId, String action,
                                     String commentaire, String emailEntreprise) {
    Candidature c = candidatureRepo.findById(candidatureId)
            .orElseThrow(() -> new RuntimeException("Candidature introuvable."));

    if (!c.getOffre().getEntreprise().getEmail().equals(emailEntreprise))
        throw new RuntimeException("Accès non autorisé.");

    if ("accepter".equals(action)) {
        c.setStatut(Candidature.StatutCandidature.ACCEPTEE);
        c.setCommentaireEntreprise(commentaire);
        candidatureRepo.save(c);

        // ✅ REMPLACEZ L'ANCIEN BLOC PAR CELUI-CI :
        boolean exists = stageRepo.findByCandidature_Id(c.getId()).isPresent();
        if (!exists) {
            Stage stage = new Stage();
            stage.setCandidature(c);
            stage.setDateDebut(LocalDate.now());
            stage.setDateFin(LocalDate.now().plusMonths(
                parseDuree(c.getOffre().getDuree())
            ));
            stageRepo.save(stage);
            System.out.println("✅ Stage créé via repondre() pour candidature " + c.getId());
        }

    } else {
        c.setStatut(Candidature.StatutCandidature.REFUSEE);
        c.setCommentaireEntreprise(commentaire);
        candidatureRepo.save(c);
    }

    return candidatureToMap(c);
}

    // ── UTILITAIRES ───────────────────────────────────────────
    private int parseDuree(String duree) {
        if (duree == null) return 3;
        try { return Integer.parseInt(duree.replaceAll("[^0-9]", "")); }
        catch (Exception e) { return 3; }
    }

    public Map<String, Object> candidatureToMap(Candidature c) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",               c.getId());
        map.put("statut",           c.getStatut().name());
        map.put("datePostulation",  c.getDatePostulation().toString());
        map.put("messageMotivation",c.getMessageMotivation() != null ? c.getMessageMotivation() : "");
        map.put("commentaire",      c.getCommentaireEntreprise() != null ? c.getCommentaireEntreprise() : "");
        map.put("cvPath", c.getEtudiant().getCvFilePath());
        // Étudiant
        map.put("etudiantId",       c.getEtudiant().getId());
        map.put("nom",              c.getEtudiant().getNom());
        map.put("prenom",           c.getEtudiant().getPrenom());
        map.put("email",            c.getEtudiant().getEmail());
        map.put("universite",       c.getEtudiant().getUniversite() != null ? c.getEtudiant().getUniversite() : "");
        map.put("niveau",           c.getEtudiant().getNiveau() != null ? c.getEtudiant().getNiveau() : "");
        map.put("hasCv",            c.getEtudiant().getCvFilePath() != null);
        // Offre
        map.put("offreId",          c.getOffre().getId());
        map.put("offreTitre",       c.getOffre().getTitre());
        map.put("entrepriseNom",    c.getOffre().getEntreprise().getRaisonSociale() != null
                                    ? c.getOffre().getEntreprise().getRaisonSociale()
                                    : c.getOffre().getEntreprise().getNom());
        return map;
    }
    // ── CHANGER STATUT (entreprise) ───────────────────────────
public Map<String, Object> changerStatut(Long candidatureId, String statut,
                                          String commentaire, String dateEntretien,
                                          String lieuEntretien, String emailEntreprise) {
    Candidature c = candidatureRepo.findById(candidatureId)
            .orElseThrow(() -> new RuntimeException("Candidature introuvable."));

    if (!c.getOffre().getEntreprise().getEmail().equals(emailEntreprise))
        throw new RuntimeException("Accès non autorisé.");

    Candidature.StatutCandidature nouveauStatut =
            Candidature.StatutCandidature.valueOf(statut);

    c.setStatut(nouveauStatut);
    if (commentaire != null && !commentaire.isBlank())
        c.setCommentaireEntreprise(commentaire);

    candidatureRepo.save(c);

    // Si acceptée → créer le stage automatiquement
   // Dans changerStatut() ET repondre() — ajoutez cette logique dans LES DEUX :
// Dans changerStatut() :
// APRÈS candidatureRepo.save(c);
// REMPLACER le bloc if (nouveauStatut == ACCEPTEE) par :

if (nouveauStatut == Candidature.StatutCandidature.ACCEPTEE) {
    boolean exists = stageRepo.findByCandidature_Id(c.getId()).isPresent();
    if (!exists) {
        Stage stage = new Stage();
        stage.setCandidature(c);
        stage.setDateDebut(LocalDate.now());
        stage.setDateFin(LocalDate.now().plusMonths(
            parseDuree(c.getOffre().getDuree())
        ));
        stageRepo.save(stage);
    }
    // ← NOTIFICATION ÉTUDIANT
    notificationService.creerNotification(
        c.getEtudiant(),
        "Candidature acceptée 🎉",
        "Votre candidature pour \"" + c.getOffre().getTitre()
        + "\" a été acceptée. Votre stage commence !"
    );

} else if (nouveauStatut == Candidature.StatutCandidature.REFUSEE) {
    // ← NOTIFICATION ÉTUDIANT
    notificationService.creerNotification(
        c.getEtudiant(),
        "Candidature refusée",
        "Votre candidature pour \"" + c.getOffre().getTitre()
        + "\" n'a pas été retenue."
    );

} else if (nouveauStatut == Candidature.StatutCandidature.ENTRETIEN) {
    // ← NOTIFICATION ÉTUDIANT
    notificationService.creerNotification(
        c.getEtudiant(),
        "Entretien planifié 📅",
        "Un entretien a été planifié pour votre candidature à \""
        + c.getOffre().getTitre() + "\""
    );
}

    return candidatureToMap(c);  // ← manquait
}
// Dans CandidatureService.java
   public void saveCvPath(String username, String fileName) {
    User u = userRepo.findByEmail(username)
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

    // Vérifier que l'utilisateur a le rôle étudiant
    boolean estEtudiant = u.getRoles().stream()
            .anyMatch(r -> "ROLE_ETUDIANT".equals(r.getName().name())); // getName() renvoie l'Enum

    if (!estEtudiant) {
        throw new RuntimeException("Seuls les étudiants peuvent uploader un CV");
    }

    // Sauvegarder le CV
    u.setCvFilePath(fileName);      // ← utiliser cvFilePath comme dans ton Entity
    u.setCvUploadedAt(LocalDateTime.now());

    userRepo.save(u);
}
}                                