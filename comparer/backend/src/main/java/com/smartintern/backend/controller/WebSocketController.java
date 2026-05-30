package com.smartintern.backend.controller;

import com.smartintern.backend.dto.messaging.SendMessageRequest;
import com.smartintern.backend.dto.messaging.WebSocketPayload;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.service.MessagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final MessagingService messagingService;

    // ─── Envoi d'un message texte ─────────────────────────────────
    // Client envoie vers : /app/conversations/{convId}/send
    // Broadcast vers    : /topic/conversations/{convId}
    @MessageMapping("/conversations/{convId}/send")
    public void envoyerMessage(
            @DestinationVariable Long convId,
            @Payload WebSocketPayload payload,
            Principal principal) {

        if (principal == null) {
            log.warn("WebSocket : message reçu sans authentification");
            return;
        }

        Long userId = getUserId(principal);
        if (userId == null) return;

        SendMessageRequest req = new SendMessageRequest();
        req.setConversationId(convId);
        req.setContenu(payload.getContenu());

        messagingService.envoyerMessage(req, userId);
    }

    // ─── Indicateur de frappe ─────────────────────────────────────
    // Client envoie vers : /app/conversations/{convId}/typing
    // Broadcast vers    : /topic/conversations/{convId}/typing
    @MessageMapping("/conversations/{convId}/typing")
    public void typing(
            @DestinationVariable Long convId,
            @Payload WebSocketPayload payload,
            Principal principal) {

        if (principal == null) return;

        Long userId = getUserId(principal);
        if (userId == null) return;

        boolean isTyping = "TYPING_START".equals(payload.getAction());
        messagingService.envoyerIndicateurTyping(convId, userId, isTyping);
    }

    // ─── Marquer messages comme lus ───────────────────────────────
    // Client envoie vers : /app/conversations/{convId}/read
    // Broadcast vers    : /topic/conversations/{convId}/lus
    @MessageMapping("/conversations/{convId}/read")
    public void marquerLus(
            @DestinationVariable Long convId,
            Principal principal) {

        if (principal == null) return;

        Long userId = getUserId(principal);
        if (userId == null) return;

        messagingService.marquerLus(convId, userId);
    }

    // ─── Helper : extraire l'ID utilisateur depuis le Principal ───
    private Long getUserId(Principal principal) {
        try {
            if (principal instanceof org.springframework.security.authentication
                    .UsernamePasswordAuthenticationToken auth) {
                Object p = auth.getPrincipal();
                if (p instanceof User user) {
                    return user.getId();
                }
            }
        } catch (Exception e) {
            log.warn("Impossible d'extraire userId depuis Principal : {}", e.getMessage());
        }
        return null;
    }
}