package com.smartintern.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;

public class CandidatureDto {

    @Data
    public static class CandidatureRequest {
        @NotNull(message = "L'offre est obligatoire")
        private Long offreId;

        private String lettreMotivation;
        // url_demande_stage : rempli côté serveur après upload du document
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidatureResponse {
        private Long id;
        private String statut;
        private String statutLabel;
        private LocalDate dateCandidature;
        private LocalDate dateReponse;
        private String lettreMotivation;
        private String urlDemandeStage;
        private Float scoreMatching;
        private String commentaireEntreprise;
        private String commentaireAdministration;
        // Infos dénormalisées
        private Long offreId;
        private String offreTitre;
        private String entrepriseNom;
        private Long etudiantId;
        private String etudiantNom;
    }

    @Data
    public static class CandidatureDecisionRequest {
        // ACCEPTEE ou REFUSEE
        private String statut;
        private String commentaire;
    }
}
