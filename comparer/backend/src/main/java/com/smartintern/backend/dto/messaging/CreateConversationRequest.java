package com.smartintern.backend.dto.messaging;

import com.smartintern.backend.entity.Conversation.TypeConversation;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationRequest {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    private String description;

    private TypeConversation type = TypeConversation.GROUPE;

    // IDs des participants à ajouter (sans le créateur, il est ajouté automatiquement)
    private List<Long> participantIds;
}