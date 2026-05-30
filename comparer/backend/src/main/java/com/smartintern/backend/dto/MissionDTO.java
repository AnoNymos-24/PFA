package com.smartintern.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MissionDTO {
    private String titre;
    private String description;
    private String objectifs;
    private LocalDateTime dateEcheance;
}