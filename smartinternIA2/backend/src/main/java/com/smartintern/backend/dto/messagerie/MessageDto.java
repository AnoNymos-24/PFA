package com.smartintern.backend.dto.messagerie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Représentation d'un message — retourné via REST et broadcasté via WebSocket.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto {

    private Long            id;
    private Long            conversationId;
    private UserSummaryDto  expediteur;    // { id, prenom, nom, role }
    private String          contenu;
    private LocalDateTime   envoieLe;
    private boolean         lu;
    private String          type;          // "TEXTE", "SYSTEME", "FICHIER", "IMAGE"

    // ── Pièce jointe (null pour les messages TEXTE/SYSTEME) ───────────────────
    private String          fichierNom;
    private String          fichierPath;
    private String          fichierType;
    private Long            fichierTaille;

    // ── Classe imbriquée : résumé de l'expéditeur ─────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSummaryDto {
        private Long   id;
        private String prenom;
        private String nom;
        private String role;
    }
}
