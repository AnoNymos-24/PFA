package com.smartintern.backend.controller;

import com.smartintern.backend.service.CandidatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/candidatures")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CandidatureController {

    private final CandidatureService service;

    // ── POSTULER ─────────────────────────────────────────────
    @PostMapping("/postuler/{offreId}")
    public ResponseEntity<Map<String, Object>> postuler(
            @PathVariable Long offreId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserDetails user) {

        String message = body != null
                ? body.getOrDefault("messageMotivation", "")
                : "";

        return ResponseEntity.ok(
                service.postuler(user.getUsername(), offreId, message)
        );
    }

    // ── UPLOAD CV ────────────────────────────────────────────
    @PostMapping("/upload-cv")
    public ResponseEntity<String> uploadCV(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails user) {
        try {
            Path uploadDir = Paths.get("uploads/cv");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path path = uploadDir.resolve(fileName);
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
            service.saveCvPath(user.getUsername(), fileName);

            return ResponseEntity.ok(fileName);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body("Erreur lors de l'upload du CV : " + e.getMessage());
        }
    }

    // ── TÉLÉCHARGER / AFFICHER CV ────────────────────────────
    @GetMapping("/uploads/cv/{fileName:.+}")
    public ResponseEntity<Resource> getCv(@PathVariable String fileName) {
        try {
            Path uploadDir = Paths.get("uploads/cv").toAbsolutePath().normalize();
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            Path filePath = uploadDir.resolve(fileName).normalize();

            if (!filePath.startsWith(uploadDir)) {
                throw new RuntimeException("Chemin invalide : " + fileName);
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new RuntimeException("Fichier non trouvé : " + fileName);
            }

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(404).body(null);
        }
    }

    // ── MES CANDIDATURES ─────────────────────────────────────
    @GetMapping("/mes-candidatures")
    public ResponseEntity<List<Map<String, Object>>> mes(
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(
                service.mesCandidatures(user.getUsername())
        );
    }

    // ── CANDIDATURES REÇUES ──────────────────────────────────
    @GetMapping("/recues")
    public ResponseEntity<List<Map<String, Object>>> recues(
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(
                service.candidaturesRecues(user.getUsername())
        );
    }

    // ── CHANGER STATUT ───────────────────────────────────────
    @PutMapping("/{id}/statut")
    public ResponseEntity<Map<String, Object>> changerStatut(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails user) {

        String statut = (String) body.get("statut");
        String commentaire = (String) body.getOrDefault("commentaire", "");
        String dateEntretien = (String) body.get("dateEntretien");
        String lieuEntretien = (String) body.get("lieuEntretien");

        return ResponseEntity.ok(
                service.changerStatut(
                        id,
                        statut,
                        commentaire,
                        dateEntretien,
                        lieuEntretien,
                        user.getUsername()
                )
        );
    }
}