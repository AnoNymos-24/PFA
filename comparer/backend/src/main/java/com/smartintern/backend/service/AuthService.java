package com.smartintern.backend.service;

import com.smartintern.backend.dto.*;
import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.*;
import com.smartintern.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository     userRepo;
    private final RoleRepository     roleRepo;
    private final PasswordEncoder    passwordEncoder;
    private final JwtService         jwtService;
    private final AuthenticationManager authManager;
    private final EmailService       emailService;

    // ── INSCRIPTION ───────────────────────────────────────────
    public String register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.getEmail()))
            throw new RuntimeException("Cet e-mail est déjà utilisé.");

        User user = new User();
        user.setNom(req.getNom());
        user.setPrenom(req.getPrenom());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));

        if (req.getUniversite()      != null) user.setUniversite(req.getUniversite());
        if (req.getCarteEtudiant()   != null) user.setCarteEtudiant(req.getCarteEtudiant());
        if (req.getNiveau()          != null) user.setNiveau(req.getNiveau());
        if (req.getSpecialite()      != null) user.setSpecialite(req.getSpecialite());
        if (req.getRaisonSociale()   != null) user.setRaisonSociale(req.getRaisonSociale());
        if (req.getMatriculeFiscal() != null) user.setMatriculeFiscal(req.getMatriculeFiscal());
        if (req.getSecteur()         != null) user.setSecteur(req.getSecteur());
        if (req.getAdresse()         != null) user.setAdresse(req.getAdresse());
        if (req.getVille()           != null) user.setVille(req.getVille());
        if (req.getTelephone()       != null) user.setTelephone(req.getTelephone());

        String roleName = "ROLE_" + req.getRole().toUpperCase();
        Role role = roleRepo.findByName(Role.RoleName.valueOf(roleName))
                .orElseThrow(() -> new RuntimeException("Rôle introuvable : " + roleName));
        user.getRoles().add(role);

        String code = generateCode();
        user.setVerificationCode(code);
        user.setVerificationCodeExpiry(LocalDateTime.now().plusMinutes(10));
        user.setEmailVerified(false);
        user.setFirstLogin(false);

        userRepo.save(user);
        emailService.sendVerificationCode(user.getEmail(), user.getPrenom(), code);

        return "Code de vérification envoyé à " + user.getEmail();
    }

    // ── VÉRIFICATION EMAIL ────────────────────────────────────
  public AuthResponse verifyEmail(String email, String code) {
    User user = userRepo.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Utilisateur introuvable."));

    if (user.isEmailVerified())
        throw new RuntimeException("Ce compte est déjà vérifié.");

    if (user.getVerificationCode() == null ||
        !user.getVerificationCode().equals(code))
        throw new RuntimeException("Code incorrect.");

    if (user.getVerificationCodeExpiry().isBefore(LocalDateTime.now()))
        throw new RuntimeException("Code expiré. Veuillez en demander un nouveau.");

    user.setEmailVerified(true);
    user.setVerificationCode(null);
    user.setVerificationCodeExpiry(null);
    userRepo.save(user);

    // ✅ Récupérer le rôle
    String role = user.getRoles().iterator().next().getName().name();
    
    // ✅ Générer le token AVEC le rôle
    String token = jwtService.generateToken(user.getEmail(), role, user.getId());

    return new AuthResponse(token, role, user.getNom(),
            user.getPrenom(), user.getId(), user.isFirstLogin());
}

    // ── RENVOI DU CODE ────────────────────────────────────────
    public void resendCode(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable."));

        if (user.isEmailVerified())
            throw new RuntimeException("Ce compte est déjà vérifié.");

        String code = generateCode();
        user.setVerificationCode(code);
        user.setVerificationCodeExpiry(LocalDateTime.now().plusMinutes(10));
        userRepo.save(user);

        emailService.sendVerificationCode(user.getEmail(), user.getPrenom(), code);
    }

    // ── CONNEXION ─────────────────────────────────────────────
public AuthResponse login(LoginRequest req) {
    // 1. Vérifier que l'utilisateur existe
    User user = userRepo.findByEmail(req.getEmail())
            .orElseThrow(() -> new RuntimeException("Email ou mot de passe incorrect."));

    // 2. Vérifier que l'email est vérifié
    if (!user.isEmailVerified())
        throw new RuntimeException("Veuillez vérifier votre e-mail avant de vous connecter.");

    // 3. Vérifier que le compte est actif
    if (!user.isEnabled())
        throw new RuntimeException("Votre compte a été désactivé. Contactez l'administrateur.");

    // 4. Authentifier
    try {
        authManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                req.getEmail(), req.getPassword()
            )
        );
    } catch (BadCredentialsException e) {
        throw new RuntimeException("Email ou mot de passe incorrect.");
    }

    // ✅ 5. Récupérer le rôle
    String role = user.getRoles().iterator().next().getName().name();
    
    // ✅ 6. Générer le token AVEC le rôle et l'ID
    String token = jwtService.generateToken(user.getEmail(), role, user.getId());

    return new AuthResponse(
        token, role,
        user.getNom(), user.getPrenom(),
        user.getId(), user.isFirstLogin()
    );
}
    // ── CHANGEMENT MOT DE PASSE ───────────────────────────────
    public void changePassword(String email, String oldPassword, String newPassword) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable."));

        if (!passwordEncoder.matches(oldPassword, user.getPassword()))
            throw new RuntimeException("Mot de passe temporaire incorrect.");

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFirstLogin(false);
        userRepo.save(user);
    }

   // ── UTILITAIRE ────────────────────────────────────────────
    private String generateCode() {
        return String.format("%04d", new Random().nextInt(10000));
    }

    // ← AJOUTER CETTE MÉTHODE
    private String genererMotDePasseTemporaire() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$!";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public Map<String, Object> registerByAdmin(String nom, String prenom,
                                               String email, String role) {
        if (userRepo.existsByEmail(email))
            throw new RuntimeException("Un compte avec cet email existe déjà.");

        String tempPassword = genererMotDePasseTemporaire();

        User user = new User();
        user.setNom(nom);
        user.setPrenom(prenom);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(tempPassword));
        user.setEmailVerified(true);
        user.setEnabled(true);
        user.setFirstLogin(true);

        Role roleEntity = roleRepo.findByName(Role.RoleName.valueOf("ROLE_" + role))
                .orElseThrow(() -> new RuntimeException("Rôle introuvable."));
        user.setRoles(Set.of(roleEntity));

        userRepo.save(user);

        emailService.sendEncadrantCredentials(email, prenom, tempPassword, role);

        Map<String, Object> result = new HashMap<>();
        result.put("message",      "Compte encadrant créé avec succès.");
        result.put("email",        email);
        result.put("tempPassword", tempPassword);
        return result;
    }
}
