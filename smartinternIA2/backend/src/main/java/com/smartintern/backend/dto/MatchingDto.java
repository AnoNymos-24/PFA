package com.smartintern.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO miroir des modèles Pydantic du microservice Python smartintern-ai-service.
 * Utilisé pour désérialiser les réponses JSON des endpoints :
 *   POST /ai/matching/candidats
 *   POST /ai/recommandation/offres
 *   POST /ai/analyse/profil
 *
 * Convention : camelCase Jackson ↔ snake_case Python (via alias_generator=to_camel).
 */
public class MatchingDto {

    // ════════════════════════════════════════════════════════════════════
    // RÉPONSE MATCHING CANDIDATS  (POST /ai/matching/candidats)
    // ════════════════════════════════════════════════════════════════════

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MatchingResponse {
        private boolean success;
        private String generatedAt;
        private String aiVersion;
        private MatchingData data;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MatchingData {
        private Long offreId;
        private String titreOffre;
        private List<CandidatScore> candidatsClasses = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CandidatScore {
        private Long candidatureId;
        private Long etudiantId;
        private String nomComplet;
        private ScoreMatching scoreMatching;
    }

    // ════════════════════════════════════════════════════════════════════
    // RÉPONSE RECOMMANDATION OFFRES  (POST /ai/recommandation/offres)
    // ════════════════════════════════════════════════════════════════════

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecommandationResponse {
        private boolean success;
        private String generatedAt;
        private String aiVersion;
        private RecommandationData data;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecommandationData {
        private Long etudiantId;
        private List<OffreRecommandee> offresRecommandees = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OffreRecommandee {
        private Long offreId;
        private String titre;
        private String domaine;
        private String entrepriseNom;
        private ScoreMatching scoreMatching;
    }

    // ════════════════════════════════════════════════════════════════════
    // SCORE COMMUN
    // ════════════════════════════════════════════════════════════════════

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScoreMatching {
        private double score;
        private String niveau;               // EXCELLENT | BON | MOYEN | FAIBLE
        private List<String> forces          = new ArrayList<>();
        private List<String> faiblesses      = new ArrayList<>();
        private double probabiliteSelection;
        private DetailsScore detailsScore;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DetailsScore {
        private double competencesTechniques;
        private double similariteTfIdf;
        private double experience;
        private double niveauAcademique;
        private double softSkills;
    }

    // ════════════════════════════════════════════════════════════════════
    // REQUÊTE MATCHING CANDIDATS  (corps envoyé vers Python)
    // ════════════════════════════════════════════════════════════════════

    @Data
    @NoArgsConstructor
    public static class MatchingRequest {
        private OffreInput offre;
        private List<CandidatInput> candidats = new ArrayList<>();
    }

    // ════════════════════════════════════════════════════════════════════
    // REQUÊTE RECOMMANDATION OFFRES  (corps envoyé vers Python)
    // ════════════════════════════════════════════════════════════════════

    @Data
    @NoArgsConstructor
    public static class RecommandationRequest {
        private EtudiantInput etudiant;
        private List<OffreInput> offres = new ArrayList<>();
        private int limit = 10;
    }

    // ════════════════════════════════════════════════════════════════════
    // ENTRÉES COMMUNES
    // ════════════════════════════════════════════════════════════════════

    @Data
    @NoArgsConstructor
    public static class OffreInput {
        private Long offreId;
        private String titre        = "";
        private String description  = "";
        private String domaine      = "";
        private String typeStage    = "";
        private String niveauRequis = "";
        private int    dureeMois    = 0;
        private List<String> competencesRequises = new ArrayList<>();
        private List<String> softSkillsRequis    = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class CandidatInput {
        private Long   candidatureId;
        private Long   etudiantId;
        private String nomComplet               = "";
        private List<String> competencesTechniques = new ArrayList<>();
        private List<String> softSkills            = new ArrayList<>();
        private String experiencesTexte          = "";
        private String formationsTexte           = "";
        private List<String> projets             = new ArrayList<>();
        private double scoreCompletudeCv         = 0.0;
        private String niveauFormation           = "";
    }

    @Data
    @NoArgsConstructor
    public static class EtudiantInput {
        private Long   etudiantId;
        private String nomComplet               = "";
        private List<String> competencesTechniques = new ArrayList<>();
        private List<String> softSkills            = new ArrayList<>();
        private String experiencesTexte          = "";
        private String formationsTexte           = "";
        private List<String> projets             = new ArrayList<>();
        private double scoreCompletudeCv         = 0.0;
        private String niveauFormation           = "";
    }
}
