package com.smartintern.backend.client;

import com.smartintern.backend.dto.risque.RisqueAnalyseRequest;
import com.smartintern.backend.dto.risque.RisqueAnalyseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * AD-04 — Client HTTP dédié vers le microservice Python risque.
 *
 * <p>Appelle {@code POST {cv.service.url}/ai/stage/analyse-risque} avec un timeout de 30 s.
 *
 * <p>En cas d'erreur réseau, de timeout ou de réponse invalide, retourne un score de
 * secours {@code scoreEngagement = -1} (signal d'erreur explicite) avec le champ
 * {@code erreur} renseigné, afin que l'appelant puisse distinguer une vraie analyse
 * d'un résultat de fallback.
 *
 * <p>Utilise son propre {@link RestTemplate} configuré à 30 s, distinct du bean
 * global de l'application.
 */
@Slf4j
@Component
public class RisquePythonClient {

    private static final String ENDPOINT   = "/ai/stage/analyse-risque";
    private static final int    TIMEOUT_MS = 30_000; // 30 secondes

    /** Score sentinelle indiquant un échec de communication avec le microservice. */
    public static final int SCORE_FALLBACK = -1;

    private final RestTemplate restTemplate;
    private final String       baseUrl;

    public RisquePythonClient(
            @Value("${cv.service.url:http://localhost:8000}") String baseUrl) {
        this.baseUrl = baseUrl;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Appelle le microservice Python et retourne l'analyse.
     *
     * <p>Retourne <b>jamais {@code null}</b> — en cas d'erreur, retourne un objet
     * de fallback avec {@code scoreEngagement = -1} et {@code success = false}.
     *
     * @param payload métriques du stage à analyser
     * @return réponse de l'analyse (potentiellement fallback si IA indisponible)
     */
    public RisqueAnalyseResponse analyser(RisqueAnalyseRequest payload) {
        String url = baseUrl + ENDPOINT;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<RisqueAnalyseRequest> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<RisqueAnalyseResponse> resp =
                    restTemplate.postForEntity(url, entity, RisqueAnalyseResponse.class);

            if (resp.getStatusCode().is2xxSuccessful()
                    && resp.getBody() != null
                    && resp.getBody().isSuccess()) {
                log.debug("Microservice risque OK — stageId={} score={}",
                        payload.getStage().getId(),
                        resp.getBody().getScoreEngagement());
                return resp.getBody();
            }
            log.error("Microservice risque KO — statut={} body={}",
                    resp.getStatusCode(), resp.getBody());
        } catch (Exception e) {
            log.error("Erreur appel microservice risque ({}) : {}", url, e.getMessage());
        }
        return fallback("Microservice IA indisponible — " + url);
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    /**
     * Construit une réponse de secours avec {@code scoreEngagement = -1}.
     *
     * <p>La valeur {@code -1} est une sentinelle exploitable par le service
     * appelant pour décider de ne pas persister un résultat ou d'afficher
     * un message d'erreur explicite à l'utilisateur.
     */
    private RisqueAnalyseResponse fallback(String messageErreur) {
        RisqueAnalyseResponse r = new RisqueAnalyseResponse();
        r.setSuccess(false);
        r.setScoreEngagement(SCORE_FALLBACK);          // -1 = signal d'erreur explicite
        r.setNiveauRisque("INCONNU");
        r.setErreur(messageErreur + " — relancer l'analyse lorsque le service sera disponible.");
        r.setAnalyse(new RisqueAnalyseResponse.AnalyseDetail());
        r.setAlertes(List.of());
        r.setRecommandations(List.of(
                "Relancer l'analyse lorsque le microservice IA sera disponible."));
        return r;
    }
}
