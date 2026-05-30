package com.smartintern.backend.controller;

import com.smartintern.backend.dto.DashboardRisqueDto;
import com.smartintern.backend.dto.ResultatRisqueDto;
import com.smartintern.backend.entity.ResultatRisque;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.UserRepository;
import com.smartintern.backend.service.RisqueStageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AD-04 — Endpoints d'identification des étudiants à risque d'abandon ou d'échec.
 *
 * <p>Sécurité : assurée par SecurityConfig via les préfixes de chemin
 * — NE PAS ajouter {@code @PreAuthorize}.
 * <ul>
 *   <li>{@code /api/admin/risques/**}               → ADMIN uniquement</li>
 *   <li>{@code /api/encadrant-academique/risques/**} → ENCADRANT_ACADEMIQUE uniquement</li>
 * </ul>
 *
 * <h3>Endpoints Admin</h3>
 * <ul>
 *   <li>POST /api/admin/risques/analyser/{stageId}   — Déclencher une analyse IA</li>
 *   <li>POST /api/admin/risques/analyser-tous        — Analyser tous les stages EN_COURS</li>
 *   <li>GET  /api/admin/risques/etudiants-a-risque   — Stages A_RISQUE et A_SURVEILLER</li>
 *   <li>GET  /api/admin/risques/dashboard            — Vue agrégée complète</li>
 *   <li>GET  /api/admin/risques/stage/{id}/historique — Dernier résultat d'un stage</li>
 * </ul>
 *
 * <h3>Endpoints Encadrant Académique</h3>
 * <ul>
 *   <li>POST /api/encadrant-academique/risques/analyser/{stageId}</li>
 *   <li>GET  /api/encadrant-academique/risques/stage/{stageId}/historique</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RisqueStageController {

    private final RisqueStageService risqueService;
    private final UserRepository     userRepository;

    // ════════════════════════════════════════════════════════════════════════
    //  ADMIN
    // ════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/admin/risques/analyser/{stageId}
     * Déclenche une analyse IA pour le stage et persiste le résultat (upsert).
     */
    @PostMapping("/api/admin/risques/analyser/{stageId}")
    public ResponseEntity<ResultatRisqueDto.Response> analyserStageAdmin(
            @PathVariable Long stageId,
            Authentication auth,
            HttpServletRequest request) {

        User admin = getUser(auth);
        return ResponseEntity.ok(risqueService.analyserStage(stageId, admin, request));
    }

    /**
     * POST /api/admin/risques/analyser-tous
     * Analyse en lot tous les stages au statut EN_COURS.
     */
    @PostMapping("/api/admin/risques/analyser-tous")
    public ResponseEntity<List<ResultatRisqueDto.Response>> analyserTous() {
        return ResponseEntity.ok(risqueService.analyserTousStagesEnCours());
    }

    /**
     * GET /api/admin/risques/etudiants-a-risque
     * Retourne tous les stages aux niveaux A_RISQUE et A_SURVEILLER,
     * triés par score croissant (les plus urgents en premier).
     */
    @GetMapping("/api/admin/risques/etudiants-a-risque")
    public ResponseEntity<List<ResultatRisqueDto.Resume>> etudiantsARisque() {
        List<ResultatRisqueDto.Resume> aRisque =
                risqueService.getStagesParNiveauRisque(ResultatRisque.NiveauRisque.A_RISQUE);
        List<ResultatRisqueDto.Resume> aSurveiller =
                risqueService.getStagesParNiveauRisque(ResultatRisque.NiveauRisque.A_SURVEILLER);

        // Fusionner et trier par score croissant
        var tous = new java.util.ArrayList<>(aRisque);
        tous.addAll(aSurveiller);
        tous.sort((a, b) -> Integer.compare(a.getScoreEngagement(), b.getScoreEngagement()));
        return ResponseEntity.ok(tous);
    }

    /**
     * GET /api/admin/risques/dashboard
     * Vue agrégée complète : compteurs par niveau, score moyen/min/max, liste complète.
     */
    @GetMapping("/api/admin/risques/dashboard")
    public ResponseEntity<DashboardRisqueDto> dashboard() {
        return ResponseEntity.ok(risqueService.getDashboard());
    }

    /**
     * GET /api/admin/risques/stage/{stageId}/historique
     * Dernier résultat d'analyse pour le stage (sans redéclencher une analyse).
     */
    @GetMapping("/api/admin/risques/stage/{stageId}/historique")
    public ResponseEntity<?> historiqueAdmin(@PathVariable Long stageId) {
        return risqueService.getDernierResultat(stageId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(
                        Map.of("message",
                                "Aucune analyse disponible pour ce stage. "
                                        + "Déclenchez POST /api/admin/risques/analyser/" + stageId)));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  ENCADRANT ACADÉMIQUE
    // ════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/encadrant-academique/risques/analyser/{stageId}
     * Déclenche une analyse IA (accessible à l'encadrant académique).
     */
    @PostMapping("/api/encadrant-academique/risques/analyser/{stageId}")
    public ResponseEntity<ResultatRisqueDto.Response> analyserStageEncadrant(
            @PathVariable Long stageId,
            Authentication auth,
            HttpServletRequest request) {

        User encadrant = getUser(auth);
        return ResponseEntity.ok(risqueService.analyserStage(stageId, encadrant, request));
    }

    /**
     * GET /api/encadrant-academique/risques/stage/{stageId}/historique
     * Dernier résultat d'analyse (accessible à l'encadrant académique).
     */
    @GetMapping("/api/encadrant-academique/risques/stage/{stageId}/historique")
    public ResponseEntity<?> historiqueEncadrant(@PathVariable Long stageId) {
        return risqueService.getDernierResultat(stageId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(
                        Map.of("message",
                                "Aucune analyse disponible. "
                                        + "Déclenchez POST /api/encadrant-academique/risques/analyser/" + stageId)));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private User getUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException(
                        "Utilisateur non trouvé : " + auth.getName()));
    }
}
