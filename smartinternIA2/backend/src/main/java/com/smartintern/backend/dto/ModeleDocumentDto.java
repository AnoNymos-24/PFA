package com.smartintern.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ModeleDocumentDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ModeleDocumentRequest {

        @NotBlank(message = "Le nom du modèle est obligatoire")
        private String nom;

        @NotNull(message = "Le type de document est obligatoire")
        private Long typeDocumentId;

        @Min(value = 1, message = "La durée doit être d'au moins 1 jour")
        private int dureeValiditeJours = 365;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate dateExpiration;

        private SignatureNumeriqueDto signatureNumerique;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ModeleDocumentUpdateRequest {
        private String nom;
        private int dureeValiditeJours;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate dateExpiration;

        private SignatureNumeriqueDto signatureNumerique;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SignatureNumeriqueDto {
        private String nomSignataire;        // "Dr. Ahmed Ben Ali"
        private String titreSignataire;      // "Directeur des Études"
        private String nomEtablissement;     // "ITEAM University"
        private String cheminLogo;           // "uploads/logos/logo.png"
        private String cheminCachet;         // "uploads/cachets/cachet.png"
        private String cheminSignature;      // "uploads/signatures/sig.png"
        private String mentionLegale;        // "Ce document est officiel..."
        private String hashCle;              // SHA-256 auto-généré
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ModeleDocumentResponse {
        private Long id;
        private String nom;
        private String titreDocument;
        private String cheminModele;
        private String cheminHeader;
        private String cheminFooter;
        private String cheminScript;
        private String champsDynamiques;
        private int dureeValiditeJours;
        private int version;
        private String statut;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime dateCreation;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime dateModification;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate dateExpiration;

        private boolean expire;
        private SignatureNumeriqueDto signatureNumerique;
        private String typeDocumentCode;
        private String typeDocumentNom;
        private int nombreDocumentsGeneres;
    }
}