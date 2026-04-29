package com.smartintern.backend.controller;

import com.smartintern.backend.entity.CvStandardise;
import com.smartintern.backend.entity.Etudiant;
import com.smartintern.backend.repository.EtudiantRepository;
import com.smartintern.backend.repository.UserRepository;
import com.smartintern.backend.service.AsyncCvService;
import com.smartintern.backend.service.CvExtractionService;
import com.smartintern.backend.service.CvStandardiseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/etudiant")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CvController {

    private final UserRepository userRepository;
    private final EtudiantRepository etudiantRepository;
    private final CvExtractionService cvExtractionService;
    private final CvStandardiseService cvStandardiseService;
    private final AsyncCvService asyncCvService;

    private static final String UPLOAD_DIR = "uploads/cv/";

    /**
     * Upload CV + lancement extraction IA en arrière-plan.
     * Retourne 202 immédiatement — le client interroge ensuite GET /cv/statut.
     */
    @PostMapping("/cv")
    public ResponseEntity<?> uploadCv(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {

        Etudiant etudiant = getEtudiantConnecte(authentication);

        // ── Validations ───────────────────────────────────────────────────
        String contentType = file.getContentType();
        java.util.Set<String> typesAcceptes = java.util.Set.of(
                "application/pdf",
                "image/jpeg", "image/jpg", "image/png", "image/webp"
        );
        if (contentType == null || !typesAcceptes.contains(contentType)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Formats acceptés : PDF, JPG, PNG (max 5 Mo)"));
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Le fichier ne doit pas dépasser 5 MB"));
        }

        // ── Lire les bytes AVANT la fin de la requête ─────────────────────
        // Important : après la fin du cycle HTTP, le stream du fichier peut
        // être fermé par le conteneur. On lit tout maintenant.
        byte[] fileBytes = file.getBytes();

        // ── Sauvegarde sur disque (synchrone, rapide) ─────────────────────
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);

        String ext = switch (contentType) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png"               -> ".png";
            case "image/webp"              -> ".webp";
            default                        -> ".pdf";
        };
        String filename = "cv_" + etudiant.getId() + "_" + System.currentTimeMillis() + ext;
        Path filePath = uploadPath.resolve(filename);
        Files.write(filePath, fileBytes);

        // Mettre à jour cvPath immédiatement
        etudiant.setCvPath(filename);
        etudiantRepository.save(etudiant);

        // ── Initialiser le statut EN_COURS en base ────────────────────────
        try {
            cvStandardiseService.initialiserEnCours(authentication.getName(), filename);
        } catch (Exception e) {
            log.warn("Impossible d'initialiser CvStandardise EN_COURS: {}", e.getMessage());
        }

        // ── Lancer l'extraction en arrière-plan ───────────────────────────
        asyncCvService.extraireEtPersister(
                fileBytes,
                file.getOriginalFilename(),
                authentication.getName(),
                filename);

        log.info("CV uploadé pour {} → extraction async lancée ({})", etudiant.getEmail(), filename);

        return ResponseEntity.accepted().body(Map.of(
                "message", "CV reçu — extraction IA en cours",
                "filename", filename,
                "statut", "EN_COURS",
                "statut_url", "/api/etudiant/cv/statut"
        ));
    }

    /**
     * Statut de l'extraction en cours.
     * Le frontend poll cet endpoint après un POST /cv jusqu'à obtenir EXTRAIT ou ERREUR.
     */
    @GetMapping("/cv/statut")
    public ResponseEntity<?> getStatutExtraction(Authentication authentication) {
        CvStandardise.StatutExtraction statut =
                cvStandardiseService.getStatutExtraction(authentication.getName());

        if (statut == null) {
            return ResponseEntity.ok(Map.of(
                    "statut", "AUCUN",
                    "message", "Aucun CV en cours d'extraction"
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("statut", statut.name());
        response.put("message", switch (statut) {
            case EN_COURS -> "Extraction IA en cours, veuillez patienter…";
            case EXTRAIT  -> "CV extrait et structuré avec succès";
            case ERREUR   -> "Erreur lors de l'extraction — réessayez ou contactez le support";
        });
        response.put("pret", statut == CvStandardise.StatutExtraction.EXTRAIT);

        return ResponseEntity.ok(response);
    }

    /**
     * Données CV structurées de l'étudiant connecté.
     */
    @GetMapping("/cv")
    public ResponseEntity<?> getCvInfo(Authentication authentication) {
        Etudiant etudiant = getEtudiantConnecte(authentication);

        if (etudiant.getCvPath() == null) {
            return ResponseEntity.ok(Map.of("hasCv", false));
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("hasCv", true);
        resp.put("filename", etudiant.getCvPath());

        CvStandardise.StatutExtraction statut =
                cvStandardiseService.getStatutExtraction(authentication.getName());
        resp.put("statutExtraction", statut != null ? statut.name() : "AUCUN");

        if (etudiant.getCvDataJson() != null && !etudiant.getCvDataJson().isEmpty()) {
            resp.put("cvData", deserializerCvData(etudiant.getCvDataJson()));
            resp.put("analysed", true);
        } else {
            resp.put("analysed", false);
        }

        // Données structurées enrichies si disponibles
        if (statut == CvStandardise.StatutExtraction.EXTRAIT) {
            try {
                CvStandardise cv = cvStandardiseService.getCvByEtudiantEmail(authentication.getName());
                resp.put("scoreCompletude", cv.getScoreCompletude());
                resp.put("niveauQualite", cv.getNiveauQualite());
                resp.put("dateExtraction", cv.getDateExtraction());
            } catch (Exception ignored) { /* données enrichies optionnelles */ }
        }

        return ResponseEntity.ok(resp);
    }

    /**
     * Ré-analyse manuelle du CV existant (synchrone, depuis le disque).
     * Retourne aussi 202 — même flow que l'upload initial.
     */
    @PostMapping("/cv/reanalyse")
    public ResponseEntity<?> reanalyseCv(Authentication authentication) throws IOException {
        Etudiant etudiant = getEtudiantConnecte(authentication);

        if (etudiant.getCvPath() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Aucun CV uploadé"));
        }
        if (!cvExtractionService.isServiceDisponible()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Service d'extraction temporairement indisponible"));
        }

        Path filePath = Paths.get(UPLOAD_DIR, etudiant.getCvPath());
        if (!Files.exists(filePath)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Fichier CV introuvable sur le serveur"));
        }

        byte[] fileBytes = Files.readAllBytes(filePath);

        cvStandardiseService.initialiserEnCours(authentication.getName(), etudiant.getCvPath());

        asyncCvService.extraireEtPersister(
                fileBytes,
                etudiant.getCvPath(),
                authentication.getName(),
                etudiant.getCvPath());

        return ResponseEntity.accepted().body(Map.of(
                "message", "Ré-analyse IA lancée en arrière-plan",
                "statut", "EN_COURS",
                "statut_url", "/api/etudiant/cv/statut"
        ));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Etudiant getEtudiantConnecte(Authentication authentication) {
        return etudiantRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException(
                        "Étudiant non trouvé : " + authentication.getName()));
    }

    private Object deserializerCvData(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }
}
