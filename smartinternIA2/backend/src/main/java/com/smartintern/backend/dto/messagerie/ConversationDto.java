package com.smartintern.backend.dto.messagerie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vue complète d'une conversation retournée au client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDto {

    private Long              id;
    private String            type;           // "PRIVEE" ou "REUNION"
    private String            statut;         // "ACTIVE", "LECTURE_SEULE", "ARCHIVEE"
    private String            sujet;
    private List<ParticipantDto> participants;
    private MessageDto        dernierMessage;
    private long              nbNonLus;
    private LocalDateTime     dernierMessageLe;
}
