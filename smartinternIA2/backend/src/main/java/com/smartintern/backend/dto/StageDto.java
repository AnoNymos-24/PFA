package com.smartintern.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

public class StageDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
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
        private Long etudiantId;
        private String etudiantNom;
        private String etudiantPrenom;
        private String etudiantEmail;
        private Long entrepriseId;
        private String entrepriseNom;
        private String encadrantAcademiqueNom;
        private String encadrantAcademiquePrenom;
        private String encadrantEntrepriseNom;
        private String offreTitre;
        private int dureeSemaines;
        private Long candidatureId;
        private boolean conventionSigneeEntreprise;
        private LocalDate dateSignatureEntreprise;
        private boolean conventionSigneeEncadrant;
        private LocalDate dateSignatureEncadrant;
    }
}