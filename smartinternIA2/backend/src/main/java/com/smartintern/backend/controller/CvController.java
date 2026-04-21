package com.smartintern.backend.controller;

import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.UserRepository;
import com.smartintern.backend.service.CvExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;


/* package com.smartintern.backend.controller;



@RestController
@RequestMapping("/api/etudiant")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CvController {

    private final UserRepository userRepository;
    private static final String UPLOAD_DIR = "uploads/cv/";

    @PostMapping("/cv")
    public ResponseEntity<?> uploadCv(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        // Vérification type fichier
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            return ResponseEntity.badRequest().body("Seuls les fichiers PDF sont acceptés");
        }

        // Vérification taille (5MB max)
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body("Le fichier ne doit pas dépasser 5MB");
        }

        // Création du dossier si inexistant
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Sauvegarde avec nom unique
        String filename = "cv_" + user.getId() + "_" + System.currentTimeMillis() + ".pdf";
        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Mise à jour du chemin CV dans le profil utilisateur
        user.setCvPath(filename);
        userRepository.save(user);

        return ResponseEntity.ok().body(java.util.Map.of(
                "message", "CV téléversé avec succès",
                "filename", filename
        ));
    }

    @GetMapping("/cv")
    public ResponseEntity<?> getCvInfo(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (user.getCvPath() == null) {
            return ResponseEntity.ok().body(java.util.Map.of("hasCv", false));
        }
        return ResponseEntity.ok().body(java.util.Map.of(
                "hasCv", true,
                "filename", user.getCvPath()
        ));
    }
}
 */





@RestController
@RequestMapping("/api/etudiant")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CvController {

    private final UserRepository userRepository;
    private final CvExtractionService cvExtractionService;
    private static final String UPLOAD_DIR = "uploads/cv/";

    /**
     * Upload du CV + extraction automatique des données via le microservice Python.
     */
    @PostMapping("/cv")
    public ResponseEntity<?> uploadCv(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Seuls les fichiers PDF sont acceptés"));
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Le fichier ne doit pas dépasser 5MB"));
        }

        // Sauvegarde fichier
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);

        String filename = "cv_" + user.getId() + "_" + System.currentTimeMillis() + ".pdf";
        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        user.setCvPath(filename);

        // Appel microservice Python extraction IA
        CvExtractionService.CvResponse cvData = cvExtractionService.extraireCv(file);

        if (cvData != null && cvData.isSuccess() && cvData.getCvStandardise() != null) {
            user.setCvDataJson(serializerCvData(cvData.getCvStandardise()));
            userRepository.save(user);
            return ResponseEntity.ok(Map.of(
                    "message", "CV téléversé et analysé avec succès",
                    "filename", filename,
                    "cv_data", cvData.getCvStandardise(),
                    "nb_pages", cvData.getNbPages()
            ));
        }

        userRepository.save(user);
        return ResponseEntity.ok(Map.of(
                "message", "CV téléversé (analyse IA indisponible)",
                "filename", filename,
                "cv_extracted", false
        ));
    }

    /**
     * Récupère les infos CV de l'étudiant connecté.
     */
    @GetMapping("/cv")
    public ResponseEntity<?> getCvInfo(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (user.getCvPath() == null) {
            return ResponseEntity.ok(Map.of("hasCv", false));
        }

        var resp = new java.util.HashMap<String, Object>();
        resp.put("hasCv", true);
        resp.put("filename", user.getCvPath());

        if (user.getCvDataJson() != null && !user.getCvDataJson().isEmpty()) {
            resp.put("cv_data", deserializerCvData(user.getCvDataJson()));
            resp.put("analysed", true);
        } else {
            resp.put("analysed", false);
        }

        return ResponseEntity.ok(resp);
    }

    /**
     * Ré-analyse le CV existant si le service était indisponible lors de l'upload.
     */
    @PostMapping("/cv/reanalyse")
    public ResponseEntity<?> reanalyseCv(Authentication authentication) throws IOException {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (user.getCvPath() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Aucun CV uploadé"));
        }

        if (!cvExtractionService.isServiceDisponible()) {
            return ResponseEntity.status(503)
                    .body(Map.of("message", "Service d'extraction temporairement indisponible"));
        }

        Path filePath = Paths.get(UPLOAD_DIR, user.getCvPath());
        if (!Files.exists(filePath)) {
            return ResponseEntity.status(404).body(Map.of("message", "Fichier CV introuvable"));
        }

        org.springframework.mock.web.MockMultipartFile mockFile =
            new org.springframework.mock.web.MockMultipartFile(
                "file", user.getCvPath(), "application/pdf",
                Files.readAllBytes(filePath)
            );

        CvExtractionService.CvResponse cvData = cvExtractionService.extraireCv(mockFile);

        if (cvData != null && cvData.isSuccess()) {
            user.setCvDataJson(serializerCvData(cvData.getCvStandardise()));
            userRepository.save(user);
            return ResponseEntity.ok(Map.of(
                    "message", "CV ré-analysé avec succès",
                    "cv_data", cvData.getCvStandardise()
            ));
        }

        return ResponseEntity.status(500)
                .body(Map.of("message", "Échec analyse: " + (cvData != null ? cvData.getMessage() : "erreur inconnue")));
    }

    // ── Helpers JSON ───────────────────────────────────────────────────────

    private String serializerCvData(CvExtractionService.CvStandardise data) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
        } catch (Exception e) { return null; }
    }

    private Object deserializerCvData(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
        } catch (Exception e) { return null; }
    }
}



