/* package com.smartintern.backend.dto;

import com.smartintern.backend.entity.Offre;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class OffreDto {

    @Data
    public static class OffreRequest {
        @NotBlank
        private String titre;
        @NotBlank
        private String description;
        @NotBlank
        private String ville;
        @NotBlank
        private String domaine;
        private int dureeMois;
        private LocalDate dateLimite;
    }

    @Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class OffreResponse {
        private Long id;
        private String titre;
        private String description;
        private String entreprise;
        private String ville;
        private String domaine;
        private int dureeMois;
        private String statut;
        private LocalDate dateLimite;
        private LocalDateTime createdAt;
    }
} */