package com.smartintern.backend.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class OffreRequest {
    private String titre;
    private String description;
    private String type;
    private String duree;
    private String lieu;
    private String niveauRequis;
    private String specialite;
    private String competences;
    private LocalDate dateExpiration;
}