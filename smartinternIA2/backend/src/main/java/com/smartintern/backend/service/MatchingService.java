package com.smartintern.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartintern.backend.dto.MatchingDto;
import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service Java qui orchestre les appels au microservice IA pour :
 *   - Matching candidats pour une offre  (côté entreprise)
 *   - Recommandation d'offres            (côté étudiant)
 *
 * Pattern RestTemplate identique à CvExtractionService.
 * Persiste scoreMatching dans l'entité Candidature.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    @Value("${ai.matching.url:http://localhost:8000}")
    private String aiMatchingUrl;

    private final RestTemplate        restTemplate;
    private final ObjectMapper        objectMapper;
    private final OffreStageRepository offreRepo;
    private final CandidatureRepository candidatureRepo;
    private final EtudiantRepository  etudiantRepo;
    private final CvStandardiseRepository cvRepo;

    // ════════════════════════════════════════════════════════════════════
    // MATCHING CANDIDATS  (entreprise)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Classe et score les candidats d'une offre via le moteur IA.
     * Persiste le scoreMatching dans chaque Candidature.
     *
     * @param offreId  identifiant de l'offre de stage
     * @return liste de CandidatScore triée par score décroissant
     */
    public List<MatchingDto.CandidatScore> classerCandidats(Long offreId) {
        OffreStage offre = offreRepo.findById(offreId)
                .orElseThrow(() -> new RuntimeException("Offre introuvable : " + offreId));

        List<Candidature> candidatures = candidatureRepo.findByOffreId(offreId);
        if (candidatures.isEmpty()) {
            return Collections.emptyList();
        }

        // Construction de la requête Python
        MatchingDto.MatchingRequest req = new MatchingDto.MatchingRequest();
        req.setOffre(toOffreInput(offre));

        for (Candidature c : candidatures) {
            try {
                MatchingDto.CandidatInput ci = toCandidatInput(c);
                req.getCandidats().add(ci);
            } catch (Exception e) {
                log.warn("Candidature {} ignorée (profil incomplet) : {}", c.getId(), e.getMessage());
            }
        }

        // Appel microservice IA
        MatchingDto.MatchingResponse response = appelMatchingCandidats(req);
        if (response == null || response.getData() == null) {
            log.error("Réponse IA vide pour offre {}", offreId);
            return Collections.emptyList();
        }

        List<MatchingDto.CandidatScore> classement = response.getData().getCandidatsClasses();

        // Persistance des scores dans les entités Candidature
        persistScores(classement);

        return classement;
    }

    // ════════════════════════════════════════════════════════════════════
    // RECOMMANDATION OFFRES  (étudiant)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Recommande des offres pour un étudiant via le moteur IA.
     *
     * @param etudiantId  identifiant de l'étudiant
     * @param limit       nombre maximum d'offres retournées (défaut 10)
     * @return liste d'OffreRecommandee triée par score décroissant
     */
    public List<MatchingDto.OffreRecommandee> recommanderOffres(Long etudiantId, int limit) {
        Etudiant etudiant = etudiantRepo.findById(etudiantId)
                .orElseThrow(() -> new RuntimeException("Étudiant introuvable : " + etudiantId));

        // Offres actives et validées uniquement
        List<OffreStage> offresActives = offreRepo.findByStatutAndStatutValidation(
                OffreStage.Statut.ACTIVE,
                OffreStage.StatutValidation.VALIDEE,
                org.springframework.data.domain.Pageable.unpaged()
        ).getContent();

        if (offresActives.isEmpty()) {
            return Collections.emptyList();
        }

        MatchingDto.RecommandationRequest req = new MatchingDto.RecommandationRequest();
        req.setEtudiant(toEtudiantInput(etudiant));
        req.setLimit(limit);
        offresActives.forEach(o -> req.getOffres().add(toOffreInput(o)));

        MatchingDto.RecommandationResponse response = appelRecommandationOffres(req);
        if (response == null || response.getData() == null) {
            log.error("Réponse IA vide pour étudiant {}", etudiantId);
            return Collections.emptyList();
        }

        return response.getData().getOffresRecommandees();
    }

    // ════════════════════════════════════════════════════════════════════
    // APPELS REST VERS LE MICROSERVICE IA
    // ════════════════════════════════════════════════════════════════════

    private MatchingDto.MatchingResponse appelMatchingCandidats(MatchingDto.MatchingRequest req) {
        String url = aiMatchingUrl + "/ai/matching/candidats";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<MatchingDto.MatchingRequest> entity = new HttpEntity<>(req, headers);
            ResponseEntity<MatchingDto.MatchingResponse> resp = restTemplate.exchange(
                    url, HttpMethod.POST, entity, MatchingDto.MatchingResponse.class);
            return resp.getBody();
        } catch (Exception e) {
            log.error("Erreur appel IA matching candidats [{}] : {}", url, e.getMessage());
            return null;
        }
    }

    private MatchingDto.RecommandationResponse appelRecommandationOffres(MatchingDto.RecommandationRequest req) {
        String url = aiMatchingUrl + "/ai/recommandation/offres";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<MatchingDto.RecommandationRequest> entity = new HttpEntity<>(req, headers);
            ResponseEntity<MatchingDto.RecommandationResponse> resp = restTemplate.exchange(
                    url, HttpMethod.POST, entity, MatchingDto.RecommandationResponse.class);
            return resp.getBody();
        } catch (Exception e) {
            log.error("Erreur appel IA recommandation offres [{}] : {}", url, e.getMessage());
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // PERSISTANCE SCORES
    // ════════════════════════════════════════════════════════════════════

    private void persistScores(List<MatchingDto.CandidatScore> classement) {
        for (MatchingDto.CandidatScore cs : classement) {
            if (cs.getCandidatureId() == null) continue;
            candidatureRepo.findById(cs.getCandidatureId()).ifPresent(c -> {
                if (cs.getScoreMatching() != null) {
                    c.setScoreMatching((float) cs.getScoreMatching().getScore());
                    candidatureRepo.save(c);
                }
            });
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // CONVERSION ENTITÉS → INPUTS IA
    // ════════════════════════════════════════════════════════════════════

    private MatchingDto.OffreInput toOffreInput(OffreStage offre) {
        MatchingDto.OffreInput dto = new MatchingDto.OffreInput();
        dto.setOffreId(offre.getId());
        dto.setTitre(nvl(offre.getTitre()));
        dto.setDescription(nvl(offre.getDescription()));
        dto.setDomaine(nvl(offre.getDomaine()));
        dto.setTypeStage(nvl(offre.getTypeStage()));
        dto.setNiveauRequis(nvl(offre.getNiveauRequis()));
        dto.setDureeMois(offre.getDureeMois());
        // Compétences requises : non stockées en liste dans OffreStage v1
        // → on extrait les mots-clés depuis la description
        return dto;
    }

    private MatchingDto.CandidatInput toCandidatInput(Candidature c) {
        Etudiant etudiant = c.getEtudiant();
        MatchingDto.CandidatInput dto = new MatchingDto.CandidatInput();
        dto.setCandidatureId(c.getId());
        dto.setEtudiantId(etudiant.getId());
        dto.setNomComplet(etudiant.getFirstName() + " " + etudiant.getLastName());
        dto.setNiveauFormation(nvl(etudiant.getFiliere()));

        // Compétences depuis CvStandardise
        CvStandardise cv = etudiant.getCvStandardise();
        if (cv != null) {
            dto.setScoreCompletudeCv(cv.getScoreCompletude() != null ? cv.getScoreCompletude() : 0.0);

            if (cv.getCompetences() != null) {
                cv.getCompetences().forEach(comp -> {
                    if (comp.getType() == Competence.TypeCompetence.TECHNIQUE
                            || comp.getType() == Competence.TypeCompetence.OUTIL) {
                        dto.getCompetencesTechniques().add(comp.getNom());
                    } else if (comp.getType() == Competence.TypeCompetence.SOFT_SKILL) {
                        dto.getSoftSkills().add(comp.getNom());
                    }
                });
            }

            // Expériences : concaténation des postes et descriptions
            if (cv.getExperiences() != null) {
                String expTexte = cv.getExperiences().stream()
                        .map(e -> nvl(e.getPoste()) + " " + nvl(e.getDescription()))
                        .collect(Collectors.joining(" "));
                dto.setExperiencesTexte(expTexte.trim());
            }

            // Formations
            if (cv.getFormations() != null) {
                String formTexte = cv.getFormations().stream()
                        .map(f -> nvl(f.getDiplome()) + " " + nvl(f.getEtablissement()))
                        .collect(Collectors.joining(" "));
                dto.setFormationsTexte(formTexte.trim());
            }

            // Projets
            if (cv.getProfil() != null) {
                // Le champ projets vient du JSON brut ou d'une liste dédiée
            }

            // Parsing JSON brut si disponible (données plus riches)
            if (cv.getDonneesJsonBrutes() != null && !cv.getDonneesJsonBrutes().isBlank()) {
                enrichirDepuisJsonBrut(dto, cv.getDonneesJsonBrutes());
            }
        }
        return dto;
    }

    private MatchingDto.EtudiantInput toEtudiantInput(Etudiant etudiant) {
        MatchingDto.EtudiantInput dto = new MatchingDto.EtudiantInput();
        dto.setEtudiantId(etudiant.getId());
        dto.setNomComplet(etudiant.getFirstName() + " " + etudiant.getLastName());
        dto.setNiveauFormation(nvl(etudiant.getFiliere()));

        CvStandardise cv = etudiant.getCvStandardise();
        if (cv != null) {
            dto.setScoreCompletudeCv(cv.getScoreCompletude() != null ? cv.getScoreCompletude() : 0.0);

            if (cv.getCompetences() != null) {
                cv.getCompetences().forEach(comp -> {
                    if (comp.getType() == Competence.TypeCompetence.TECHNIQUE
                            || comp.getType() == Competence.TypeCompetence.OUTIL) {
                        dto.getCompetencesTechniques().add(comp.getNom());
                    } else if (comp.getType() == Competence.TypeCompetence.SOFT_SKILL) {
                        dto.getSoftSkills().add(comp.getNom());
                    }
                });
            }

            if (cv.getExperiences() != null) {
                String expTexte = cv.getExperiences().stream()
                        .map(e -> nvl(e.getPoste()) + " " + nvl(e.getDescription()))
                        .collect(Collectors.joining(" "));
                dto.setExperiencesTexte(expTexte.trim());
            }

            if (cv.getFormations() != null) {
                String formTexte = cv.getFormations().stream()
                        .map(f -> nvl(f.getDiplome()) + " " + nvl(f.getEtablissement()))
                        .collect(Collectors.joining(" "));
                dto.setFormationsTexte(formTexte.trim());
            }

            if (cv.getDonneesJsonBrutes() != null && !cv.getDonneesJsonBrutes().isBlank()) {
                enrichirEtudiantDepuisJsonBrut(dto, cv.getDonneesJsonBrutes());
            }
        }
        return dto;
    }

    /**
     * Enrichit un CandidatInput depuis le JSON brut de donneesJsonBrutes
     * (structure {competences: {techniques: [...], softSkills: [...]}, projets: [...]}).
     */
    @SuppressWarnings("unchecked")
    private void enrichirDepuisJsonBrut(MatchingDto.CandidatInput dto, String jsonBrut) {
        try {
            Map<String, Object> root = objectMapper.readValue(jsonBrut, new TypeReference<>() {});
            Object compObj = root.get("competences");
            if (compObj instanceof Map) {
                Map<String, Object> comps = (Map<String, Object>) compObj;
                ajouterListeDepuisMap(comps, "techniques",  dto.getCompetencesTechniques());
                ajouterListeDepuisMap(comps, "softSkills",  dto.getSoftSkills());
                ajouterListeDepuisMap(comps, "outils",      dto.getCompetencesTechniques());
            }
            Object projObj = root.get("projets");
            if (projObj instanceof List) {
                ((List<?>) projObj).forEach(p -> dto.getProjets().add(p.toString()));
            }
        } catch (Exception e) {
            log.debug("Parsing JSON brut échoué (candidat) : {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void enrichirEtudiantDepuisJsonBrut(MatchingDto.EtudiantInput dto, String jsonBrut) {
        try {
            Map<String, Object> root = objectMapper.readValue(jsonBrut, new TypeReference<>() {});
            Object compObj = root.get("competences");
            if (compObj instanceof Map) {
                Map<String, Object> comps = (Map<String, Object>) compObj;
                ajouterListeDepuisMap(comps, "techniques",  dto.getCompetencesTechniques());
                ajouterListeDepuisMap(comps, "softSkills",  dto.getSoftSkills());
                ajouterListeDepuisMap(comps, "outils",      dto.getCompetencesTechniques());
            }
            Object projObj = root.get("projets");
            if (projObj instanceof List) {
                ((List<?>) projObj).forEach(p -> dto.getProjets().add(p.toString()));
            }
        } catch (Exception e) {
            log.debug("Parsing JSON brut échoué (étudiant) : {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void ajouterListeDepuisMap(Map<String, Object> map, String cle, List<String> cible) {
        Object val = map.get(cle);
        if (val instanceof List) {
            ((List<?>) val).forEach(item -> {
                String s = item.toString().trim();
                if (!s.isEmpty() && !cible.contains(s)) {
                    cible.add(s);
                }
            });
        }
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}
