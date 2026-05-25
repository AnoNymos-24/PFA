package com.smartintern.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

public class DemandeStageDto {

    @Data
    public static class DemandeStageRequest {
        private Long etudiantId;
        private Long entrepriseId;
        private String lettreMotivation;
        private String typeDemande;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DemandeStageResponse {
        private Long id;
        private String statut;
        private LocalDate dateDemande;
        private String lettreMotivation;
        private String typeDemande;
        private Long etudiantId;
        private String etudiantNom;
        private Long entrepriseId;
        private String entrepriseNom;
    }
}
