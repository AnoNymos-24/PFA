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

    public void sendCandidatureAccepteeEmail(String to, String firstName, String offreTitre, String entrepriseNom) {
        String html = buildEmailTemplate(
            "Votre candidature a été acceptée ! 🎉",
            firstName,
            "Félicitations ! Votre candidature pour le poste <strong>" + offreTitre + "</strong> "
            + "chez <strong>" + entrepriseNom + "</strong> a été acceptée.",
            "Vous serez contacté(e) prochainement pour les prochaines étapes.",
            "#10b981"
        );
        sendHtml(to, "✅ Candidature acceptée — " + offreTitre, html);
    }

    public void sendCandidatureRefuseeEmail(String to, String firstName, String offreTitre, String entrepriseNom, String commentaire) {
        String detail = (commentaire != null && !commentaire.isBlank())
            ? "Commentaire de l'entreprise : <em>" + commentaire + "</em>" : "";
        String html = buildEmailTemplate(
            "Réponse à votre candidature",
            firstName,
            "Votre candidature pour le poste <strong>" + offreTitre + "</strong> "
            + "chez <strong>" + entrepriseNom + "</strong> n'a pas été retenue cette fois.",
            detail + "<p style='margin-top:12px;'>Ne vous découragez pas, de nombreuses opportunités vous attendent sur SmartIntern AI.</p>",
            "#ef4444"
        );
        sendHtml(to, "Réponse à votre candidature — " + offreTitre, html);
    }

    public void sendRapportValideEmail(String to, String firstName, String titreRapport) {
        String html = buildEmailTemplate(
            "Votre rapport a été validé ✅",
            firstName,
            "Votre rapport <strong>" + titreRapport + "</strong> a été validé par votre encadrant académique.",
            "Continuez votre excellent travail !",
            "#10b981"
        );
        sendHtml(to, "✅ Rapport validé — " + titreRapport, html);
    }

    public void sendRapportCommenteEmail(String to, String firstName, String titreRapport, String commentaire) {
        String html = buildEmailTemplate(
            "Commentaire sur votre rapport",
            firstName,
            "Votre encadrant académique a commenté votre rapport <strong>" + titreRapport + "</strong>.",
            "Commentaire : <em>" + commentaire + "</em>",
            "#f59e0b"
        );
        sendHtml(to, "Commentaire rapport — " + titreRapport, html);
    }

    public void sendStageCreeeEmail(String to, String firstName, String entrepriseNom, String dateDebut, String dateFin) {
        String html = buildEmailTemplate(
            "Votre stage a été créé 🎓",
            firstName,
            "Félicitations ! Votre stage chez <strong>" + entrepriseNom + "</strong> a été officiellement créé.",
            "Période : du <strong>" + dateDebut + "</strong> au <strong>" + dateFin + "</strong>.<br>"
            + "Connectez-vous à SmartIntern AI pour suivre votre progression.",
            "#2563eb"
        );
        sendHtml(to, "🎓 Votre stage est confirmé chez " + entrepriseNom, html);
    }

    public void sendNouvelleOffreEmail(String to, String firstName, String offreTitre, String entrepriseNom) {
        String html = buildEmailTemplate(
            "Nouvelle offre correspondant à votre profil",
            firstName,
            "Une nouvelle offre de stage qui correspond à votre profil vient d'être publiée : "
            + "<strong>" + offreTitre + "</strong> chez <strong>" + entrepriseNom + "</strong>.",
            "Connectez-vous pour postuler avant la clôture des candidatures.",
            "#8b5cf6"
        );
        sendHtml(to, "💼 Nouvelle offre : " + offreTitre, html);
    }

    private String buildEmailTemplate(String titre, String prenom, String corps, String detail, String color) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'/></head><body "
            + "style='font-family:Sora,Arial,sans-serif;background:#f8fafc;margin:0;padding:20px;'>"
            + "<div style='max-width:600px;margin:0 auto;background:#fff;border-radius:16px;"
            + "overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,.08);'>"
            + "<div style='background:" + color + ";padding:24px 32px;'>"
            + "<h1 style='color:#fff;margin:0;font-size:1.3rem;'>" + titre + "</h1></div>"
            + "<div style='padding:32px;'>"
            + "<p style='color:#334155;font-size:1rem;'>Bonjour <strong>" + prenom + "</strong>,</p>"
            + "<p style='color:#475569;line-height:1.7;'>" + corps + "</p>"
            + "<div style='background:#f1f5f9;border-radius:10px;padding:16px 20px;margin:20px 0;color:#475569;line-height:1.6;'>"
            + detail + "</div>"
            + "<hr style='border:none;border-top:1px solid #e2e8f0;margin:24px 0;'/>"
            + "<p style='color:#94a3b8;font-size:.8rem;'>SmartIntern AI — Plateforme de gestion des stages</p>"
            + "</div></div></body></html>";
    }
}
