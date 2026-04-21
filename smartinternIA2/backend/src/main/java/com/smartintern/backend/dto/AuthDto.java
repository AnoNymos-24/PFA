/* package com.smartintern.backend.dto;

import com.smartintern.backend.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AuthDto {

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "Le prénom est obligatoire")
        private String firstName;

        @NotBlank(message = "Le nom est obligatoire")
        private String lastName;

        @Email(message = "Email invalide")
        @NotBlank(message = "L'email est obligatoire")
        private String email;

        @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
        private String password;

        private User.Role role;
    }

    @Data
    public static class LoginRequest {
        @Email
        @NotBlank
        private String email;

        @NotBlank
        private String password;
    }

    @Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AuthResponse {
        private String token;
        private String type = "Bearer";
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
        private String role;
    }
} */




package com.smartintern.backend.dto;

import com.smartintern.backend.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

public class AuthDto {

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "Le prénom est obligatoire")
        private String firstName;

        @NotBlank(message = "Le nom est obligatoire")
        private String lastName;

        @Email(message = "Email invalide")
        @NotBlank(message = "L'email est obligatoire")
        private String email;

        @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
        private String password;

        private User.Role role;

        // Champs optionnels selon le rôle
        private String telephone;

        // Pour ETUDIANT
        private String filiere;
        private String classe;
        private String codeEtudiant;

        // Pour ENCADRANT_ACADEMIQUE / ENCADRANT_ENTREPRISE
        private String domaine;
        private Long etablissementId;
        private Long entrepriseId;
    }

    @Data
    public static class LoginRequest {
        @Email
        @NotBlank
        private String email;

        @NotBlank
        private String password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthResponse {
        private String token;
        private String type;
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
        private String role;
        // Remplace enabled — retourne le statut du compte
        private String statut;
    }
}