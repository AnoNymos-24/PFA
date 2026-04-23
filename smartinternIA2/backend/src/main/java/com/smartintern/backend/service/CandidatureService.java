package com.smartintern.backend.service;

import com.smartintern.backend.dto.CandidatureDto;
import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CandidatureService {

    private final CandidatureRepository candidatureRepository;
    private final EtudiantRepository etudiantRepository;
    private final OffreStageRepository offreStageRepository;

    // ── Étudiant : postuler à une offre ──────────────────────────────────────
    public CandidatureDto.CandidatureResponse postuler(
            CandidatureDto.CandidatureRequest request, String email) {

        Etudiant etudiant = etudiantRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));

        OffreStage offre = offreStageRepository.findById(request.getOffreId())
                .orElseThrow(() -> new RuntimeException("Offre non trouvée"));

        if (offre.getStatut() != OffreStage.Statut.ACTIVE
                || offre.getStatutValidation() != OffreStage.StatutValidation.VALIDEE) {
            throw new RuntimeException("Cette offre n'est pas disponible");
        }

        if (candidatureRepository.existsByEtudiantIdAndOffreId(etudiant.getId(), offre.getId())) {
            throw new RuntimeException("Vous avez déjà postulé à cette offre");
        }

        Candidature candidature = Candidature.builder()
                .etudiant(etudiant)
                .offre(offre)
                .statut(Candidature.Statut.EN_ATTENTE)
                .statutLabel("En attente de réponse")
                .dateCandidature(LocalDate.now())
                .lettreMotivation(request.getLettreMotivation())
                .build();

        candidatureRepository.save(candidature);
        return toResponse(candidature);
    }

    // ── Étudiant : mes candidatures ───────────────────────────────────────────
    public List<CandidatureDto.CandidatureResponse> getMesCandidatures(String email) {
        Etudiant etudiant = etudiantRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));
        return candidatureRepository.findByEtudiantId(etudiant.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Entreprise : candidatures reçues pour une offre ───────────────────────
    public List<CandidatureDto.CandidatureResponse> getCandidaturesParOffre(Long offreId) {
        return candidatureRepository.findByOffreId(offreId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Entreprise : décision (accepter / refuser) ────────────────────────────
    public CandidatureDto.CandidatureResponse decider(
            Long id, CandidatureDto.CandidatureDecisionRequest request, String email) {

        Candidature candidature = candidatureRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Candidature non trouvée"));

        // Vérifier que l'entreprise est bien propriétaire de l'offre
        if (!candidature.getOffre().getCreateur().getEmail().equals(email)) {
            throw new RuntimeException("Non autorisé");
        }

        Candidature.Statut nouveauStatut = Candidature.Statut.valueOf(request.getStatut());
        candidature.setStatut(nouveauStatut);
        candidature.setDateReponse(LocalDate.now());
        candidature.setCommentaireEntreprise(request.getCommentaire());
        candidature.setStatutLabel(
                nouveauStatut == Candidature.Statut.ACCEPTEE ? "Candidature acceptée" : "Candidature refusée");

        candidatureRepository.save(candidature);
        return toResponse(candidature);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────
    private CandidatureDto.CandidatureResponse toResponse(Candidature c) {
        return CandidatureDto.CandidatureResponse.builder()
                .id(c.getId())
                .statut(c.getStatut().name())
                .statutLabel(c.getStatutLabel())
                .dateCandidature(c.getDateCandidature())
                .dateReponse(c.getDateReponse())
                .lettreMotivation(c.getLettreMotivation())
                .urlDemandeStage(c.getUrlDemandeStage())
                .scoreMatching(c.getScoreMatching())
                .commentaireEntreprise(c.getCommentaireEntreprise())
                .commentaireAdministration(c.getCommentaireAdministration())
                .offreId(c.getOffre().getId())
                .offreTitre(c.getOffre().getTitre())
                .entrepriseNom(c.getOffre().getEntreprise() != null
                        ? c.getOffre().getEntreprise().getNom() : null)
                .etudiantId(c.getEtudiant().getId())
                .etudiantNom(c.getEtudiant().getFirstName() + " " + c.getEtudiant().getLastName())
                .build();
    }
}
