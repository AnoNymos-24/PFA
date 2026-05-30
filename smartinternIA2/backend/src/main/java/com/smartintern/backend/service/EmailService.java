package com.smartintern.backend.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Adaptateur SMTP bas niveau.
 * Appelé uniquement par NotificationServiceImpl — ne pas injecter ailleurs.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    // ── Méthode générique (utilisée par NotificationServiceImpl) ───────────

    public void sendHtml(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, "SmartIntern AI");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Erreur envoi email : " + e.getMessage());
        }
    }

    public void sendOtpEmail(String to, String firstName, String otp) {
        String html = "<h2>Bonjour " + firstName + ",</h2>"
                + "<p>Votre code de vérification SmartIntern est :</p>"
                + "<h1 style='letter-spacing:8px'>" + otp + "</h1>"
                + "<p>Ce code expire dans 10 minutes.</p>";
        sendHtml(to, "Votre code de vérification SmartIntern", html);
    }

    public void sendPasswordResetEmail(String to, String firstName, String otp) {
        String html = "<h2>Bonjour " + firstName + ",</h2>"
                + "<p>Votre code de réinitialisation de mot de passe est :</p>"
                + "<h1 style='letter-spacing:8px'>" + otp + "</h1>"
                + "<p>Ce code expire dans 10 minutes.</p>";
        sendHtml(to, "Réinitialisation de votre mot de passe SmartIntern", html);
    }
}
