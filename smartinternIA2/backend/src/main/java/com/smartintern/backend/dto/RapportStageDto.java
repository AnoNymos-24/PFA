package com.smartintern.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class RapportStageDto {

    @Data
    public static class RapportRequest {
        private String titre;
        private String contenu;
        private String type; // HEBDOMADAIRE ou FINAL
        private LocalDate semaineDebut;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RapportResponse {
        private Long id;
        private Long stageId;
        private Long etudiantId;
        private String etudiantNom;
        private String type;
        private String titre;
        private String contenu;
        private LocalDate semaineDebut;
        private String statut;
        private String commentaireEncadrant;
        private LocalDateTime dateCreation;
        private LocalDateTime dateSoumission;
    }
}
