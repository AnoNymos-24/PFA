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

    // ── Mettre à jour le profil de l'utilisateur connecté ────────────────────
    @PutMapping("/api/users/me")
    public ResponseEntity<?> updateProfile(
            @RequestBody ProfileUpdateRequest request,
            Authentication authentication) {

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (request.getTelephone() != null)
            user.setTelephone(request.getTelephone());

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
        private String telephone;
        private String filiere;
        private String classe;
    }

    @Data
    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;
    }
}
