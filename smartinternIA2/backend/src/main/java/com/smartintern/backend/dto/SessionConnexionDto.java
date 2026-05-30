package com.smartintern.backend.dto;

import java.time.LocalDateTime;

/**
 * DTOs pour les sessions de connexion (lecture seule — admin).
 */
public class SessionConnexionDto {

    public record Response(
            Long id,
            Long userId,
            String userEmail,
            String userPrenom,
            String userNom,
            LocalDateTime dateConnexion,
            LocalDateTime dateDeconnexion,
            Long dureeSecondes,
            String adresseIp,
            String appareil,
            String statut
    ) {}
}
