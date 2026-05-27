package com.smartintern.backend.controller;

import com.smartintern.backend.dto.MatchingDto;
import com.smartintern.backend.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints Matching — côté entreprise.
 *
 * Base : /api/entreprise/matching
 * Sécurité : ROLE_ENTREPRISE (géré par SecurityConfig, pas de modification)
 *
 * GET /api/entreprise/matching/offre/{offreId}
 *   → Lance le matching IA pour l'offre et retourne le classement des candidats.
 */
@Slf4j
@RestController
@RequestMapping("/api/entreprise/matching")
@RequiredArgsConstructor
public class MatchingEntrepriseController {

    private final MatchingService matchingService;

    /**
     * Classe les candidats d'une offre par score IA décroissant.
     *
     * @param offreId  identifiant de l'offre de stage
     * @return liste CandidatScore triée (score décroissant)
     */
    @GetMapping("/offre/{offreId}")
    public ResponseEntity<?> classerCandidats(@PathVariable Long offreId) {
        log.info("Matching candidats demandé pour offre={}", offreId);
        try {
            List<MatchingDto.CandidatScore> classement = matchingService.classerCandidats(offreId);
            return ResponseEntity.ok(Map.of(
                    "success",   true,
                    "offreId",   offreId,
                    "total",     classement.size(),
                    "candidats", classement
            ));
        } catch (RuntimeException e) {
            log.error("Erreur matching offre {} : {}", offreId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
