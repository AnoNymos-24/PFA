package com.smartintern.backend.dto;

import com.smartintern.backend.entity.Offre;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class OffreResponse {
    private Long id;
    private String titre;
    private String description;
    private String type;
    private String duree;
    private String lieu;
    private String niveauRequis;
    private String specialite;
    private String competences;
    private String statut;
    private LocalDateTime createdAt;
    private LocalDate dateExpiration;
    private int nombreCandidatures;

    // Infos entreprise
    private Long entrepriseId;
    private String entrepriseNom;
    private String entrepriseVille;
    private String entrepriseSecteur;

    public static OffreResponse from(Offre o) {
        OffreResponse r = new OffreResponse();
        r.setId(o.getId());
        r.setTitre(o.getTitre());
        r.setDescription(o.getDescription());
        r.setType(o.getType());
        r.setDuree(o.getDuree());
        r.setLieu(o.getLieu());
        r.setNiveauRequis(o.getNiveauRequis());
        r.setSpecialite(o.getSpecialite());
        r.setCompetences(o.getCompetences());
        r.setStatut(o.getStatut().name());
        r.setCreatedAt(o.getCreatedAt());
        r.setDateExpiration(o.getDateExpiration());
        r.setNombreCandidatures(o.getNombreCandidatures());
        if (o.getEntreprise() != null) {
            r.setEntrepriseId(o.getEntreprise().getId());
            r.setEntrepriseNom(o.getEntreprise().getRaisonSociale() != null
                ? o.getEntreprise().getRaisonSociale()
                : o.getEntreprise().getNom());
            r.setEntrepriseVille(o.getEntreprise().getVille());
            r.setEntrepriseSecteur(o.getEntreprise().getSecteur());
        }
        return r;
    }
}