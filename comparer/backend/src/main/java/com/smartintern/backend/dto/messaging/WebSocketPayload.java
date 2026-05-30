package com.smartintern.backend.dto.messaging;

import lombok.*;

// Payload envoyé par le client via WebSocket STOMP
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketPayload {

    // Action : SEND | TYPING_START | TYPING_STOP | READ
    private String action;

    private Long conversationId;
    private String contenu;
}