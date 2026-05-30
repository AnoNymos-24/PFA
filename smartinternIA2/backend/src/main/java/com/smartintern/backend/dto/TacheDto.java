package com.smartintern.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTOs pour les Tâches.
 */
public class TacheDto {

    // ── Requêtes encadrant ────────────────────────────────────────────────────

    @Data
    public static class CreationRequest {
        @NotBlank(message = "Le titre est obligatoire")
        private String titre;

        private String description;
        private LocalDate dateDebutPrevue;
        private LocalDate dateFinPrevue;
    }

    @Data
    public static class ModificationRequest {
        private String titre;
        private String description;
        private LocalDate dateDebutPrevue;
        private LocalDate dateFinPrevue;
        private String observationEncadrant;
    }

    @Data
    public static class ValidationRequest {
        /** Optionnel — commentaire de validation */
        private String observation;
        /** Optionnel — note numérique */
        private Float note;
    }

    @Data
    public static class RefusRequest {
        @NotBlank(message = "L'observation est obligatoire pour refuser une tâche")
        private String observation;
    }

    // ── Requêtes étudiant ─────────────────────────────────────────────────────

    @Data
    public static class TerminerRequest {
        /** Optionnel — note / commentaire rédigé par l'étudiant */
        private String noteEtudiant;
    }

    // ── Réponse ───────────────────────────────────────────────────────────────

    @Data
    public static class Response {
        private Long id;
        private Long sprintId;
        private String titre;
        private String description;
        private String statut;
        private String decisionValidation;
        private LocalDate dateDebutPrevue;
        private LocalDate dateFinPrevue;
        private LocalDateTime dateRealisationEtudiant;
        private LocalDateTime dateTraitementEncadrant;
        private String noteEtudiant;
        private String observationEncadrant;
        private Float note;
        private boolean genereParIa;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
