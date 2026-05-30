package com.smartintern.backend.dto.messaging;

import com.smartintern.backend.entity.Message.TypeMessage;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDTO {
    private Long id;
    private Long conversationId;
    private UserSummaryDTO expediteur;
    private String contenu;
    private String fichierNom;
    private String fichierPath;
    private String fichierType;
    private Long fichierTaille;
    private TypeMessage type;
    private LocalDateTime sentAt;
    private boolean lu;
}