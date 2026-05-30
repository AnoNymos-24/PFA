package com.smartintern.backend.scheduler;

import com.smartintern.backend.service.RisqueStageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AD-04 — Job planifié : analyse automatique des risques chaque matin en semaine.
 *
 * <p>Déclenchement : tous les jours ouvrables (lundi → vendredi) à 07h00.
 *
 * <p><b>Activation :</b> le scheduler est <em>désactivé par défaut</em>.
 * Pour l'activer, positionner dans {@code application.properties} (ou via variable d'env) :
 * <pre>{@code risque.scheduler.enabled=true}</pre>
 *
 * <p>En cas d'erreur, l'exception est logguée sans interrompre le scheduler —
 * le prochain déclenchement planifié s'exécutera normalement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name         = "risque.scheduler.enabled",
        havingValue  = "true",
        matchIfMissing = false   // désactivé par défaut (dev / CI)
)
public class RisqueScheduler {

    private final RisqueStageService risqueStageService;

    /**
     * Déclenche l'analyse de risque pour tous les stages EN_COURS.
     * Les résultats sont persistés en upsert (un résultat par stage).
     *
     * <p>Cron : {@code 0 0 7 * * MON-FRI} → chaque jour ouvrable à 07:00:00.
     */
    @Scheduled(cron = "0 0 7 * * MON-FRI")
    public void analyseQuotidienneRisques() {
        log.info("[AD-04] Démarrage analyse quotidienne des risques (batch planifié)");
        try {
            var resultats = risqueStageService.analyserTousStagesEnCours();
            long nbARisque = resultats.stream()
                    .filter(r -> "A_RISQUE".equals(r.getNiveauRisque()))
                    .count();
            log.info("[AD-04] Analyse quotidienne terminée — {} stage(s) traité(s), {} à risque critique",
                    resultats.size(), nbARisque);
        } catch (Exception e) {
            log.error("[AD-04] Erreur lors de l'analyse quotidienne des risques : {}", e.getMessage(), e);
        }
    }
}
