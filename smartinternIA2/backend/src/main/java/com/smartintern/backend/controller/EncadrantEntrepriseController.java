package com.smartintern.backend.controller;

import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.EncadrantEntrepriseRepository;
import com.smartintern.backend.repository.EntrepriseRepository;
import com.smartintern.backend.repository.UserRepository;
import com.smartintern.backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

/**
 * Gestion des encadrants entreprise.
 * POST /api/entreprise/encadrants/creer  — Entreprise crée un encadrant
 * GET  /api/entreprise/encadrants        — Liste des encadrants de l'entreprise
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EncadrantEntrepriseController {

    private final UserRepository                 userRepository;
    private final EntrepriseRepository           entrepriseRepository;
    private final EncadrantEntrepriseRepository  encadrantRepo;
    private final PasswordEncoder                passwordEncoder;
    private final EmailService                   emailService;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ── Entreprise : créer un encadrant entreprise ───────────────────────────
    @PostMapping("/api/entreprise/encadrants/creer")
    public ResponseEntity<Map<String, Object>> creerEncadrant(
            @RequestBody Map<String, String> body,
            Authentication authentication) {

        String emailEncadrant = body.get("email");
        String nom            = body.get("nom");
        String prenom         = body.get("prenom");

        if (emailEncadrant == null || nom == null || prenom == null)
            throw new RuntimeException("Nom, prénom et email sont obligatoires");

        if (userRepository.existsByEmail(emailEncadrant))
            throw new RuntimeException("Cet email est déjà utilisé");

        // Récupérer l'entreprise du compte connecté (via offres ou première entreprise)
        Entreprise entreprise = entrepriseRepository.findByAdminEmail(authentication.getName())
                .orElseGet(() -> entrepriseRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new RuntimeException("Aucune entreprise trouvée")));

        // Générer un mot de passe temporaire
        String tempPassword = genererMotDePasseTemp();

        // Créer l'encadrant avec OTP déjà validé (compte actif directement)
        EncadrantEntreprise encadrant = EncadrantEntreprise.builder()
                .firstName(prenom)
                .lastName(nom)
                .email(emailEncadrant)
                .password(passwordEncoder.encode(tempPassword))
                .role(User.Role.ENCADRANT_ENTREPRISE)
                .statut(User.Statut.ACTIF)
                .entreprise(entreprise)
                .build();

        userRepository.save(encadrant);
        log.info("Encadrant entreprise créé : {} pour entreprise {}", emailEncadrant, entreprise.getNom());

        // Envoi email avec le mot de passe temporaire
        try {
            String html = "<h2>Bonjour " + prenom + ",</h2>"
                + "<p>Un compte encadrant entreprise a été créé pour vous sur <strong>SmartIntern AI</strong>.</p>"
                + "<p><strong>Email :</strong> " + emailEncadrant + "</p>"
                + "<p><strong>Mot de passe temporaire :</strong> <code style='font-size:1.2rem;letter-spacing:4px;'>"
                + tempPassword + "</code></p>"
                + "<p>Connectez-vous et modifiez votre mot de passe dès votre première connexion.</p>";
            emailService.sendHtml(emailEncadrant, "Vos accès SmartIntern AI — Encadrant Entreprise", html);
        } catch (Exception e) {
            log.warn("Email non envoyé à {} : {}", emailEncadrant, e.getMessage());
        }

        return ResponseEntity.ok(Map.of(
            "message",      "Encadrant créé avec succès",
            "email",        emailEncadrant,
            "tempPassword", tempPassword
        ));
    }

    // ── Entreprise : lister ses encadrants ───────────────────────────────────
    @GetMapping("/api/entreprise/encadrants")
    public ResponseEntity<List<Map<String, Object>>> listerEncadrants(
            Authentication authentication) {

        Entreprise entreprise = entrepriseRepository.findByAdminEmail(authentication.getName())
                .orElseGet(() -> entrepriseRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new RuntimeException("Aucune entreprise trouvée")));

        List<Map<String, Object>> result = encadrantRepo.findByEntrepriseId(entreprise.getId())
                .stream().map(e -> Map.<String, Object>of(
                    "id",        e.getId(),
                    "prenom",    e.getFirstName(),
                    "nom",       e.getLastName(),
                    "email",     e.getEmail(),
                    "statut",    e.getStatut() != null ? e.getStatut().name() : "ACTIF"
                )).toList();

        return ResponseEntity.ok(result);
    }

    // ── Ancienne route (api.js apiEntrepriseCreerEncadrant) ──────────────────
    @PostMapping("/api/encadrants/creer")
    public ResponseEntity<Map<String, Object>> creerEncadrantLegacy(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        return creerEncadrant(body, authentication);
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private String genererMotDePasseTemp() {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789@#!";
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++)
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        return sb.toString();
    }
}
