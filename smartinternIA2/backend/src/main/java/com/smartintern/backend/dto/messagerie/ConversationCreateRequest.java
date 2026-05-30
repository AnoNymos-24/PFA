package com.smartintern.backend.dto.messagerie;

import lombok.Data;

/**
 * Requête de création / récupération d'une conversation.
 * <ul>
 *   <li>PRIVEE : {@code destinataireId} + {@code stageId} obligatoires</li>
 *   <li>REUNION : {@code entrepriseId} + {@code sujet} obligatoires</li>
 * </ul>
 */
@Data
public class ConversationCreateRequest {

    /** "PRIVEE" ou "REUNION" */
    private String type;

    /** PRIVEE uniquement — identifiant du destinataire */
    private Long destinataireId;

    /** PRIVEE uniquement — stage liant les deux participants */
    private Long stageId;

    /** REUNION uniquement — entreprise organisatrice */
    private Long entrepriseId;

    /** Requis pour REUNION */
    private String sujet;
}
