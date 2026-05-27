package com.smartintern.backend.controller;

import com.smartintern.backend.dto.MatchingDto;
import com.smartintern.backend.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints Recommandation — côté étudiant.
 *
 * Base : /api/etudiant/recommandations
 * Sécurité : ROLE_ETUDIANT (géré par SecurityConfig, pas de modification)
 *
 * GET /api/etudiant/recommandations/offres?limit=10
 *   → Recommande des offres personnalisées à l'étudiant connecté.
 *
 * GET /api/etudiant/recommandations/offres/{etudiantId}?limit=10
 *   → Variante avec etudiantId explicite (utile pour les tests / admin).
 */
@Slf4j
@RestController
@RequestMapping("/api/etudiant/recommandations")
@RequiredArgsConstructor
public class MatchingEtudiantController {

    private final MatchingService matchingService;

    /**
     * Recommande des offres pour un étudiant par son identifiant.
     *
     * @param etudiantId  identifiant de l'étudiant
     * @param limit       nombre maximal d'offres retournées (défaut 10)
     * @return liste OffreRecommandee triée par score décroissant
     */
    @GetMapping("/offres/{etudiantId}")
    public ResponseEntity<?> recommanderOffres(
            @PathVariable Long etudiantId,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("Recommandation offres pour étudiant={}, limit={}", etudiantId, limit);
        try {
            List<MatchingDto.OffreRecommandee> offres =
                    matchingService.recommanderOffres(etudiantId, Math.min(limit, 50));
            return ResponseEntity.ok(Map.of(
                    "success",    true,
                    "etudiantId", etudiantId,
                    "total",      offres.size(),
                    "offres",     offres
            ));
        } catch (RuntimeException e) {
            log.error("Erreur recommandation étudiant {} : {}", etudiantId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
