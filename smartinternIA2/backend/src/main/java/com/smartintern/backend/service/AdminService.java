package com.smartintern.backend.service;

import com.smartintern.backend.dto.DemandeStageDto;
import com.smartintern.backend.dto.UserDto;
import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final EtudiantRepository etudiantRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final DemandeStageRepository demandeStageRepository;

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
                .dateDemande(LocalDate.now())
                .statut(DemandeStage.Statut.EN_ATTENTE)
                .build();

        demandeStageRepository.save(demande);
        return toDemandeResponse(demande);
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
