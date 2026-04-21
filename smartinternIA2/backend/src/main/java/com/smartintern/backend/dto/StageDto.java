package com.smartintern.backend.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;

public class StageDto {

    @Data
    public static class StageRequest {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private String sujet;
        private String mission;
        private Long encadrantAcademiqueId;
        private Long encadrantEntrepriseId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageResponse {
        private Long id;
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private int dureeMois;
        private String statut;
        private String sujet;
        private String mission;
        private String evaluationFinale;
        // Infos dénormalisées
        private Long etudiantId;
        private String etudiantNom;
        private Long entrepriseId;
        private String entrepriseNom;
        private String encadrantAcademiqueNom;
        private String encadrantEntrepriseNom;
        private Long candidatureId;
    }
}
