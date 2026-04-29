package com.smartintern.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartintern.backend.entity.CvStandardise;
import com.smartintern.backend.entity.Etudiant;
import com.smartintern.backend.repository.CvStandardiseRepository;
import com.smartintern.backend.repository.EtudiantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

/**
 * Service async dédié à l'extraction CV.
 * Séparé de CvExtractionService pour que Spring puisse proxifier @Async
 * correctement (l'appel doit venir de l'extérieur du bean).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncCvService {

    private final CvExtractionService cvExtractionService;
    private final CvStandardiseService cvStandardiseService;
    private final CvStandardiseRepository cvStandardiseRepository;
    private final EtudiantRepository etudiantRepository;
    private final ObjectMapper objectMapper;

    /**
     * Lance l'extraction CV en arrière-plan.
     *
     * @param fileBytes       contenu du fichier (lu avant la fin de la requête HTTP)
     * @param originalFilename nom original du fichier uploadé
     * @param emailEtudiant   email de l'étudiant connecté
     * @param savedFilename   nom enregistré sur disque (pour référence)
     */
    @Async("cvExecutor")
    @Transactional
    public CompletableFuture<Void> extraireEtPersister(byte[] fileBytes,
                                                        String originalFilename,
                                                        String emailEtudiant,
                                                        String savedFilename,
                                                        String contentType) {
        log.info("[ASYNC] Démarrage extraction CV pour {} ({})", emailEtudiant, originalFilename);

        // Marquer EN_COURS si un CvStandardise existe déjà
        cvStandardiseRepository.findByEtudiantEmail(emailEtudiant).ifPresent(cv -> {
            cv.setStatutExtraction(CvStandardise.StatutExtraction.EN_COURS);
            cvStandardiseRepository.save(cv);
        });

        try {
            MultipartFile multipartFile = new ByteArrayMultipartFile(
                    fileBytes, originalFilename,
                    contentType != null ? contentType : "application/pdf");

            CvExtractionService.CvResponse cvResponse =
                    cvExtractionService.extraireCv(multipartFile);

            if (cvResponse != null && cvResponse.isSuccess()
                    && cvResponse.getCvStandardise() != null) {

                // Persister dans les tables structurées
                cvStandardiseService.persisterCvExtrait(emailEtudiant, cvResponse, savedFilename);

                // Mettre à jour le JSON brut sur l'entité Etudiant
                etudiantRepository.findByEmail(emailEtudiant).ifPresent(etudiant -> {
                    try {
                        etudiant.setCvDataJson(
                                objectMapper.writeValueAsString(cvResponse.getCvStandardise()));
                        etudiantRepository.save(etudiant);
                    } catch (Exception e) {
                        log.warn("[ASYNC] Impossible de sérialiser cvDataJson: {}", e.getMessage());
                    }
                });

                log.info("[ASYNC] Extraction CV terminée pour {} — score={}", emailEtudiant,
                        cvResponse.getCvStandardise().getProfil() != null ? "ok" : "partiel");

            } else {
                String erreur = cvResponse != null ? cvResponse.getMessage() : "réponse nulle";
                log.error("[ASYNC] Extraction échouée pour {}: {}", emailEtudiant, erreur);
                marquerErreur(emailEtudiant, erreur);
            }

        } catch (Exception e) {
            log.error("[ASYNC] Exception extraction CV pour {}: {}", emailEtudiant, e.getMessage());
            marquerErreur(emailEtudiant, e.getMessage());
        }

        return CompletableFuture.completedFuture(null);
    }

    private void marquerErreur(String email, String message) {
        cvStandardiseRepository.findByEtudiantEmail(email).ifPresent(cv -> {
            cv.setStatutExtraction(CvStandardise.StatutExtraction.ERREUR);
            cv.setRecommandations("Erreur extraction: " + message);
            cvStandardiseRepository.save(cv);
        });
    }

    // ── MultipartFile minimal à partir d'un byte[] ────────────────────────
    // Utilisé pour passer les bytes (déjà lus depuis la requête HTTP)
    // à CvExtractionService sans que la requête soit toujours active.

    private static final class ByteArrayMultipartFile implements MultipartFile {
        private final byte[] content;
        private final String originalFilename;
        private final String contentType;

        ByteArrayMultipartFile(byte[] content, String originalFilename, String contentType) {
            this.content = content;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
        }

        @Override public String getName()             { return "file"; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType()      { return contentType; }
        @Override public boolean isEmpty()            { return content.length == 0; }
        @Override public long getSize()               { return content.length; }
        @Override public byte[] getBytes()            { return content; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }

        @Override
        public void transferTo(File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
