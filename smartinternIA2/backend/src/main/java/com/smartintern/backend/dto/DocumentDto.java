package com.smartintern.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTOs pour la génération et l'authentification des documents.
 */
public class DocumentDto {

    // ══════════════════════════════════════════════════════════════════════
    // REQUÊTE DE GÉNÉRATION
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Corps de la requête de génération d'un document.
     *
     * Pour un étudiant :
     *   - Seul {@code typeDocumentId} est obligatoire.
     *   - Les données sont auto-remplies depuis le profil (nom, prénom, matricule,
     *     filière, établissement…).
     *   - {@code donneesSupplementaires} peut compléter les champs non extraits du profil.
     *
     * Pour un admin :
     *   - {@code typeDocumentId} obligatoire.
     *   - {@code userId} : ID de l'étudiant pour qui générer le document.
     *   - {@code donneesSupplementaires} : données additionnelles / overrides.
     *   - Aucune limite de génération (peut regénérer le même type plusieurs fois).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentGenerationRequest {

        @NotNull(message = "L'identifiant du type de document est obligatoire")
        private Long typeDocumentId;

        /**
         * Admin uniquement — ID de l'utilisateur (étudiant) pour qui le document est généré.
         * Si null (étudiant) : le document est généré pour l'utilisateur authentifié.
         */
        private Long userId;

        /**
         * Données supplémentaires (clé = nom du champ [champ] dans le template, valeur = texte).
         * Prioritaires sur les données auto-extraites du profil.
         */
        private Map<String, Object> donneesSupplementaires;

        /**
         * Format de sortie : "docx" (défaut) ou "pdf" (nécessite LibreOffice côté microservice).
         */
        @Builder.Default
        private String outputFormat = "docx";
    }

    // ══════════════════════════════════════════════════════════════════════
    // RÉPONSE DE GÉNÉRATION
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Réponse retournée après la génération réussie d'un document.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentGenerationResponse {

        private boolean success;

        /** UUID unique du document généré — sert de clé pour toutes les opérations. */
        private String docUuid;

        /** URL de téléchargement du fichier (DOCX ou PDF). */
        private String urlTelechargement;

        /**
         * URL publique d'authentification du document (contenu dans le QR code).
         * Format : {baseUrl}/api/documents/{docUuid}/authentifier
         */
        private String urlAuthentification;

        /** Code du type de document (ex : convention_stage). */
        private String typeDocument;

        /** Format généré : "docx" ou "pdf". */
        private String format;

        /** Statut du document : VALIDE. */
        private String statut;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime dateCreation;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime dateExpiration;

        private Long tailleOctets;

        private String message;
    }

    // ══════════════════════════════════════════════════════════════════════
    // PAGE D'AUTHENTIFICATION PUBLIQUE
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Données retournées par l'endpoint public {@code GET /api/documents/{uuid}/authentifier}.
     * Scanned depuis le QR code intégré dans le document.
     *
     * Contient toutes les informations nécessaires à l'affichage de la page d'authenticité.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentAuthResponse {

        // ── Identification du document ──────────────────────────────────────
        private String docUuid;
        private String typeDocument;       // code (ex : convention_stage)
        private String nomTypeDocument;    // libellé (ex : Convention de stage)

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime dateCreation;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime dateExpiration;

        private int numeroVersion;

        // ── Informations du détenteur ───────────────────────────────────────
        /** Numéro matricule (codeEtudiant). */
        private String matricule;

        /** Nom complet du détenteur (prénom + nom). */
        private String nomComplet;

        private String email;

        // ── Informations de l'établissement ────────────────────────────────
        private String nomEtablissement;

        /** URL ou chemin du logo de l'établissement. */
        private String logoEtablissement;

        // ── Statut d'authenticité ───────────────────────────────────────────
        /** VALIDE / EXPIRE / REVOQUE */
        private String statut;

        private boolean valide;
        private boolean expire;
    }
}
