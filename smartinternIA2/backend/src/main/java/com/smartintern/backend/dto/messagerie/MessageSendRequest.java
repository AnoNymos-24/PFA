package com.smartintern.backend.dto.messagerie;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Requête d'envoi de message — utilisée via REST et WebSocket STOMP.
 */
@Data
public class MessageSendRequest {

    @NotNull(message = "La conversation est obligatoire")
    private Long conversationId;

    @NotBlank(message = "Le contenu ne peut pas être vide")
    @Size(max = 2000, message = "Le message ne peut pas dépasser 2000 caractères")
    private String contenu;
}
