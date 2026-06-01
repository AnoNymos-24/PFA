package com.smartintern.backend.service;

import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.UserRepository;
import com.smartintern.backend.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public Map<String, Object> verifyOtp(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (user.getOtpCode() == null || !user.getOtpCode().equals(code)) {
            throw new RuntimeException("Code incorrect");
        }
        if (user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Code expiré");
        }

        // Activation du compte : EN_ATTENTE → ACTIF
        user.setStatut(User.Statut.ACTIF);
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        String token = jwtUtils.generateToken(userDetails);

        return Map.of(
                "token", token,
                "id", user.getId(),
                "firstName", user.getFirstName(),
                "lastName", user.getLastName(),
                "email", user.getEmail(),
                "role", user.getRole().name(),
                "statut", user.getStatut().name(),
                "type", "Bearer"
        );
    }

    public void resendOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        String newCode = generateOtp();
        user.setOtpCode(newCode);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        try {
            emailService.sendOtpEmail(email, user.getFirstName(), newCode);
        } catch (Exception e) {
            log.warn("⚠️  Email non envoyé (SMTP). OTP pour [{}] : >>>  {}  <<<", email, newCode);
        }
    }

    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return; // Sécurité : ne pas révéler si l'email existe

        String code = generateOtp();
        user.setOtpCode(code);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        try {
            emailService.sendPasswordResetEmail(email, user.getFirstName(), code);
        } catch (Exception e) {
            log.warn("⚠️  Email non envoyé (SMTP). Code reset pour [{}] : >>>  {}  <<<", email, code);
        }
    }

    public void resetPassword(String email, String code, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (user.getOtpCode() == null || !user.getOtpCode().equals(code)) {
            throw new RuntimeException("Code incorrect");
        }
        if (user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Code expiré");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        userRepository.save(user);
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private String generateOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1000000));
    }
}
