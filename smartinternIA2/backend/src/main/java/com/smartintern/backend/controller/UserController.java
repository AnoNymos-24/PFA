package com.smartintern.backend.controller;

import com.smartintern.backend.entity.Etudiant;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.EtudiantRepository;
import com.smartintern.backend.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRepository userRepository;
    private final EtudiantRepository etudiantRepository;
    private final PasswordEncoder passwordEncoder;

    // ── Profil de l'utilisateur connecté ─────────────────────────────────────
    @GetMapping("/api/users/me")
    public ResponseEntity<?> getMe(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("id",        user.getId());
        response.put("firstName", user.getFirstName());
        response.put("lastName",  user.getLastName());
        response.put("email",     user.getEmail());
        response.put("role",      user.getRole() != null ? user.getRole().name() : null);
        response.put("statut",    user.getStatut() != null ? user.getStatut().name() : null);
        response.put("telephone", user.getTelephone());
        if (user.getRole() == User.Role.ETUDIANT) {
            etudiantRepository.findByEmail(email).ifPresent(e -> {
                response.put("filiere", e.getFiliere());
                response.put("classe",  e.getClasse());
                response.put("codeEtudiant", e.getCodeEtudiant());
            });
        }
        return ResponseEntity.ok(response);
    }

    // ── Mettre à jour le profil de l'utilisateur connecté ────────────────────
    @PutMapping("/api/users/me")
    public ResponseEntity<?> updateProfile(
            @RequestBody ProfileUpdateRequest request,
            Authentication authentication) {

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (request.getFirstName()   != null) user.setFirstName(request.getFirstName());
        if (request.getLastName()    != null) user.setLastName(request.getLastName());
        if (request.getTelephone()   != null) user.setTelephone(request.getTelephone());
        if (request.getPhotoProfil() != null) user.setPhotoProfil(request.getPhotoProfil());

        userRepository.save(user);

        // Mise à jour des champs étudiant si applicable
        if (user.getRole() == User.Role.ETUDIANT) {
            etudiantRepository.findByEmail(email).ifPresent(etudiant -> {
                if (request.getFiliere() != null) etudiant.setFiliere(request.getFiliere());
                if (request.getClasse() != null)  etudiant.setClasse(request.getClasse());
                etudiantRepository.save(etudiant);
            });
        }

        return ResponseEntity.ok(Map.of("message", "Profil mis à jour"));
    }

    // ── Changer le mot de passe ───────────────────────────────────────────────
    @PutMapping("/api/users/change-password")
    public ResponseEntity<?> changePassword(
            @RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword()))
            return ResponseEntity.badRequest().body(Map.of("error", "Ancien mot de passe incorrect"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Mot de passe modifié"));
    }

    @Data
    public static class ProfileUpdateRequest {
        private String firstName;
        private String lastName;
        private String telephone;
        private String filiere;
        private String classe;
        private String photoProfil;
    }

    @Data
    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;
    }
}
