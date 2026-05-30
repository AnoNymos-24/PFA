package com.smartintern.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
public class User {

    // ── IDENTITÉ DE BASE ──────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String nom;

    @NotBlank
    private String prenom;

    @Email
    @Column(unique = true, nullable = false)
    private String email;

    @NotBlank
    private String password;

    private boolean enabled = true;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── RÔLES ─────────────────────────────────────────────────
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    // ── VÉRIFICATION EMAIL ────────────────────────────────────
    private String  verificationCode;
    private LocalDateTime verificationCodeExpiry;
    private boolean emailVerified = false;

    // ── FIRST LOGIN (encadrants) ──────────────────────────────
    private boolean firstLogin = false;

    // ── RÉINITIALISATION MOT DE PASSE ────────────────────────
    private String        resetToken;
    private LocalDateTime resetTokenExpiry;

    // ── CHAMPS ÉTUDIANT ───────────────────────────────────────
    private String universite;
    private String carteEtudiant;
    private String niveau;
    private String specialite;

    // ── CHAMPS ENTREPRISE ─────────────────────────────────────
    private String raisonSociale;
    private String matriculeFiscal;
    private String secteur;
    private String adresse;
    private String ville;
    private String telephone;

    // ── CV (étudiant) ─────────────────────────────────────────
    private String cvFileName;
    private String cvFilePath;
    private LocalDateTime cvUploadedAt;
}