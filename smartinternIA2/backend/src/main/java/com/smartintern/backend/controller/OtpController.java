package com.smartintern.backend.controller;

import com.smartintern.backend.entity.User;
import com.smartintern.backend.notification.NotificationEvent;
import com.smartintern.backend.notification.NotificationService;
import com.smartintern.backend.repository.UserRepository;
import com.smartintern.backend.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OtpController {

    private final UserRepository      userRepository;
    private final NotificationService notificationService;
    private final JwtUtils            jwtUtils;
    private final UserDetailsService  userDetailsService;
    private final PasswordEncoder     passwordEncoder;

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code  = body.get("code");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (user.getOtpCode() == null || !user.getOtpCode().equals(code))
            return ResponseEntity.badRequest().body(Map.of("message", "Code incorrect"));

        if (user.getOtpExpiry().isBefore(LocalDateTime.now()))
            return ResponseEntity.badRequest().body(Map.of("message", "Code expiré"));

        user.setStatut(User.Statut.ACTIF);
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        String token = jwtUtils.generateToken(userDetails);

        return ResponseEntity.ok(Map.of(
                "token",      token,
                "type",       "Bearer",
                "id",         user.getId(),
                "firstName",  user.getFirstName(),
                "lastName",   user.getLastName(),
                "email",      user.getEmail(),
                "role",       user.getRole().name(),
                "statut",     user.getStatut().name(),
                "firstLogin", true
        ));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        String newCode = generateOtp();
        user.setOtpCode(newCode);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        notificationService.envoyer(
            NotificationEvent.RENVOI_OTP,
            Map.of("prenom", user.getFirstName(), "code", newCode, "expiration", "10 minutes"),
            email
        );

        return ResponseEntity.ok(Map.of("message", "Code renvoyé avec succès"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null) {
            String code = generateOtp();
            user.setOtpCode(code);
            user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));
            userRepository.save(user);

            notificationService.envoyer(
                NotificationEvent.RESET_PASSWORD_OTP,
                Map.of("prenom", user.getFirstName(), "code", code, "expiration", "10 minutes"),
                email
            );
        }

        return ResponseEntity.ok(Map.of("message", "Si cet email existe, un code vous a été envoyé"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String email       = body.get("email");
        String code        = body.get("code");
        String newPassword = body.get("newPassword");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (user.getOtpCode() == null || !user.getOtpCode().equals(code))
            return ResponseEntity.badRequest().body(Map.of("message", "Code incorrect"));

        if (user.getOtpExpiry().isBefore(LocalDateTime.now()))
            return ResponseEntity.badRequest().body(Map.of("message", "Code expiré"));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Mot de passe réinitialisé avec succès"));
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private String generateOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1000000));
    }
}
