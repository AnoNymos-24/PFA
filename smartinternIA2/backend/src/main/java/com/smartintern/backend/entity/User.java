/* package com.smartintern.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "first_name")
    private String firstName;

    @NotBlank
    @Column(name = "last_name")
    private String lastName;

    @Email
    @NotBlank
    @Column(unique = true)
    private String email;

    @NotBlank
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Builder.Default
    private boolean enabled = false;

    @Column(name = "cv_path")
    private String cvPath;

    @Column(name = "otp_code")
    private String otpCode;

    @Column(name = "otp_expiry")
    private LocalDateTime otpExpiry;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Role {
        ETUDIANT,
        ENTREPRISE,
        ADMIN,
        ENCADRANT_ACADEMIQUE,
        ENCADRANT_ENTREPRISE
    }
}
 */






package com.smartintern.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED) // Héritage JOINED : une table par sous-classe
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Diagramme : nom / prenom
    @NotBlank
    @Column(name = "nom")
    private String lastName;

    @NotBlank
    @Column(name = "prenom")
    private String firstName;

    @Email
    @NotBlank
    @Column(unique = true)
    private String email;

    @NotBlank
    private String password;

    // Diagramme : photo_profil
    @Column(name = "photo_profil")
    private String photoProfil;

    // Diagramme : telephone
    private String telephone;

    // Diagramme : role (string dans le diagramme → enum en Java)
    @Enumerated(EnumType.STRING)
    private Role role;

    // Diagramme : statut (ACTIF, INACTIF, EN_ATTENTE...)
    // Remplace l'ancien champ enabled (boolean)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Statut statut = Statut.EN_ATTENTE;

    // OTP (interne, pas dans le diagramme mais fonctionnel)
    @Column(name = "otp_code")
    private String otpCode;

    @Column(name = "otp_expiry")
    private LocalDateTime otpExpiry;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // -----------------------------------------------
    // Méthode de compatibilité avec Spring Security
    // (remplace l'ancien champ enabled)
    // -----------------------------------------------
    public boolean isEnabled() {
        return this.statut == Statut.ACTIF;
    }

    public enum Role {
        ETUDIANT,
        ENTREPRISE,
        ADMIN,
        ENCADRANT_ACADEMIQUE,
        ENCADRANT_ENTREPRISE
    }

    public enum Statut {
        ACTIF,
        INACTIF,
        EN_ATTENTE,
        SUSPENDU
    }
}