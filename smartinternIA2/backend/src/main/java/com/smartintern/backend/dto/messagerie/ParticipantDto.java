package com.smartintern.backend.dto.messagerie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vue allégée d'un participant à une conversation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantDto {

    private Long    utilisateurId;
    private String  prenom;
    private String  nom;
    private String  role;
    private boolean estActif;
}
