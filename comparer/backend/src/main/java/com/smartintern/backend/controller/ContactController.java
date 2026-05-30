// ═══════════════════════════════════════════════════════════
// À AJOUTER dans votre UserController.java existant
// (ou créer un nouveau fichier ContactController.java)
// ═══════════════════════════════════════════════════════════

package com.smartintern.backend.controller;

import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ContactController {

    private final UserRepository userRepository;

    /**
     * GET /api/users/contacts
     * Retourne tous les utilisateurs actifs (accessible à tous les rôles authentifiés)
     * Utilisé par la messagerie pour la liste des participants
     */
    @GetMapping("/contacts")
    public ResponseEntity<List<Map<String, Object>>> getContacts(
            @AuthenticationPrincipal User currentUser) {

        List<User> users = userRepository.findAll().stream()
            .filter(u -> u.isEnabled() && u.getId() != currentUser.getId())
            .collect(Collectors.toList());

        // On retourne uniquement les infos nécessaires (pas le mot de passe)
        List<Map<String, Object>> contacts = users.stream().map(u -> {
            String role = u.getRoles().stream()
                .findFirst()
                .map(r -> r.getName().name())
                .orElse("");
            return Map.<String, Object>of(
                "id",     u.getId(),
                "nom",    u.getNom(),
                "prenom", u.getPrenom(),
                "email",  u.getEmail(),
                "role",   role
            );
        }).collect(Collectors.toList());

        return ResponseEntity.ok(contacts);
    }
}