package com.smartintern.backend.dto;

import com.smartintern.backend.dto.risque.RisqueAnalyseResponse;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de présentation d'un résultat d'analyse de risque.
 * Retourné par RisqueStageController — ne contient jamais les JSON bruts.
 */
public class ResultatRisqueDto {

    @Data
    public static class Response {
        private Long id;
        private Long stageId;
        private String etudiantNomComplet;
        private int scoreEngagement;
        private String niveauRisque;
        private RisqueAnalyseResponse.AnalyseDetail analyse;
        private List<RisqueAnalyseResponse.Alerte> alertes;
        private List<String> recommandations;
        private LocalDateTime analyseLe;
    }

    @Data
    public static class Resume {
        /** Vue allégée pour les listes (dashboard admin). */
        private Long stageId;
        private Long etudiantId;
        private String etudiantNomComplet;
        private String etudiantPrenom;
        private String etudiantNom;
        private String etudiantEmail;
        private String entrepriseNom;
        private String universite;
        private int scoreEngagement;
        private String niveauRisque;
        private int nbAlertesCritiques;
        private List<String> alertesResume;
        private LocalDateTime analyseLe;
    }
}
