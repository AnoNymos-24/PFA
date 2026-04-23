package com.smartintern.backend.controller;

import com.smartintern.backend.entity.Etudiant;
import com.smartintern.backend.repository.EtudiantRepository;
import com.smartintern.backend.repository.UserRepository;
import com.smartintern.backend.service.CvExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

@RestController
@RequestMapping("/api/etudiant")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CvController {

    private final UserRepository userRepository;
    private final EtudiantRepository etudiantRepository;
    private final CvExtractionService cvExtractionService;
    private static final String UPLOAD_DIR = "uploads/cv/";

    /** Upload CV + extraction IA */
    @PostMapping("/cv")
    public ResponseEntity<?> uploadCv(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {

        Etudiant etudiant = getEtudiantConnecte(authentication);

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Seuls les fichiers PDF sont acceptés"));
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Le fichier ne doit pas dépasser 5MB"));
        }

        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);

        String filename = "cv_" + etudiant.getId() + "_" + System.currentTimeMillis() + ".pdf";
        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        etudiant.setCvPath(filename);

        CvExtractionService.CvResponse cvData = cvExtractionService.extraireCv(file);

        if (cvData != null && cvData.isSuccess() && cvData.getCvStandardise() != null) {
            etudiant.setCvDataJson(serializerCvData(cvData.getCvStandardise()));
            etudiantRepository.save(etudiant);
            return ResponseEntity.ok(Map.of(
                    "message",  "CV téléversé et analysé avec succès",
                    "filename", filename,
                    "cv_data",  cvData.getCvStandardise(),
                    "nb_pages", cvData.getNbPages()
            ));
        }

        etudiantRepository.save(etudiant);
        return ResponseEntity.ok(Map.of(
                "message",      "CV téléversé (analyse IA indisponible)",
                "filename",     filename,
                "cv_extracted", false
        ));
    }

    /** Infos CV de l'étudiant connecté */
    @GetMapping("/cv")
    public ResponseEntity<?> getCvInfo(Authentication authentication) {
        Etudiant etudiant = getEtudiantConnecte(authentication);

        if (etudiant.getCvPath() == null) {
            return ResponseEntity.ok(Map.of("hasCv", false));
        }

        var resp = new java.util.HashMap<String, Object>();
        resp.put("hasCv",    true);
        resp.put("filename", etudiant.getCvPath());

        if (etudiant.getCvDataJson() != null && !etudiant.getCvDataJson().isEmpty()) {
            resp.put("cv_data",  deserializerCvData(etudiant.getCvDataJson()));
            resp.put("analysed", true);
        } else {
            resp.put("analysed", false);
        }

        return ResponseEntity.ok(resp);
    }

    /**
     * Ré-analyse le CV existant.
     * ✅ Fix : utilise ByteArrayResource au lieu de MockMultipartFile (classe de test)
     */
    @PostMapping("/cv/reanalyse")
    public ResponseEntity<?> reanalyseCv(Authentication authentication) throws IOException {
        Etudiant etudiant = getEtudiantConnecte(authentication);

        if (etudiant.getCvPath() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Aucun CV uploadé"));
        }
        if (!cvExtractionService.isServiceDisponible()) {
            return ResponseEntity.status(503)
                    .body(Map.of("message", "Service d'extraction temporairement indisponible"));
        }

        Path filePath = Paths.get(UPLOAD_DIR, etudiant.getCvPath());
        if (!Files.exists(filePath)) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "Fichier CV introuvable sur le serveur"));
        }

        // ✅ Remplacement de MockMultipartFile par un vrai MultipartFile standard
        byte[] fileBytes = Files.readAllBytes(filePath);
        MultipartFile multipartFile = new SimpleMultipartFile(
                etudiant.getCvPath(), "application/pdf", fileBytes);

        CvExtractionService.CvResponse cvData = cvExtractionService.extraireCv(multipartFile);

        if (cvData != null && cvData.isSuccess()) {
            etudiant.setCvDataJson(serializerCvData(cvData.getCvStandardise()));
            etudiantRepository.save(etudiant);
            return ResponseEntity.ok(Map.of(
                    "message", "CV ré-analysé avec succès",
                    "cv_data", cvData.getCvStandardise()
            ));
        }

        return ResponseEntity.status(500)
                .body(Map.of("message", "Échec analyse : " +
                        (cvData != null ? cvData.getMessage() : "erreur inconnue")));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Etudiant getEtudiantConnecte(Authentication authentication) {
        String email = authentication.getName();
        return etudiantRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé : " + email));
    }

    private String serializerCvData(CvExtractionService.CvStandardise data) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(data);
        } catch (Exception e) { return null; }
    }

    private Object deserializerCvData(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, Object.class);
        } catch (Exception e) { return null; }
    }

    // ── Implémentation légère de MultipartFile (remplace MockMultipartFile) ─

    private static class SimpleMultipartFile implements MultipartFile {
        private final String filename;
        private final String contentType;
        private final byte[] content;

        SimpleMultipartFile(String filename, String contentType, byte[] content) {
            this.filename    = filename;
            this.contentType = contentType;
            this.content     = content;
        }

        @Override public String getName()             { return "file"; }
        @Override public String getOriginalFilename() { return filename; }
        @Override public String getContentType()      { return contentType; }
        @Override public boolean isEmpty()            { return content.length == 0; }
        @Override public long getSize()               { return content.length; }
        @Override public byte[] getBytes()            { return content; }

        @Override
        public java.io.InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }
    }
}