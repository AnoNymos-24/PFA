package com.smartintern.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Async
    public void sendVerificationCode(String toEmail, String prenom, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(from, fromName);
            helper.setTo(toEmail);
            helper.setSubject("SmartIntern AI — Votre code de vérification");
            helper.setText(buildVerificationHtml(prenom, code), true);

            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Erreur envoi email : " + e.getMessage());
        }
    }

    @Async
    public void sendEncadrantCredentials(String toEmail, String prenom,
                                          String tempPassword, String role) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(from, fromName);
            helper.setTo(toEmail);
            helper.setSubject("SmartIntern AI — Vos identifiants de connexion");
            helper.setText(buildEncadrantHtml(prenom, toEmail, tempPassword, role), true);

            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Erreur envoi email : " + e.getMessage());
        }
    }

    private String buildVerificationHtml(String prenom, String code) {
        String[] digits = code.split("");
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
              <meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width,initial-scale=1"/>
            </head>
            <body style="margin:0;padding:0;background:#f8fafc;font-family:'Segoe UI',Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f8fafc;padding:40px 20px;">
                <tr><td align="center">
                  <table width="520" cellpadding="0" cellspacing="0"
                         style="background:#ffffff;border-radius:16px;overflow:hidden;
                                border:1px solid #e2e8f0;max-width:520px;width:100%%;">

                    <!-- HEADER -->
                    <tr>
                      <td style="background:#1d4ed8;padding:32px 40px;text-align:center;">
                        <div style="display:inline-flex;align-items:center;gap:10px;">
                          <div style="width:36px;height:36px;background:rgba(255,255,255,0.2);
                                      border-radius:9px;display:inline-block;
                                      line-height:36px;text-align:center;vertical-align:middle;">
                            &#9776;
                          </div>
                          <span style="font-size:1.2rem;font-weight:800;color:#ffffff;
                                       letter-spacing:-0.3px;vertical-align:middle;">
                            SmartIntern <span style="color:#93c5fd;">AI</span>
                          </span>
                        </div>
                      </td>
                    </tr>

                    <!-- BODY -->
                    <tr>
                      <td style="padding:40px 40px 32px;">
                        <p style="font-size:0.85rem;font-weight:600;color:#1d4ed8;
                                   letter-spacing:1px;text-transform:uppercase;margin:0 0 12px;">
                          Vérification du compte
                        </p>
                        <h1 style="font-size:1.6rem;font-weight:800;color:#0f172a;
                                   letter-spacing:-0.8px;margin:0 0 12px;line-height:1.2;">
                          Bonjour %s&nbsp;👋
                        </h1>
                        <p style="font-size:0.95rem;color:#64748b;line-height:1.7;margin:0 0 32px;">
                          Votre compte SmartIntern AI a bien été créé.<br/>
                          Entrez ce code à 4 chiffres pour activer votre accès :
                        </p>

                        <!-- CODE BOXES -->
                        <table cellpadding="0" cellspacing="0" style="margin:0 auto 32px;">
                          <tr>
                            %s
                          </tr>
                        </table>

                        <div style="background:#f8fafc;border:1px solid #e2e8f0;
                                    border-radius:10px;padding:14px 18px;margin-bottom:32px;">
                          <p style="font-size:0.8rem;color:#64748b;margin:0;line-height:1.6;">
                            ⏱&nbsp; Ce code est valable <strong style="color:#0f172a;">10 minutes</strong>.<br/>
                            🔒&nbsp; Ne partagez jamais ce code avec personne.
                          </p>
                        </div>

                        <p style="font-size:0.8rem;color:#94a3b8;margin:0;line-height:1.6;">
                          Si vous n'avez pas créé de compte sur SmartIntern AI,
                          ignorez simplement cet e-mail.
                        </p>
                      </td>
                    </tr>

                    <!-- FOOTER -->
                    <tr>
                      <td style="padding:20px 40px;border-top:1px solid #f1f5f9;
                                  text-align:center;">
                        <p style="font-size:0.75rem;color:#cbd5e1;margin:0;">
                          © 2024 SmartIntern AI — Tous droits réservés
                        </p>
                      </td>
                    </tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(
                prenom,
                buildDigitCells(digits)
            );
    }

    private String buildDigitCells(String[] digits) {
        StringBuilder sb = new StringBuilder();
        for (String d : digits) {
            sb.append("""
                <td style="padding:0 6px;">
                  <div style="width:60px;height:68px;background:#eff6ff;
                               border:2px solid #1d4ed8;border-radius:12px;
                               font-size:2rem;font-weight:800;color:#1d4ed8;
                               text-align:center;line-height:68px;
                               font-family:'Segoe UI',Arial,sans-serif;">
                    %s
                  </div>
                </td>
                """.formatted(d));
        }
        return sb.toString();
    }

    private String buildEncadrantHtml(String prenom, String email,
                                       String tempPassword, String role) {
        String roleLabel = role.contains("ACADEMIQUE") ? "Encadrant académique" : "Encadrant entreprise";
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head><meta charset="UTF-8"/></head>
            <body style="margin:0;padding:0;background:#f8fafc;font-family:'Segoe UI',Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f8fafc;padding:40px 20px;">
                <tr><td align="center">
                  <table width="520" cellpadding="0" cellspacing="0"
                         style="background:#ffffff;border-radius:16px;overflow:hidden;
                                border:1px solid #e2e8f0;max-width:520px;width:100%%;">

                    <tr>
                      <td style="background:#1d4ed8;padding:32px 40px;text-align:center;">
                        <span style="font-size:1.2rem;font-weight:800;color:#ffffff;">
                          SmartIntern <span style="color:#93c5fd;">AI</span>
                        </span>
                      </td>
                    </tr>

                    <tr>
                      <td style="padding:40px 40px 32px;">
                        <p style="font-size:0.85rem;font-weight:600;color:#1d4ed8;
                                   letter-spacing:1px;text-transform:uppercase;margin:0 0 12px;">
                          Bienvenue sur la plateforme
                        </p>
                        <h1 style="font-size:1.5rem;font-weight:800;color:#0f172a;margin:0 0 12px;">
                          Bonjour %s&nbsp;!
                        </h1>
                        <p style="font-size:0.95rem;color:#64748b;line-height:1.7;margin:0 0 28px;">
                          Un compte <strong style="color:#0f172a;">%s</strong> a été créé pour vous
                          sur SmartIntern AI. Voici vos identifiants de connexion :
                        </p>

                        <div style="background:#f8fafc;border:1px solid #e2e8f0;
                                    border-radius:12px;padding:20px 24px;margin-bottom:28px;">
                          <table cellpadding="0" cellspacing="0" width="100%%">
                            <tr>
                              <td style="font-size:0.8rem;color:#64748b;font-weight:600;
                                          padding-bottom:8px;">E-mail</td>
                              <td style="font-size:0.9rem;color:#0f172a;font-weight:700;
                                          text-align:right;padding-bottom:8px;">%s</td>
                            </tr>
                            <tr>
                              <td style="font-size:0.8rem;color:#64748b;font-weight:600;
                                          border-top:1px solid #e2e8f0;padding-top:8px;">
                                Mot de passe temporaire
                              </td>
                              <td style="font-size:0.9rem;color:#1d4ed8;font-weight:700;
                                          font-family:monospace;letter-spacing:1px;
                                          text-align:right;border-top:1px solid #e2e8f0;
                                          padding-top:8px;">%s</td>
                            </tr>
                          </table>
                        </div>

                        <div style="background:#fef9c3;border:1px solid #fde68a;
                                    border-radius:10px;padding:14px 18px;margin-bottom:28px;">
                          <p style="font-size:0.8rem;color:#92400e;margin:0;line-height:1.6;">
                            ⚠️ &nbsp;Vous devrez <strong>changer ce mot de passe</strong>
                            dès votre première connexion pour des raisons de sécurité.
                          </p>
                        </div>

                        <p style="font-size:0.8rem;color:#94a3b8;margin:0;line-height:1.6;">
                          En cas de problème, contactez votre administrateur.
                        </p>
                      </td>
                    </tr>

                    <tr>
                      <td style="padding:20px 40px;border-top:1px solid #f1f5f9;text-align:center;">
                        <p style="font-size:0.75rem;color:#cbd5e1;margin:0;">
                          © 2024 SmartIntern AI — Tous droits réservés
                        </p>
                      </td>
                    </tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(prenom, roleLabel, email, tempPassword);
    }
    
}