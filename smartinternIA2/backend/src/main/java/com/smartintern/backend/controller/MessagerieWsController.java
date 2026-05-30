package com.smartintern.backend.controller;

import com.smartintern.backend.dto.messagerie.MessageSendRequest;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.UserRepository;
import com.smartintern.backend.service.ConversationService;
import com.smartintern.backend.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * U-07/U-08 — Contrôleur WebSocket STOMP (messagerie temps réel).
 *
 * <p>Les destinations sont préfixées {@code /app} par la config STOMP.
 * Le broadcast vers {@code /topic/conversation.{id}} est géré dans {@link MessageService}.
 *
 * <p>Sécurité : {@link com.smartintern.backend.config.WebSocketAuthInterceptor}
 * valide le JWT au CONNECT — {@code Principal} est toujours non-null ici.
 *
 * <p>Nouvelles destinations :
 * <ul>
 *   <li>{@code /app/conversation.typing} — indicateur de frappe temps réel</li>
 * </ul>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MessagerieWsController {

    private final MessageService         messageService;
    private final ConversationService    conversationService;
    private final UserRepository         userRepository;
    private final SimpMessagingTemplate  messagingTemplate;

    /**
     * Reçoit un message depuis le client STOMP.
     * Destination côté client : {@code /app/message.envoyer}
     * Broadcast effectué dans {@link MessageService#envoyerMessage} → pas de {@code @SendTo}.
     */
    @MessageMapping("/message.envoyer")
    public void envoyerMessage(@Payload MessageSendRequest request, Principal principal) {
        User user = chargerUser(principal.getName());
        try {
            messageService.envoyerMessage(user, request);
        } catch (Exception e) {
            log.warn("WS envoyerMessage — erreur pour user={} : {}", principal.getName(), e.getMessage());
        }
    }

    /**
     * Confirme l'abonnement à une conversation et vérifie l'accès.
     * Destination côté client : {@code /app/conversation.rejoindre}
     * Payload attendu : {@code { "conversationId": 42 }}
     */
    @MessageMapping("/conversation.rejoindre")
    public void rejoindreConversation(@Payload Map<String, Long> payload, Principal principal) {
        Long conversationId = payload.get("conversationId");
        if (conversationId == null) return;

        User user = chargerUser(principal.getName());
        try {
            conversationService.verifierAcces(user, conversationId);
            log.debug("WS conversation.rejoindre — user={} conv={}", principal.getName(), conversationId);
        } catch (Exception e) {
            log.warn("WS conversation.rejoindre — accès refusé pour user={} conv={} : {}",
                    principal.getName(), conversationId, e.getMessage());
        }
    }

    /**
     * Diffuse un indicateur de frappe à tous les participants d'une conversation.
     * Destination côté client : {@code /app/conversation.typing}
     * Broadcast vers           : {@code /topic/conversation.{id}.typing}
     *
     * <p>Payload attendu :
     * <pre>{ "conversationId": 42, "typing": true }</pre>
     * <p>Payload diffusé :
     * <pre>{ "userId": 3, "prenom": "Alice", "typing": true }</pre>
     */
    @MessageMapping("/conversation.typing")
    public void typing(@Payload Map<String, Object> payload, Principal principal) {
        Object convIdObj = payload.get("conversationId");
        if (convIdObj == null) return;

        Long conversationId;
        try {
            conversationId = Long.valueOf(String.valueOf(convIdObj));
        } catch (NumberFormatException e) {
            log.warn("WS conversation.typing — conversationId invalide : {}", convIdObj);
            return;
        }

        boolean isTyping = Boolean.TRUE.equals(payload.get("typing"));
        User user = chargerUser(principal.getName());

        messagingTemplate.convertAndSend(
                "/topic/conversation." + conversationId + ".typing",
                Map.of(
                        "userId", user.getId(),
                        "prenom", user.getFirstName(),
                        "typing", isTyping
                )
        );
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private User chargerUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé : " + email));
    }
}
