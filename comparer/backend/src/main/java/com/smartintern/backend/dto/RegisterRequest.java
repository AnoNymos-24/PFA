package com.smartintern.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    // Communs
    @NotBlank private String nom;
    @NotBlank private String prenom;
    @Email    private String email;
    @Size(min = 8) private String password;
    @NotBlank private String role;

    // Étudiant
    private String universite;
    private String carteEtudiant;
    private String niveau;
    private String specialite;

    // Entreprise
    private String raisonSociale;
    private String matriculeFiscal;
    private String secteur;
    private String adresse;
    private String ville;
    private String telephone;
}