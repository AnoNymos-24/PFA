package com.smartintern.backend.service;

import com.smartintern.backend.dto.StageDto;
import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StageService {

    private final StageRepository stageRepository;
    private final CandidatureRepository candidatureRepository;
    private final UserRepository userRepository;

    // ── Admin : créer un stage depuis une candidature acceptée ────────────────
    public StageDto.StageResponse creerDepuisCandidature(Long candidatureId) {
        Candidature candidature = candidatureRepository.findById(candidatureId)
                .orElseThrow(() -> new RuntimeException("Candidature non trouvée"));

        if (candidature.getStatut() != Candidature.Statut.ACCEPTEE) {
            throw new RuntimeException("La candidature doit être acceptée pour créer un stage");
        }

        if (stageRepository.findByCandidatureId(candidatureId).isPresent()) {
            throw new RuntimeException("Un stage existe déjà pour cette candidature");
        }

        OffreStage offre = candidature.getOffre();

        Stage stage = Stage.builder()
                .candidature(candidature)
                .etudiant(candidature.getEtudiant())
                .entreprise(offre.getEntreprise())
                .dureeMois(offre.getDureeMois())
                .sujet(offre.getTitre())
                .statut(Stage.Statut.EN_COURS)
                .build();

        stageRepository.save(stage);
        return toResponse(stage);
    }

    // ── Assigner les encadrants ───────────────────────────────────────────────
    public StageDto.StageResponse assignerEncadrants(
            Long stageId, StageDto.StageRequest request) {

        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new RuntimeException("Stage non trouvé"));

        if (request.getEncadrantAcademiqueId() != null) {
            User enc = userRepository.findById(request.getEncadrantAcademiqueId())
                    .orElseThrow(() -> new RuntimeException("Encadrant académique non trouvé"));
            stage.setEncadrantAcademique((EncadrantAcademique) enc);
        }
        if (request.getEncadrantEntrepriseId() != null) {
            User enc = userRepository.findById(request.getEncadrantEntrepriseId())
                    .orElseThrow(() -> new RuntimeException("Encadrant entreprise non trouvé"));
            stage.setEncadrantEntreprise((EncadrantEntreprise) enc);
        }

        if (request.getDateDebut() != null) stage.setDateDebut(request.getDateDebut());
        if (request.getDateFin() != null) stage.setDateFin(request.getDateFin());
        if (request.getSujet() != null) stage.setSujet(request.getSujet());
        if (request.getMission() != null) stage.setMission(request.getMission());

        stageRepository.save(stage);
        return toResponse(stage);
    }

    // ── Admin : tous les stages ───────────────────────────────────────────────
    public List<StageDto.StageResponse> getAllStages() {
        return stageRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Étudiant : mon stage actuel ───────────────────────────────────────────
    public List<StageDto.StageResponse> getMesStages(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        return stageRepository.findByEtudiantId(user.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Encadrant académique : mes stagiaires ─────────────────────────────────
    public List<StageDto.StageResponse> getStagesEncadrantAcademique(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        return stageRepository.findByEncadrantAcademiqueId(user.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Encadrant entreprise : mes stagiaires ─────────────────────────────────
    public List<StageDto.StageResponse> getStagesEncadrantEntreprise(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        return stageRepository.findByEncadrantEntrepriseId(user.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── EN-06 : Entreprise signe la convention de stage ───────────────────────
    public StageDto.StageResponse signerConventionEntreprise(Long stageId, String email) {
        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new RuntimeException("Stage non trouvé"));
        if (stage.getCandidature() == null
                || !stage.getCandidature().getOffre().getCreateur().getEmail().equals(email)) {
            throw new RuntimeException("Non autorisé");
        }
        stage.setConventionSigneeEntreprise(true);
        stage.setDateSignatureEntreprise(java.time.LocalDate.now());
        stageRepository.save(stage);
        return toResponse(stage);
    }

    // ── EE-03 : Encadrant entreprise signe la convention ─────────────────────
    public StageDto.StageResponse signerConventionEncadrant(Long stageId, String email) {
        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new RuntimeException("Stage non trouvé"));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        if (stage.getEncadrantEntreprise() == null
                || !stage.getEncadrantEntreprise().getId().equals(user.getId())) {
            throw new RuntimeException("Non autorisé : vous n'êtes pas l'encadrant de ce stage");
        }
        stage.setConventionSigneeEncadrant(true);
        stage.setDateSignatureEncadrant(java.time.LocalDate.now());
        stageRepository.save(stage);
        return toResponse(stage);
    }

    // ── EE-04 : Encadrant entreprise définit la mission du stagiaire ──────────
    public StageDto.StageResponse definirMission(Long stageId, String mission, String email) {
        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new RuntimeException("Stage non trouvé"));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        if (stage.getEncadrantEntreprise() == null
                || !stage.getEncadrantEntreprise().getId().equals(user.getId())) {
            throw new RuntimeException("Non autorisé : vous n'êtes pas l'encadrant de ce stage");
        }
        stage.setMission(mission);
        stageRepository.save(stage);
        return toResponse(stage);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────
    private StageDto.StageResponse toResponse(Stage s) {
        return StageDto.StageResponse.builder()
                .id(s.getId())
                .dateDebut(s.getDateDebut())
                .dateFin(s.getDateFin())
                .dureeMois(s.getDureeMois())
                .statut(s.getStatut().name())
                .sujet(s.getSujet())
                .mission(s.getMission())
                .evaluationFinale(s.getEvaluationFinale())
                .etudiantId(s.getEtudiant().getId())
                .etudiantPrenom(s.getEtudiant().getFirstName())
                .etudiantNom(s.getEtudiant().getLastName())
                .etudiantEmail(s.getEtudiant().getEmail())
                .entrepriseId(s.getEntreprise().getId())
                .entrepriseNom(s.getEntreprise().getNom())
                .encadrantAcademiqueNom(s.getEncadrantAcademique() != null
                        ? s.getEncadrantAcademique().getLastName() : null)
                .encadrantAcademiquePrenom(s.getEncadrantAcademique() != null
                        ? s.getEncadrantAcademique().getFirstName() : null)
                .encadrantEntrepriseNom(s.getEncadrantEntreprise() != null
                        ? s.getEncadrantEntreprise().getFirstName() + " " + s.getEncadrantEntreprise().getLastName()
                        : null)
                .offreTitre(s.getCandidature() != null && s.getCandidature().getOffre() != null
                        ? s.getCandidature().getOffre().getTitre() : null)
                .dureeSemaines(s.getDureeMois() * 4)
                .candidatureId(s.getCandidature() != null ? s.getCandidature().getId() : null)
                .conventionSigneeEntreprise(s.isConventionSigneeEntreprise())
                .dateSignatureEntreprise(s.getDateSignatureEntreprise())
                .conventionSigneeEncadrant(s.isConventionSigneeEncadrant())
                .dateSignatureEncadrant(s.getDateSignatureEncadrant())
                .build();
    }
}
