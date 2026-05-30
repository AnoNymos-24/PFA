package com.smartintern.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTOs pour les Sprints.
 */
public class SprintDto {

    // ── Requêtes ──────────────────────────────────────────────────────────────

    @Data
    public static class CreationRequest {
        @NotBlank(message = "Le titre est obligatoire")
        private String titre;

        @NotNull(message = "Le numéro de sprint est obligatoire")
        private Integer numero;

        private String description;
        private LocalDate dateDebut;
        private LocalDate dateFin;
    }

    @Data
    public static class ModificationRequest {
        private String titre;
        private String description;
        private LocalDate dateDebut;
        private LocalDate dateFin;
        /** Modifiable uniquement en état CLOTURE */
        private String observationEncadrant;
    }

    @Data
    public static class CloturerRequest {
        @NotBlank(message = "L'observation est obligatoire pour clôturer un sprint")
        private String observation;
    }

    // ── Réponse ───────────────────────────────────────────────────────────────

    @Data
    public static class Response {
        private Long id;
        private Long stageId;
        private Integer numero;
        private String titre;
        private String description;
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDateTime dateClotureReelle;
        private String observationEncadrant;
        private String statut;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
