package com.smartintern.backend.scheduler;

import com.smartintern.backend.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * U-06 — Job planifié : synchronisation des statuts de conversations.
 *
 * <p>Toutes les 30 minutes, passe en {@code LECTURE_SEULE} toutes les conversations
 * PRIVEE dont le stage associé n'est plus {@code EN_COURS}.
 *
 * <p>{@code @EnableScheduling} est déjà présent sur {@code BackendApplication}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessagerieScheduler {

    private final ConversationService conversationService;

    /**
     * Cron : {@code 0 *&#47;30 * * * *} → toutes les 30 minutes (à :00 et :30).
     */
    @Scheduled(cron = "0 */30 * * * *")
    public void synchroniserStatuts() {
        log.debug("[Messagerie] Synchronisation des statuts de conversations...");
        try {
            conversationService.synchroniserStatutsConversations();
        } catch (Exception e) {
            log.error("[Messagerie] Erreur lors de la synchronisation des statuts : {}", e.getMessage(), e);
        }
    }
}
