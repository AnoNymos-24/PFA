package com.smartintern.backend.scheduler;

import com.smartintern.backend.entity.Stage;
import com.smartintern.backend.repository.StageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StageProgressionScheduler {

    private final StageRepository stageRepository;

    @Scheduled(cron = "0 0 6 * * MON")
public void mettreAJourProgression() {
    System.out.println("⏰ Mise à jour de la progression des stages...");
    
    List<Stage> stages = stageRepository.findAll();
    int mis_a_jour = 0;
    
    for (Stage stage : stages) {
        if (stage.getStatut() != Stage.StatutStage.EN_COURS) continue;
        if (stage.getDateDebut() == null) continue;
        
        // ✅ Calculer la durée réelle depuis dateDebut et dateFin
        int dureeSemaines;
        if (stage.getDateFin() != null) {
            dureeSemaines = (int) ChronoUnit.WEEKS.between(
                stage.getDateDebut(), stage.getDateFin()
            );
        } else if (stage.getDureeSemaines() > 0) {
            dureeSemaines = stage.getDureeSemaines();
        } else {
            dureeSemaines = 12; // fallback uniquement si rien n'est dispo
        }
        
        // Calculer la semaine actuelle
        long semaines = ChronoUnit.WEEKS.between(stage.getDateDebut(), LocalDate.now()) + 1;
        int nouvelleSemaine = (int) Math.min(semaines, dureeSemaines);
        
        // ✅ Mettre à jour dureeSemaines aussi
        if (stage.getDureeSemaines() != dureeSemaines) {
            stage.setDureeSemaines(dureeSemaines);
        }
        
        if (stage.getSemaineActuelle() != nouvelleSemaine) {
            stage.setSemaineActuelle(nouvelleSemaine);
            mis_a_jour++;
        }
        
        stageRepository.save(stage);
    }
    
    System.out.println("✅ " + mis_a_jour + " stage(s) mis à jour.");
}
}