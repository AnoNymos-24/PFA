package com.smartintern.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartintern.backend.client.RisquePythonClient;
import static com.smartintern.backend.client.RisquePythonClient.SCORE_FALLBACK;
import com.smartintern.backend.dto.DashboardRisqueDto;
import com.smartintern.backend.dto.ResultatRisqueDto;
import com.smartintern.backend.dto.risque.RisqueAnalyseRequest;
import com.smartintern.backend.dto.risque.RisqueAnalyseResponse;
import com.smartintern.backend.entity.*;
import com.smartintern.backend.entity.LogActivite.TypeAction;
import com.smartintern.backend.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Optional;

/**
 * AD-04 — Service d'identification des étudiants à risque d'abandon ou d'échec.
 *
 * <p>Algorithme :
 * <ol>
 *   <li>Collecte les métriques en base (sprints, tâches, rapports, activité).</li>
 *   <li>Construit le payload {@link RisqueAnalyseRequest} et délègue au {@link RisquePythonClient}.</li>
 *   <li>Persiste (upsert) le {@link ResultatRisque} — un seul résultat courant par stage.</li>
 *   <li>Logue l'analyse dans {@code logs_activite} (fire-and-forget via LogActiviteService).</li>
 * </ol>
 *
 * <p>Le microservice Python écoute sur {@code POST {cv.service.url}/ai/stage/analyse-risque}.
 * En cas d'indisponibilité, {@link RisquePythonClient} retourne un score de secours 50/STABLE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RisqueStageService {

    private final StageRepository          stageRepository;
    private final SprintRepository         sprintRepository;
    private final TacheRepository          tacheRepository;
    private final RapportStageRepository   rapportRepository;
    private final ResultatRisqueRepository resultatRepository;
    private final LogActiviteRepository    logActiviteRepository;
    private final LogActiviteService       logActiviteService;
    private final RisquePythonClient       risquePythonClient;
    private final ObjectMapper             objectMapper;

    // ════════════════════════════════════════════════════════════════════════
    // API PUBLIQUE
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Analyse le risque d'engagement pour un stage donné.
     * Si un résultat existait déjà, il est remplacé (upsert).
     *
     * @param stageId   identifiant du stage à analyser
     * @param demandeur utilisateur déclenchant l'analyse (pour le log d'audit)
     * @param request   requête HTTP (IP / user-agent pour le log)
     * @return DTO complet du résultat d'analyse
     */
    @Transactional
    public ResultatRisqueDto.Response analyserStage(Long stageId,
                                                     User demandeur,
                                                     HttpServletRequest request) {
        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new RuntimeException("Stage introuvable : " + stageId));

        // 1. Construire le payload pour le microservice IA
        RisqueAnalyseRequest payload = construirePayload(stage);

        // 2. Appeler le microservice Python via le client dédié
        RisqueAnalyseResponse aiResponse = risquePythonClient.analyser(payload);

        // 2b. Fallback : si score == -1, le microservice était indisponible — ne pas persister
        if (aiResponse.getScoreEngagement() == SCORE_FALLBACK) {
            log.warn("Microservice IA indisponible pour stage={} — résultat non persisté", stageId);
            return buildErrorDto(stage, aiResponse);
        }

        // 3. Persister (upsert) le résultat en BDD
        ResultatRisque resultat = upsertResultat(stage, aiResponse);

        // 4. Log d'audit (fire-and-forget)
        if (demandeur != null && request != null) {
            logActiviteService.loguer(
                    demandeur, TypeAction.ANALYSE_RISQUE,
                    "stages", stageId,
                    "Analyse risque — score=" + aiResponse.getScoreEngagement()
                            + " niveau=" + aiResponse.getNiveauRisque(),
                    request);
        }

        log.info("Risque analysé — stage={} score={} niveau={}",
                stageId, aiResponse.getScoreEngagement(), aiResponse.getNiveauRisque());

        return toDto(resultat, aiResponse);
    }

    /**
     * Analyse en lot tous les stages au statut EN_COURS.
     * Utilisé pour les jobs planifiés ({@link com.smartintern.backend.scheduler.RisqueScheduler})
     * ou le bouton "Tout analyser" du tableau de bord admin.
     *
     * @return liste des résultats, triée par score croissant (plus à risque en premier)
     */
    @Transactional
    public List<ResultatRisqueDto.Response> analyserTousStagesEnCours() {
        List<Stage> stagesEnCours = stageRepository.findAll().stream()
                .filter(s -> s.getStatut() == Stage.Statut.EN_COURS)
                .toList();

        log.info("Analyse batch risque — {} stage(s) EN_COURS à traiter", stagesEnCours.size());

        List<ResultatRisqueDto.Response> resultats = new ArrayList<>();
        for (Stage stage : stagesEnCours) {
            try {
                resultats.add(analyserStage(stage.getId(), null, null));
            } catch (Exception e) {
                log.error("Erreur analyse risque stage={} : {}", stage.getId(), e.getMessage());
            }
        }

        // Tri : plus à risque en premier
        resultats.sort((a, b) -> Integer.compare(a.getScoreEngagement(), b.getScoreEngagement()));
        return resultats;
    }

    /**
     * Récupère le dernier résultat d'analyse pour un stage sans déclencher
     * une nouvelle analyse.
     *
     * @param stageId identifiant du stage
     * @return résultat complet (vide si aucune analyse n'a encore été effectuée)
     */
    @Transactional(readOnly = true)
    public Optional<ResultatRisqueDto.Response> getDernierResultat(Long stageId) {
        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new RuntimeException("Stage introuvable : " + stageId));

        return resultatRepository.findByStage(stage).map(r -> {
            RisqueAnalyseResponse simule = deserialiserDetails(r);
            return toDto(r, simule);
        });
    }

    /**
     * Retourne tous les résultats pour un niveau de risque donné.
     */
    @Transactional(readOnly = true)
    public List<ResultatRisqueDto.Resume> getStagesParNiveauRisque(ResultatRisque.NiveauRisque niveau) {
        return resultatRepository.findByNiveauRisque(niveau).stream()
                .map(this::toResume)
                .toList();
    }

    /**
     * Retourne tous les résultats triés par score croissant (plus urgent en premier).
     */
    @Transactional(readOnly = true)
    public List<ResultatRisqueDto.Resume> getTousResultatsOrdonnés() {
        return resultatRepository.findAllByOrderByScoreEngagementAsc().stream()
                .map(this::toResume)
                .toList();
    }

    /**
     * Construit le tableau de bord agrégé pour l'admin.
     * Agrège les compteurs par niveau, le score moyen/min/max, et le nombre d'alertes critiques.
     */
    @Transactional(readOnly = true)
    public DashboardRisqueDto getDashboard() {
        List<ResultatRisque> tous = resultatRepository.findAllByOrderByScoreEngagementAsc();
        List<ResultatRisqueDto.Resume> resumes = tous.stream().map(this::toResume).toList();

        int nbExcellent   = 0, nbStable = 0, nbASurveiller = 0, nbARisque = 0;
        int totalAlertes  = 0;
        int scoreMin      = 100, scoreMax = 0;

        for (ResultatRisque r : tous) {
            switch (r.getNiveauRisque()) {
                case EXCELLENT    -> nbExcellent++;
                case STABLE       -> nbStable++;
                case A_SURVEILLER -> nbASurveiller++;
                case A_RISQUE     -> nbARisque++;
            }
            if (r.getScoreEngagement() < scoreMin) scoreMin = r.getScoreEngagement();
            if (r.getScoreEngagement() > scoreMax) scoreMax = r.getScoreEngagement();
        }

        for (ResultatRisqueDto.Resume r : resumes) {
            totalAlertes += r.getNbAlertesCritiques();
        }

        OptionalDouble scoreMoyenOpt = tous.stream()
                .mapToInt(ResultatRisque::getScoreEngagement)
                .average();
        double scoreMoyen = scoreMoyenOpt.isPresent()
                ? Math.round(scoreMoyenOpt.getAsDouble() * 10) / 10.0
                : 0.0;

        // Éviter scoreMin/scoreMax incohérents si aucun résultat
        if (tous.isEmpty()) { scoreMin = 0; scoreMax = 0; }

        return DashboardRisqueDto.builder()
                .totalStagesAnalyses(tous.size())
                .nbExcellent(nbExcellent)
                .nbStable(nbStable)
                .nbASurveiller(nbASurveiller)
                .nbARisque(nbARisque)
                .scoreMoyen(scoreMoyen)
                .scoreMin(scoreMin)
                .scoreMax(scoreMax)
                .nbAlertesCritiques(totalAlertes)
                .stagesParRisque(resumes)
                .build();
    }

    // ════════════════════════════════════════════════════════════════════════
    // FALLBACK DTO (microservice indisponible)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Construit un DTO d'erreur lorsque le microservice Python est indisponible.
     * Le résultat n'est PAS persisté en base (scoreEngagement = -1 est un signal d'erreur).
     */
    private ResultatRisqueDto.Response buildErrorDto(Stage stage, RisqueAnalyseResponse aiResponse) {
        ResultatRisqueDto.Response dto = new ResultatRisqueDto.Response();
        dto.setId(null);                                  // non persisté
        dto.setStageId(stage.getId());
        Etudiant e = stage.getEtudiant();
        dto.setEtudiantNomComplet(e.getFirstName() + " " + e.getLastName());
        dto.setScoreEngagement(SCORE_FALLBACK);           // -1 = signal d'erreur
        dto.setNiveauRisque("INCONNU");
        dto.setAnalyse(new RisqueAnalyseResponse.AnalyseDetail());
        dto.setAlertes(List.of());
        dto.setRecommandations(aiResponse.getRecommandations() != null
                ? aiResponse.getRecommandations()
                : List.of("Relancer l'analyse lorsque le microservice IA sera disponible."));
        dto.setAnalyseLe(LocalDateTime.now());
        return dto;
    }

    // ════════════════════════════════════════════════════════════════════════
    // CONSTRUCTION DU PAYLOAD
    // ════════════════════════════════════════════════════════════════════════

    private RisqueAnalyseRequest construirePayload(Stage stage) {
        RisqueAnalyseRequest req = new RisqueAnalyseRequest();

        // ── Stage ─────────────────────────────────────────────────────────────
        RisqueAnalyseRequest.StagePayload stageP = new RisqueAnalyseRequest.StagePayload();
        stageP.setId(stage.getId());
        LocalDate dateDebut = stage.getDateDebut();
        LocalDate dateFin   = stage.getDateFin();
        stageP.setDateDebut(dateDebut != null ? dateDebut.toString() : null);
        stageP.setDateFin(dateFin != null ? dateFin.toString() : null);
        stageP.setProgressionTemporelle(calculerProgression(dateDebut, dateFin));
        req.setStage(stageP);

        // ── Étudiant ──────────────────────────────────────────────────────────
        Etudiant etudiant = stage.getEtudiant();
        RisqueAnalyseRequest.EtudiantPayload etudiantP = new RisqueAnalyseRequest.EtudiantPayload();
        etudiantP.setId(etudiant.getId());
        etudiantP.setNom(etudiant.getLastName());
        etudiantP.setPrenom(etudiant.getFirstName());
        req.setEtudiant(etudiantP);

        // ── Rapports ──────────────────────────────────────────────────────────
        req.setRapports(calculerMetriquesRapports(stage));

        // ── Tâches ────────────────────────────────────────────────────────────
        req.setTaches(collecterTaches(stage));

        // ── Activité ──────────────────────────────────────────────────────────
        req.setActivite(calculerActivite(etudiant));

        return req;
    }

    /** Progression temporelle du stage en pourcentage (0.0 à 100.0). */
    private double calculerProgression(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null) return 0.0;
        LocalDate today = LocalDate.now();
        if (!today.isAfter(debut)) return 0.0;
        if (!today.isBefore(fin))  return 100.0;
        long total = ChronoUnit.DAYS.between(debut, fin);
        if (total <= 0) return 100.0;
        long ecoule = ChronoUnit.DAYS.between(debut, today);
        return Math.round(ecoule * 1000.0 / total) / 10.0; // 1 décimale
    }

    /** Métriques sur les rapports de stage. */
    private RisqueAnalyseRequest.RapportsPayload calculerMetriquesRapports(Stage stage) {
        RisqueAnalyseRequest.RapportsPayload p = new RisqueAnalyseRequest.RapportsPayload();
        List<RapportStage> rapports = rapportRepository.findByStageIdOrderByDateCreationDesc(stage.getId());

        int enRetard  = 0;
        int nonSoumis = 0;
        LocalDate seuilRetard = LocalDate.now().minusDays(3);

        for (RapportStage r : rapports) {
            if (r.getStatut() == RapportStage.Statut.BROUILLON) {
                if (r.getSemaineDebut() != null && r.getSemaineDebut().isBefore(seuilRetard)) {
                    enRetard++;
                } else {
                    nonSoumis++;
                }
            }
        }

        // Rapports attendus : 1/semaine depuis le début du stage
        int semainesEcoulees = 0;
        if (stage.getDateDebut() != null) {
            long jours = ChronoUnit.DAYS.between(stage.getDateDebut(), LocalDate.now());
            semainesEcoulees = (int) Math.max(0, jours / 7);
        }
        int rapportsSoumis = (int) rapports.stream()
                .filter(r -> r.getStatut() != RapportStage.Statut.BROUILLON)
                .count();
        int manquants = Math.max(0, semainesEcoulees - rapportsSoumis - rapports.size());
        nonSoumis += manquants;

        p.setTotal(rapports.size());
        p.setEnRetard(enRetard);
        p.setNonSoumis(nonSoumis);
        return p;
    }

    /** Collecte toutes les tâches actives du stage avec leur retard en jours. */
    private List<RisqueAnalyseRequest.TachePayload> collecterTaches(Stage stage) {
        List<Sprint> sprints = sprintRepository
                .findByStageIdAndDeletedAtIsNullOrderByNumeroAsc(stage.getId());
        List<RisqueAnalyseRequest.TachePayload> taches = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Sprint sprint : sprints) {
            List<Tache> tachesSprint = tacheRepository
                    .findBySprintIdAndDeletedAtIsNullOrderByDateDebutPrevueAsc(sprint.getId());

            for (Tache t : tachesSprint) {
                RisqueAnalyseRequest.TachePayload p = new RisqueAnalyseRequest.TachePayload();
                p.setTitre(t.getTitre());
                p.setStatut(t.getStatut().name());
                p.setJoursInactifs(calculerJoursInactifs(t, today));
                taches.add(p);
            }
        }
        return taches;
    }

    /**
     * Nombre de jours de dépassement pour une tâche.
     * Retourne 0 si la tâche est terminée ou pas encore en retard.
     */
    private int calculerJoursInactifs(Tache tache, LocalDate today) {
        return switch (tache.getStatut()) {
            case A_FAIRE -> {
                if (tache.getDateDebutPrevue() != null
                        && tache.getDateDebutPrevue().isBefore(today)) {
                    yield (int) ChronoUnit.DAYS.between(tache.getDateDebutPrevue(), today);
                }
                yield 0;
            }
            case EN_COURS -> {
                if (tache.getDateFinPrevue() != null
                        && tache.getDateFinPrevue().isBefore(today)) {
                    yield (int) ChronoUnit.DAYS.between(tache.getDateFinPrevue(), today);
                }
                yield 0;
            }
            // Terminées / validées / refusées : pas de retard à signaler
            default -> 0;
        };
    }

    /** Calcule les métriques d'activité de connexion de l'étudiant. */
    private RisqueAnalyseRequest.ActivitePayload calculerActivite(Etudiant etudiant) {
        RisqueAnalyseRequest.ActivitePayload p = new RisqueAnalyseRequest.ActivitePayload();

        // Dernière connexion (champ dénormalisé sur User)
        LocalDateTime derniereConnexion = etudiant.getDerniereConnexion();
        p.setDerniereConnexion(derniereConnexion != null ? derniereConnexion.toString() : null);

        // Fréquence sur 4 semaines (28 jours)
        LocalDateTime debut28j = LocalDateTime.now().minusDays(28);
        long nbConnexions = logActiviteRepository.countByUserIdAndActionAndCreatedAtBetween(
                etudiant.getId(), TypeAction.CONNEXION, debut28j, LocalDateTime.now());
        p.setFrequenceConnexionSemaine(nbConnexions / 4.0);

        return p;
    }

    // ════════════════════════════════════════════════════════════════════════
    // PERSISTANCE (UPSERT)
    // ════════════════════════════════════════════════════════════════════════

    private ResultatRisque upsertResultat(Stage stage, RisqueAnalyseResponse resp) {
        ResultatRisque entite = resultatRepository.findByStage(stage)
                .orElse(ResultatRisque.builder().stage(stage).build());

        entite.setScoreEngagement(resp.getScoreEngagement());
        entite.setNiveauRisque(parseNiveau(resp.getNiveauRisque()));
        entite.setAnalyseLe(LocalDateTime.now());

        try {
            entite.setAlertesJson(objectMapper.writeValueAsString(resp.getAlertes()));
            entite.setRecommandationsJson(objectMapper.writeValueAsString(resp.getRecommandations()));
            entite.setAnalyseJson(objectMapper.writeValueAsString(resp.getAnalyse()));
        } catch (Exception e) {
            log.warn("Erreur sérialisation détails risque : {}", e.getMessage());
        }

        return resultatRepository.save(entite);
    }

    private ResultatRisque.NiveauRisque parseNiveau(String niveau) {
        try {
            return ResultatRisque.NiveauRisque.valueOf(niveau);
        } catch (Exception e) {
            return ResultatRisque.NiveauRisque.STABLE;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // MAPPING DTO
    // ════════════════════════════════════════════════════════════════════════

    public ResultatRisqueDto.Response toDto(ResultatRisque entite, RisqueAnalyseResponse resp) {
        ResultatRisqueDto.Response dto = new ResultatRisqueDto.Response();
        dto.setId(entite.getId());
        dto.setStageId(entite.getStage().getId());

        Etudiant e = entite.getStage().getEtudiant();
        dto.setEtudiantNomComplet(e.getFirstName() + " " + e.getLastName());
        dto.setScoreEngagement(entite.getScoreEngagement());
        dto.setNiveauRisque(entite.getNiveauRisque().name());
        dto.setAnalyse(resp != null ? resp.getAnalyse() : null);
        dto.setAlertes(resp != null ? resp.getAlertes() : List.of());
        dto.setRecommandations(resp != null ? resp.getRecommandations() : List.of());
        dto.setAnalyseLe(entite.getAnalyseLe());
        return dto;
    }

    private ResultatRisqueDto.Resume toResume(ResultatRisque entite) {
        ResultatRisqueDto.Resume r = new ResultatRisqueDto.Resume();
        Stage stage = entite.getStage();
        Etudiant e  = stage.getEtudiant();

        r.setStageId(stage.getId());
        r.setEtudiantId(e.getId());
        r.setEtudiantPrenom(e.getFirstName());
        r.setEtudiantNom(e.getLastName());
        r.setEtudiantEmail(e.getEmail());
        r.setEtudiantNomComplet(e.getFirstName() + " " + e.getLastName());
        r.setEntrepriseNom(stage.getEntreprise() != null ? stage.getEntreprise().getNom() : "—");
        r.setUniversite(e.getEtablissement() != null ? e.getEtablissement().getNom() : "—");
        r.setScoreEngagement(entite.getScoreEngagement());
        r.setNiveauRisque(entite.getNiveauRisque().name());
        r.setAnalyseLe(entite.getAnalyseLe());

        // Alertes CRITIQUE + résumé des messages
        try {
            if (entite.getAlertesJson() != null) {
                List<RisqueAnalyseResponse.Alerte> alertes = objectMapper.readValue(
                        entite.getAlertesJson(), new TypeReference<>() {});
                r.setNbAlertesCritiques((int) alertes.stream()
                        .filter(a -> "CRITIQUE".equals(a.getNiveau()))
                        .count());
                r.setAlertesResume(alertes.stream()
                        .map(RisqueAnalyseResponse.Alerte::getMessage)
                        .filter(m -> m != null && !m.isBlank())
                        .toList());
            }
        } catch (Exception ignored) {}
        return r;
    }

    /**
     * Désérialise les détails JSON stockés pour éviter un re-appel IA.
     * Utilisé par {@link #getDernierResultat(Long)}.
     */
    private RisqueAnalyseResponse deserialiserDetails(ResultatRisque entite) {
        RisqueAnalyseResponse r = new RisqueAnalyseResponse();
        r.setSuccess(true);
        r.setScoreEngagement(entite.getScoreEngagement());
        r.setNiveauRisque(entite.getNiveauRisque().name());
        try {
            if (entite.getAlertesJson() != null)
                r.setAlertes(objectMapper.readValue(
                        entite.getAlertesJson(), new TypeReference<>() {}));
            if (entite.getRecommandationsJson() != null)
                r.setRecommandations(objectMapper.readValue(
                        entite.getRecommandationsJson(), new TypeReference<>() {}));
            if (entite.getAnalyseJson() != null)
                r.setAnalyse(objectMapper.readValue(
                        entite.getAnalyseJson(), RisqueAnalyseResponse.AnalyseDetail.class));
        } catch (Exception ignored) {}
        return r;
    }
}
