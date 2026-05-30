package com.smartintern.backend.dto.messaging;

import com.smartintern.backend.entity.Conversation.TypeConversation;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationDTO {
    private Long id;
    private String nom;
    private String description;
    private TypeConversation type;
    private LocalDateTime createdAt;
    private UserSummaryDTO createur;
    private Set<UserSummaryDTO> participants;
    private MessageDTO dernierMessage;  // prévisualisation dans la sidebar
    private int nonLus;                 // badge de messages non lus
}