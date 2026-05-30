package com.smartintern.backend.scheduler;

import com.smartintern.backend.entity.Stage;
import com.smartintern.backend.repository.StageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Job hebdomadaire : met à jour semaineActuelle sur chaque stage EN_COURS.
 * Cron : chaque lundi à 6h00.
 *
 * Calcul de dureeSemaines :
 *   - Si dateFin connue  → WEEKS.between(dateDebut, dateFin)
 *   - Sinon              → dureeMois * 4 (approximation)
 *   - Fallback           → 48 semaines (12 mois)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StageProgressionScheduler {

    private final StageRepository stageRepository;

    @Scheduled(cron = "0 0 6 * * MON")
    public void mettreAJourProgression() {
        log.debug("[StageProgression] Démarrage mise à jour semaine actuelle...");

        List<Stage> stages = stageRepository.findByStatut(Stage.Statut.EN_COURS);
        int misAJour = 0;

        for (Stage stage : stages) {
            if (stage.getDateDebut() == null) continue;

            int dureeSemaines;
            if (stage.getDateFin() != null) {
                dureeSemaines = (int) ChronoUnit.WEEKS.between(
                        stage.getDateDebut(), stage.getDateFin());
            } else if (stage.getDureeMois() > 0) {
                dureeSemaines = stage.getDureeMois() * 4;
            } else {
                dureeSemaines = 48;
            }

            long semaines = ChronoUnit.WEEKS.between(stage.getDateDebut(), LocalDate.now()) + 1;
            int nouvelleSemaine = (int) Math.min(Math.max(semaines, 1), dureeSemaines);

            if (stage.getSemaineActuelle() != nouvelleSemaine) {
                stage.setSemaineActuelle(nouvelleSemaine);
                stageRepository.save(stage);
                misAJour++;
            }
        }

        log.info("[StageProgression] {} stage(s) mis à jour sur {} EN_COURS",
                misAJour, stages.size());
    }
}
