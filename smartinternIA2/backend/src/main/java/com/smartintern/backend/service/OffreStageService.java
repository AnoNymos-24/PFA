package com.smartintern.backend.service;

import com.smartintern.backend.dto.OffreStageDto;
import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OffreStageService {

    private final OffreStageRepository offreStageRepository;
    private final UserRepository userRepository;
    private final EntrepriseRepository entrepriseRepository;

    // ── Entreprise : créer une offre ──────────────────────────────────────────
    public OffreStageDto.OffreStageResponse createOffre(
            OffreStageDto.OffreStageRequest request, String email) {

        User createur = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé"));

        // L'entreprise est trouvée via l'AdministrateurEntreprise connecté
        // Pour l'instant on cherche via le user_id dans la table entreprises
        // (à affiner quand la relation AdministrateurEntreprise ↔ Entreprise sera complète)
        Entreprise entreprise = entrepriseRepository.findById(
                getEntrepriseIdFromUser(createur))
                .orElseThrow(() -> new RuntimeException("Entreprise introuvable pour cet utilisateur"));

        OffreStage offre = OffreStage.builder()
                .titre(request.getTitre())
                .description(request.getDescription())
                .typeStage(request.getTypeStage())
                .domaine(request.getDomaine())
                .theme(request.getTheme())
                .localisation(request.getLocalisation())
                .niveauRequis(request.getNiveauRequis())
                .dureeMois(request.getDureeMois())
                .dateExpiration(request.getDateExpiration())
                .nombrePlaces(request.getNombrePlaces())
                .remuneration(request.getRemuneration() != null ? request.getRemuneration() : false)
                .statut(OffreStage.Statut.ACTIVE)
                .statutValidation(OffreStage.StatutValidation.EN_ATTENTE) // Nécessite validation admin
                .entreprise(entreprise)
                .createur(createur)
                .build();

        offreStageRepository.save(offre);
        return toResponse(offre);
    }

    // ── Entreprise : mes offres ───────────────────────────────────────────────
    public List<OffreStageDto.OffreStageResponse> getMesOffres(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé"));
        Long entrepriseId = getEntrepriseIdFromUser(user);
        return offreStageRepository.findByEntrepriseId(entrepriseId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Étudiant : offres actives et validées avec filtres ───────────────────
    public List<OffreStageDto.OffreStageResponse> searchOffres(
            String domaine, String localisation, String typeStage, String niveauRequis) {
        return offreStageRepository.searchOffres(domaine, localisation, typeStage, niveauRequis)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<OffreStageDto.OffreStageResponse> getAllOffresActives() {
        return offreStageRepository.findByStatutValidation(OffreStage.StatutValidation.VALIDEE)
                .stream()
                .filter(o -> o.getStatut() == OffreStage.Statut.ACTIVE)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Page<OffreStageDto.OffreStageResponse> getAllOffresActivesPaginees(Pageable pageable) {
        return offreStageRepository.findByStatutAndStatutValidation(
                OffreStage.Statut.ACTIVE, OffreStage.StatutValidation.VALIDEE, pageable)
                .map(this::toResponse);
    }

    // ── Admin : valider / rejeter une offre ──────────────────────────────────
    public OffreStageDto.OffreStageResponse validerOffre(Long id, boolean approuve) {
        OffreStage offre = offreStageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Offre non trouvée"));
        offre.setStatutValidation(approuve
                ? OffreStage.StatutValidation.VALIDEE
                : OffreStage.StatutValidation.REJETEE);
        offreStageRepository.save(offre);
        return toResponse(offre);
    }

    // ── Entreprise : modifier une offre ──────────────────────────────────────
    public OffreStageDto.OffreStageResponse updateOffre(
            Long id, OffreStageDto.OffreStageRequest request, String email) {

        OffreStage offre = offreStageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Offre non trouvée"));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé"));

        if (!offre.getCreateur().getEmail().equals(email)) {
            throw new RuntimeException("Non autorisé");
        }

        offre.setTitre(request.getTitre());
        offre.setDescription(request.getDescription());
        offre.setTypeStage(request.getTypeStage());
        offre.setDomaine(request.getDomaine());
        offre.setTheme(request.getTheme());
        offre.setLocalisation(request.getLocalisation());
        offre.setNiveauRequis(request.getNiveauRequis());
        offre.setDureeMois(request.getDureeMois());
        offre.setDateExpiration(request.getDateExpiration());
        offre.setNombrePlaces(request.getNombrePlaces());
        offre.setRemuneration(request.getRemuneration());
        // Re-soumettre à validation après modification
        offre.setStatutValidation(OffreStage.StatutValidation.EN_ATTENTE);

        offreStageRepository.save(offre);
        return toResponse(offre);
    }

    // ── Entreprise : supprimer une offre ─────────────────────────────────────
    public void deleteOffre(Long id, String email) {
        OffreStage offre = offreStageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Offre non trouvée"));
        if (!offre.getCreateur().getEmail().equals(email)) {
            throw new RuntimeException("Non autorisé");
        }
        offreStageRepository.delete(offre);
    }

    // ── Mapping entité → DTO ─────────────────────────────────────────────────
    public OffreStageDto.OffreStageResponse toResponse(OffreStage o) {
        return OffreStageDto.OffreStageResponse.builder()
                .id(o.getId())
                .titre(o.getTitre())
                .typeStage(o.getTypeStage())
                .domaine(o.getDomaine())
                .theme(o.getTheme())
                .description(o.getDescription())
                .localisation(o.getLocalisation())
                .niveauRequis(o.getNiveauRequis())
                .dureeMois(o.getDureeMois())
                .statut(o.getStatut().name())
                .statutValidation(o.getStatutValidation().name())
                .datePublication(o.getDatePublication())
                .dateExpiration(o.getDateExpiration())
                .nombrePlaces(o.getNombrePlaces())
                .remuneration(o.getRemuneration())
                .entrepriseId(o.getEntreprise() != null ? o.getEntreprise().getId() : null)
                .entrepriseNom(o.getEntreprise() != null ? o.getEntreprise().getNom() : null)
                .createdAt(o.getCreatedAt())
                .build();
    }

    // ── Admin : liste par statut validation ────────────────────────────────
    public List<OffreStageDto.OffreStageResponse> getAllOffresParStatutValidation(String statut) {
        return offreStageRepository
                .findByStatutValidation(OffreStage.StatutValidation.valueOf(statut))
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Utilitaire : récupérer l'entreprise_id d'un user ────────────────────
    // TODO : à remplacer par une vraie relation quand AdministrateurEntreprise sera finalisé
    private Long getEntrepriseIdFromUser(User user) {
        // Implémentation temporaire : cherche la première entreprise
        // À remplacer par : ((AdministrateurEntreprise) user).getEntreprise().getId()
        return entrepriseRepository.findAll().stream()
                .findFirst()
                .map(Entreprise::getId)
                .orElseThrow(() -> new RuntimeException(
                        "Aucune entreprise associée. Créez d'abord une entreprise."));
    }
}

// méthode appelée par OffreStageController /api/admin/offres/en-attente
// (ajoutée séparément pour éviter de modifier le fichier principal)