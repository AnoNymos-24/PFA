package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Entity
@Table(name = "etudiants")
@PrimaryKeyJoinColumn(name = "user_id")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Etudiant extends User {

    @Column(name = "code_etudiant", unique = true)
    private String codeEtudiant;

    private String filiere;

    private String classe;

    @Column(unique = true)
    private String cin;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    private String nationalite;

    @Column(name = "cv_path")
    private String cvPath;

    @Column(name = "cv_data_json", columnDefinition = "TEXT")
    private String cvDataJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etablissement_id")
    private Etablissement etablissement;
}