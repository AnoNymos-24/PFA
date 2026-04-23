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
                .etudiantNom(s.getEtudiant().getFirstName() + " " + s.getEtudiant().getLastName())
                .entrepriseId(s.getEntreprise().getId())
                .entrepriseNom(s.getEntreprise().getNom())
                .encadrantAcademiqueNom(s.getEncadrantAcademique() != null
                        ? s.getEncadrantAcademique().getFirstName() + " " + s.getEncadrantAcademique().getLastName()
                        : null)
                .encadrantEntrepriseNom(s.getEncadrantEntreprise() != null
                        ? s.getEncadrantEntreprise().getFirstName() + " " + s.getEncadrantEntreprise().getLastName()
                        : null)
                .candidatureId(s.getCandidature() != null ? s.getCandidature().getId() : null)
                .build();
    }
}
