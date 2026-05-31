package com.smartintern.backend.notification;

import com.smartintern.backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.smartintern.backend.notification.NotificationEvent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final EmailService emailService;
    private final EmailTemplateEngine templateEngine;

    // ── Mapping événement → [template, sujet] ─────────────────────────────
    private static final Map<NotificationEvent, String[]> EVENT_CONFIG = Map.ofEntries(
        Map.entry(INSCRIPTION_OTP,         new String[]{"otp-inscription",    "Votre code de vérification SmartIntern AI"}),
        Map.entry(RENVOI_OTP,              new String[]{"otp-inscription",    "Nouveau code de vérification SmartIntern AI"}),
        Map.entry(RESET_PASSWORD_OTP,      new String[]{"otp-reset-password", "Réinitialisation de votre mot de passe SmartIntern AI"}),
        Map.entry(TACHE_CREEE,             new String[]{"tache-assignee",     "Nouvelle tâche assignée — SmartIntern AI"}),
        Map.entry(TACHE_MODIFIEE,          new String[]{"tache-assignee",     "Tâche mise à jour — SmartIntern AI"}),
        Map.entry(TACHE_EN_RETARD,         new String[]{"tache-assignee",     "⚠ Tâche en retard — SmartIntern AI"}),
        Map.entry(RAPPORT_DEPOSE,          new String[]{"rapport-depose",     "Nouveau rapport déposé — SmartIntern AI"}),
        Map.entry(RAPPORT_VALIDE,          new String[]{"rapport-depose",     "Rapport validé — SmartIntern AI"}),
        Map.entry(RAPPORT_REFUSE,          new String[]{"rapport-depose",     "Rapport refusé — SmartIntern AI"}),
        Map.entry(CORRECTION_DEMANDEE,     new String[]{"rapport-depose",     "Correction demandée — SmartIntern AI"})
    );

    @Override
    @Async
    public void envoyer(NotificationEvent event, Map<String, Object> variables, String destinataire) {
        String[] config = EVENT_CONFIG.get(event);
        if (config == null) {
            log.debug("Aucun template configuré pour l'événement {} — notification ignorée", event);
            return;
        }
        try {
            String html = templateEngine.render(config[0], variables);
            emailService.sendHtml(destinataire, config[1], html);
            log.info("Notification {} envoyée à {}", event, destinataire);
        } catch (Exception e) {
            Object code = variables.get("code");
            if (code != null) {
                log.warn("⚠️  Email non envoyé [{}]. Code pour {} : >>>  {}  <<<  — {}",
                        event, destinataire, code, e.getMessage());
            } else {
                log.warn("⚠️  Notification {} non envoyée à {} : {}", event, destinataire, e.getMessage());
            }
        }
    }
}
