
package com.smartintern.backend.service;   
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Service Spring Boot qui délègue l'extraction et l'analyse du CV
 * au microservice Python/FastAPI dédié.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CvExtractionService {

    @Value("${cv.service.url:http://localhost:8000}")
    private String cvServiceUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // ── DTOs miroir du microservice Python ────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CvResponse {
        private boolean success;
        private CvStandardise cvStandardise;
        private String texteBrut;
        private int nbPages;
        private String message;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CvStandardise {
        private Profil profil;
        private List<Experience> experiences;
        private List<Formation> formations;
        private Competences competences;
        private List<Langue> langues;
        private List<String> certifications;
        private List<String> projets;
        private List<String> interets;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Profil {
        private String nom;
        private String prenom;
        private String email;
        private String telephone;
        private String adresse;
        private String nationalite;
        private String dateNaissance;
        private String titreProfessionnel;
        private String resume;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Experience {
        private String poste;
        private String entreprise;
        private String periode;
        private String dateDebut;
        private String dateFin;
        private String description;
        private String lieu;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Formation {
        private String diplome;
        private String etablissement;
        private String periode;
        private String dateDebut;
        private String dateFin;
        private String specialite;
        private String lieu;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Competences {
        private List<String> techniques;
        private List<String> softSkills;
        private List<String> outils;
        private List<String> autres;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Langue {
        private String langue;
        private String niveau;
    }

    // ── Méthodes publiques ─────────────────────────────────────────────────

    /**
     * Envoie le fichier PDF au microservice Python pour extraction complète.
     * Retourne les données structurées du CV.
     */
    public CvResponse extraireCv(MultipartFile file) {
        try {
            // Construire la requête multipart
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.APPLICATION_PDF);
            HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(fileResource, fileHeaders);
            body.add("file", filePart);

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            String url = cvServiceUrl + "/extract";
            log.info("Appel microservice extraction CV: {}", url);

            ResponseEntity<CvResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    CvResponse.class
            );

            CvResponse cvResponse = response.getBody();
            if (cvResponse != null && cvResponse.isSuccess()) {
                log.info("CV extrait avec succès ({} pages)", cvResponse.getNbPages());
            }
            return cvResponse;

        } catch (Exception e) {
            log.error("Erreur appel microservice CV: {}", e.getMessage());
            CvResponse errorResponse = new CvResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Service d'extraction indisponible: " + e.getMessage());
            return errorResponse;
        }
    }

    /**
     * Vérifie que le microservice Python est disponible.
     */
    public boolean isServiceDisponible() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    cvServiceUrl + "/health", String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.warn("Microservice CV indisponible: {}", e.getMessage());
            return false;
        }
    }
}
