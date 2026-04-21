package com.smartintern.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class OffreStageDto {

    @Data
    public static class OffreStageRequest {
        @NotBlank(message = "Le titre est obligatoire")
        private String titre;

        @NotBlank(message = "La description est obligatoire")
        private String description;

        // Diagramme : type_stage
        private String typeStage;

        @NotBlank(message = "Le domaine est obligatoire")
        private String domaine;

        private String theme;

        @NotBlank(message = "La localisation est obligatoire")
        private String localisation;

        private String niveauRequis;

        @Min(value = 1, message = "La durée doit être d'au moins 1 mois")
        private int dureeMois;

        private LocalDate dateExpiration;

        @Min(value = 1, message = "Le nombre de places doit être d'au moins 1")
        private Integer nombrePlaces;

        private Boolean remuneration;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OffreStageResponse {
        private Long id;
        private String titre;
        private String typeStage;
        private String domaine;
        private String theme;
        private String description;
        private String localisation;
        private String niveauRequis;
        private int dureeMois;
        private String statut;
        private String statutValidation;
        private LocalDate datePublication;
        private LocalDate dateExpiration;
        private Integer nombrePlaces;
        private Boolean remuneration;
        // Infos entreprise dénormalisées
        private Long entrepriseId;
        private String entrepriseNom;
        private LocalDateTime createdAt;
    }
}
