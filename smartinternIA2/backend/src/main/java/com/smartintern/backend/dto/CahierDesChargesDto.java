package com.smartintern.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * DTOs pour le Cahier des charges.
 */
public class CahierDesChargesDto {

    /** Corps de la requête pour la validation (aucun champ supplémentaire requis) */
    @Data
    public static class ValiderRequest {
        // vide — la validation ne demande aucun body supplémentaire
    }

    /** Réponse JSON renvoyée par les endpoints CDC */
    @Data
    public static class Response {
        private Long id;
        private Long stageId;
        private String titre;
        private String description;
        private String objectifs;
        private String contraintes;
        private String livrables;
        private String statut;
        private String urlFichier;
        private LocalDateTime dateValidation;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
