package com.smartintern.backend.dto.messaging;

import com.smartintern.backend.entity.Message.TypeMessage;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {
    private Long conversationId;
    private String contenu;
    private TypeMessage type = TypeMessage.TEXTE;
}