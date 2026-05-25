package com.smartintern.backend.service;

import com.smartintern.backend.dto.RapportStageDto;
import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RapportStageService {

    private final RapportStageRepository rapportStageRepository;
    private final StageRepository stageRepository;
    private final EtudiantRepository etudiantRepository;
    private final UserRepository userRepository;

    // ── ET-07 : Étudiant rédige un rapport hebdomadaire ───────────────────────
    // ── ET-08 : Étudiant dépose le rapport final ──────────────────────────────
    public RapportStageDto.RapportResponse creerRapport(
            Long stageId, RapportStageDto.RapportRequest request, String email) {

        Etudiant etudiant = etudiantRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));

        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new RuntimeException("Stage non trouvé"));

        if (!stage.getEtudiant().getId().equals(etudiant.getId())) {
            throw new RuntimeException("Non autorisé : ce stage ne vous appartient pas");
        }

        RapportStage.TypeRapport type = RapportStage.TypeRapport.valueOf(request.getType().toUpperCase());

        // Un seul rapport final autorisé par stage
        if (type == RapportStage.TypeRapport.FINAL) {
            boolean exists = !rapportStageRepository
                    .findByStageIdAndType(stageId, RapportStage.TypeRapport.FINAL).isEmpty();
            if (exists) throw new RuntimeException("Un rapport final existe déjà pour ce stage");
        }

        RapportStage rapport = RapportStage.builder()
                .stage(stage)
                .etudiant(etudiant)
                .type(type)
                .titre(request.getTitre())
                .contenu(request.getContenu())
                .semaineDebut(request.getSemaineDebut())
                .statut(RapportStage.Statut.BROUILLON)
                .build();

        rapportStageRepository.save(rapport);
        return toResponse(rapport);
    }

    // ── Étudiant soumet un rapport (BROUILLON → SOUMIS) ───────────────────────
    public RapportStageDto.RapportResponse soumettre(Long rapportId, String email) {
        Etudiant etudiant = etudiantRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));
        RapportStage rapport = rapportStageRepository.findById(rapportId)
                .orElseThrow(() -> new RuntimeException("Rapport non trouvé"));
        if (!rapport.getEtudiant().getId().equals(etudiant.getId())) {
            throw new RuntimeException("Non autorisé");
        }
        if (rapport.getStatut() != RapportStage.Statut.BROUILLON) {
            throw new RuntimeException("Ce rapport a déjà été soumis");
        }
        rapport.setStatut(RapportStage.Statut.SOUMIS);
        rapport.setDateSoumission(LocalDateTime.now());
        rapportStageRepository.save(rapport);
        return toResponse(rapport);
    }

    // ── Étudiant : ses rapports ───────────────────────────────────────────────
    public List<RapportStageDto.RapportResponse> getMesRapports(Long stageId, String email) {
        Etudiant etudiant = etudiantRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));
        return rapportStageRepository.findByStageIdOrderByDateCreationDesc(stageId)
                .stream()
                .filter(r -> r.getEtudiant().getId().equals(etudiant.getId()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Encadrant académique : rapports d'un stage ────────────────────────────
    public List<RapportStageDto.RapportResponse> getRapportsParStage(Long stageId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new RuntimeException("Stage non trouvé"));
        if (stage.getEncadrantAcademique() == null
                || !stage.getEncadrantAcademique().getId().equals(user.getId())) {
            throw new RuntimeException("Non autorisé");
        }
        return rapportStageRepository.findByStageIdOrderByDateCreationDesc(stageId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── EA-01 : Encadrant académique valide un rapport ────────────────────────
    public RapportStageDto.RapportResponse validerRapport(Long rapportId, String email) {
        return changerStatutRapport(rapportId, email, RapportStage.Statut.VALIDE, null);
    }

    // ── EA-02 : Encadrant académique rejette avec commentaire ─────────────────
    public RapportStageDto.RapportResponse commenterRapport(
            Long rapportId, String commentaire, String email) {
        return changerStatutRapport(rapportId, email, RapportStage.Statut.REJETE, commentaire);
    }

    private RapportStageDto.RapportResponse changerStatutRapport(
            Long rapportId, String email, RapportStage.Statut nouveauStatut, String commentaire) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        RapportStage rapport = rapportStageRepository.findById(rapportId)
                .orElseThrow(() -> new RuntimeException("Rapport non trouvé"));
        if (rapport.getStage().getEncadrantAcademique() == null
                || !rapport.getStage().getEncadrantAcademique().getId().equals(user.getId())) {
            throw new RuntimeException("Non autorisé");
        }
        if (rapport.getStatut() != RapportStage.Statut.SOUMIS) {
            throw new RuntimeException("Seuls les rapports soumis peuvent être traités");
        }
        rapport.setStatut(nouveauStatut);
        if (commentaire != null) rapport.setCommentaireEncadrant(commentaire);
        rapportStageRepository.save(rapport);
        return toResponse(rapport);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────
    private RapportStageDto.RapportResponse toResponse(RapportStage r) {
        return RapportStageDto.RapportResponse.builder()
                .id(r.getId())
                .stageId(r.getStage().getId())
                .etudiantId(r.getEtudiant().getId())
                .etudiantNom(r.getEtudiant().getFirstName() + " " + r.getEtudiant().getLastName())
                .type(r.getType().name())
                .titre(r.getTitre())
                .contenu(r.getContenu())
                .semaineDebut(r.getSemaineDebut())
                .statut(r.getStatut().name())
                .commentaireEncadrant(r.getCommentaireEncadrant())
                .dateCreation(r.getDateCreation())
                .dateSoumission(r.getDateSoumission())
                .build();
    }
}
