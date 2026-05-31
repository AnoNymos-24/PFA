package com.smartintern.backend.service;

import com.smartintern.backend.dto.DemandeStageDto;
import com.smartintern.backend.dto.UserDto;
import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository         userRepository;
    private final EtudiantRepository     etudiantRepository;
    private final EntrepriseRepository   entrepriseRepository;
    private final DemandeStageRepository demandeStageRepository;
    private final OffreStageRepository   offreStageRepository;
    private final StageRepository        stageRepository;
    private final CandidatureRepository  candidatureRepository;
    private final RapportStageRepository rapportStageRepository;
    private final ResultatRisqueRepository resultatsRisqueRepository;

    // ── Lister tous les utilisateurs ──────────────────────────────────────────
    public List<UserDto.UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Lister par rôle ───────────────────────────────────────────────────────
    public List<UserDto.UserResponse> getUsersByRole(String role) {
        User.Role userRole = User.Role.valueOf(role.toUpperCase());
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == userRole)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Détail d'un utilisateur ───────────────────────────────────────────────
    public UserDto.UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        return toResponse(user);
    }

    // ── AD-01 : Attribuer un rôle ─────────────────────────────────────────────
    public UserDto.UserResponse changerRole(Long id, String role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        user.setRole(User.Role.valueOf(role.toUpperCase()));
        userRepository.save(user);
        return toResponse(user);
    }

    // ── AD-02 : Changer le statut du compte ───────────────────────────────────
    public UserDto.UserResponse changerStatut(Long id, String statut) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        user.setStatut(User.Statut.valueOf(statut.toUpperCase()));
        userRepository.save(user);
        return toResponse(user);
    }

    // ── AD-05 : Générer une demande de stage pour un étudiant ────────────────
    public DemandeStageDto.DemandeStageResponse creerDemandeStage(
            DemandeStageDto.DemandeStageRequest request) {

        Etudiant etudiant = etudiantRepository.findById(request.getEtudiantId())
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));
        Entreprise entreprise = entrepriseRepository.findById(request.getEntrepriseId())
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée"));

        DemandeStage demande = DemandeStage.builder()
                .etudiant(etudiant)
                .entreprise(entreprise)
                .lettreMotivation(request.getLettreMotivation())
                .typeDemande(request.getTypeDemande())
                .dateDemande(java.time.LocalDate.now())
                .statut(DemandeStage.Statut.EN_ATTENTE)
                .build();

        demandeStageRepository.save(demande);
        return toDemandeResponse(demande);
    }

    // ── Statistiques globales ─────────────────────────────────────────────────
    public Map<String, Long> getStats() {
        long encadrants = userRepository.countByRole(User.Role.ENCADRANT_ACADEMIQUE)
                        + userRepository.countByRole(User.Role.ENCADRANT_ENTREPRISE);
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalUsers",    userRepository.count());
        stats.put("etudiants",     userRepository.countByRole(User.Role.ETUDIANT));
        stats.put("entreprises",   userRepository.countByRole(User.Role.ENTREPRISE));
        stats.put("stagesEnCours", stageRepository.countByStatut(Stage.Statut.EN_COURS));
        stats.put("encadrants",    encadrants);
        stats.put("candidatures",  candidatureRepository.count());
        stats.put("offres",        offreStageRepository.count());
        stats.put("conventions",   stageRepository.count());
        stats.put("rapports",      rapportStageRepository.count());
        return stats;
    }

    // ── Lister tous les stages ────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllStages() {
        return stageRepository.findAll().stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id",                   s.getId());
            m.put("etudiantPrenom",       s.getEtudiant().getFirstName());
            m.put("etudiantNom",          s.getEtudiant().getLastName());
            m.put("etudiantEmail",        s.getEtudiant().getEmail());
            m.put("entrepriseNom",        s.getEntreprise().getNom());
            m.put("offreTitre",           s.getCandidature() != null && s.getCandidature().getOffre() != null
                                            ? s.getCandidature().getOffre().getTitre() : "—");
            m.put("poste",                s.getCandidature() != null && s.getCandidature().getOffre() != null
                                            ? s.getCandidature().getOffre().getTitre() : "—");
            m.put("dureeSemaines",        s.getDureeMois() * 4);
            m.put("dateDebut",            s.getDateDebut());
            m.put("dateFin",              s.getDateFin());
            m.put("statut",               s.getStatut().name());
            m.put("encadrantAcademiqueNom",    s.getEncadrantAcademique() != null
                                                ? s.getEncadrantAcademique().getLastName() : null);
            m.put("encadrantAcademiquePrenom", s.getEncadrantAcademique() != null
                                                ? s.getEncadrantAcademique().getFirstName() : null);
            m.put("semaineActuelle",      s.getSemaineActuelle() != null ? s.getSemaineActuelle() : 1);
            return m;
        }).collect(Collectors.toList());
    }

    // ── Détail d'un stage ─────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Map<String, Object> getStageById(Long id) {
        Stage s = stageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stage non trouvé"));
        Map<String, Object> m = new HashMap<>();
        m.put("id",                   s.getId());
        m.put("etudiantPrenom",       s.getEtudiant().getFirstName());
        m.put("etudiantNom",          s.getEtudiant().getLastName());
        m.put("etudiantEmail",        s.getEtudiant().getEmail());
        m.put("entrepriseNom",        s.getEntreprise().getNom());
        m.put("offreTitre",           s.getCandidature() != null && s.getCandidature().getOffre() != null
                                        ? s.getCandidature().getOffre().getTitre() : "—");
        m.put("poste",                s.getCandidature() != null && s.getCandidature().getOffre() != null
                                        ? s.getCandidature().getOffre().getTitre() : "—");
        m.put("dateDebut",            s.getDateDebut());
        m.put("dateFin",              s.getDateFin());
        m.put("statut",               s.getStatut().name());
        m.put("sujet",                s.getSujet());
        m.put("mission",              s.getMission());
        m.put("encadrantAcademiqueNom",    s.getEncadrantAcademique() != null
                                            ? s.getEncadrantAcademique().getLastName() : null);
        m.put("encadrantAcademiquePrenom", s.getEncadrantAcademique() != null
                                            ? s.getEncadrantAcademique().getFirstName() : null);
        return m;
    }

    // ── Étudiants à risque ────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEtudiantsARisque() {
        return resultatsRisqueRepository
                .findByNiveauRisque(ResultatRisque.NiveauRisque.A_RISQUE)
                .stream().map(r -> {
                    Stage s = r.getStage();
                    Etudiant e = s.getEtudiant();
                    Map<String, Object> m = new HashMap<>();
                    m.put("id",           e.getId());
                    m.put("etudiantPrenom", e.getFirstName());
                    m.put("etudiantNom",  e.getLastName());
                    m.put("universite",   e.getEtablissement() != null
                                            ? e.getEtablissement().getNom() : "—");
                    m.put("entrepriseNom", s.getEntreprise().getNom());
                    m.put("scoreRisque",  r.getScoreEngagement());
                    m.put("raisons",      r.getNiveauRisque().name());
                    m.put("stageId",      s.getId());
                    return m;
                }).collect(Collectors.toList());
    }

    // ── Lister tous les étudiants ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllEtudiants() {
        return etudiantRepository.findAll().stream().map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id",      e.getId());
            m.put("prenom",  e.getFirstName());
            m.put("nom",     e.getLastName());
            m.put("email",   e.getEmail());
            m.put("filiere", e.getFiliere());
            m.put("classe",  e.getClasse());
            m.put("statut",  e.getStatut() != null ? e.getStatut().name() : null);
            m.put("universite", e.getEtablissement() != null ? e.getEtablissement().getNom() : "—");
            return m;
        }).collect(Collectors.toList());
    }

    // ── Lister toutes les entreprises ─────────────────────────────────────────
    public List<Map<String, Object>> getAllEntreprises() {
        return entrepriseRepository.findAll().stream().map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id",             e.getId());
            m.put("nom",            e.getNom());
            m.put("adresse",        e.getAdresse());
            m.put("domaineActivite", e.getDomaineActivite());
            m.put("siteWeb",        e.getSiteWeb());
            return m;
        }).collect(Collectors.toList());
    }

    // ── Admin : lister toutes les demandes de stage ───────────────────────────
    public List<DemandeStageDto.DemandeStageResponse> getAllDemandesStage() {
        return demandeStageRepository.findAll()
                .stream().map(this::toDemandeResponse).collect(Collectors.toList());
    }

    // ── Mapping ───────────────────────────────────────────────────────────────
    public DemandeStageDto.DemandeStageResponse toDemandeResponse(DemandeStage d) {
        return DemandeStageDto.DemandeStageResponse.builder()
                .id(d.getId())
                .statut(d.getStatut().name())
                .dateDemande(d.getDateDemande())
                .lettreMotivation(d.getLettreMotivation())
                .typeDemande(d.getTypeDemande())
                .etudiantId(d.getEtudiant().getId())
                .etudiantNom(d.getEtudiant().getFirstName() + " " + d.getEtudiant().getLastName())
                .entrepriseId(d.getEntreprise().getId())
                .entrepriseNom(d.getEntreprise().getNom())
                .build();
    }

    public UserDto.UserResponse toResponse(User u) {
        return UserDto.UserResponse.builder()
                .id(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .email(u.getEmail())
                .telephone(u.getTelephone())
                .role(u.getRole() != null ? u.getRole().name() : null)
                .statut(u.getStatut() != null ? u.getStatut().name() : null)
                .createdAt(u.getCreatedAt())
                .build();
    }
}
