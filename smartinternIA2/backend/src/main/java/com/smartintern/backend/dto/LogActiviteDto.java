package com.smartintern.backend.dto;

import java.time.LocalDateTime;

/**
 * DTOs pour les logs d'activité (lecture seule — admin).
 */
public class LogActiviteDto {

    /**
     * Réponse JSON renvoyée par les endpoints /api/admin/logs.
     * Record immutable — Jackson sérialise les composants directement.
     */
    public record Response(
            Long id,
            Long userId,
            String userEmail,
            String userPrenom,
            String userNom,
            String action,
            String entiteCible,
            Long entiteId,
            String description,
            String adresseIp,
            String appareil,
            LocalDateTime createdAt
    ) {}
}
