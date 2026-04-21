package com.smartintern.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendOtpEmail(String toEmail, String firstName, String otpCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("smartinternainoreply@gmail.com", "SmartIntern AI");
            helper.setTo(toEmail);
            helper.setSubject("Votre code de vérification SmartIntern AI");

            String html = """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; padding: 32px; background: #f5f5f3; border-radius: 16px;">
                    <div style="text-align: center; margin-bottom: 24px;">
                        <div style="background: #185FA5; width: 48px; height: 48px; border-radius: 12px; display: inline-flex; align-items: center; justify-content: center; margin-bottom: 12px;">
                            <span style="color: white; font-size: 24px;">🎓</span>
                        </div>
                        <h1 style="color: #185FA5; font-size: 22px; margin: 0;">SmartIntern AI</h1>
                    </div>
                    <div style="background: white; border-radius: 12px; padding: 28px; text-align: center; border: 1px solid #e8e8e5;">
                        <p style="font-size: 16px; color: #333; margin-bottom: 8px;">Bonjour <strong>%s</strong>,</p>
                        <p style="font-size: 14px; color: #888; margin-bottom: 24px;">Votre code de vérification est :</p>
                        <div style="background: #E6F1FB; border-radius: 12px; padding: 20px; margin-bottom: 24px;">
                            <span style="font-size: 40px; font-weight: 700; letter-spacing: 12px; color: #185FA5;">%s</span>
                        </div>
                        <p style="font-size: 13px; color: #aaa;">Ce code expire dans <strong>10 minutes</strong>.</p>
                        <p style="font-size: 13px; color: #aaa;">Si vous n'avez pas créé de compte, ignorez cet email.</p>
                    </div>
                    <p style="text-align: center; font-size: 12px; color: #aaa; margin-top: 20px;">© 2024 SmartIntern AI — Plateforme de gestion des stages</p>
                </div>
            """.formatted(firstName, otpCode);

            helper.setText(html, true);
            mailSender.send(message);

        } catch (Exception e) {
            throw new RuntimeException("Erreur envoi email : " + e.getMessage());
        }
    }

    public void sendPasswordResetEmail(String toEmail, String firstName, String otpCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("smartinternainoreply@gmail.com", "SmartIntern AI");
            helper.setTo(toEmail);
            helper.setSubject("Réinitialisation de votre mot de passe SmartIntern AI");

            String html = """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; padding: 32px; background: #f5f5f3; border-radius: 16px;">
                    <div style="text-align: center; margin-bottom: 24px;">
                        <h1 style="color: #185FA5; font-size: 22px; margin: 0;">SmartIntern AI</h1>
                    </div>
                    <div style="background: white; border-radius: 12px; padding: 28px; text-align: center; border: 1px solid #e8e8e5;">
                        <p style="font-size: 16px; color: #333; margin-bottom: 8px;">Bonjour <strong>%s</strong>,</p>
                        <p style="font-size: 14px; color: #888; margin-bottom: 24px;">Votre code de réinitialisation est :</p>
                        <div style="background: #FAEEDA; border-radius: 12px; padding: 20px; margin-bottom: 24px;">
                            <span style="font-size: 40px; font-weight: 700; letter-spacing: 12px; color: #BA7517;">%s</span>
                        </div>
                        <p style="font-size: 13px; color: #aaa;">Ce code expire dans <strong>10 minutes</strong>.</p>
                    </div>
                </div>
            """.formatted(firstName, otpCode);

            helper.setText(html, true);
            mailSender.send(message);

        } catch (Exception e) {
            throw new RuntimeException("Erreur envoi email : " + e.getMessage());
        }
    }
}