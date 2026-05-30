package com.smartintern.backend.controller;

import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.UserRepository;
import com.smartintern.backend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/cv")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CvController {

    private final FileStorageService fileStorage;
    private final UserRepository     userRepo;

    // ── UPLOAD ────────────────────────────────────────────────
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepo.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable."));

        try {
            // Supprimer l'ancien CV si existe
            if (user.getCvFileName() != null) {
                fileStorage.deleteCv(user.getCvFileName());
            }

            // Sauvegarder le nouveau
            String fileName = fileStorage.saveCv(file, user.getId());

            // Mettre à jour l'utilisateur
            user.setCvFileName(file.getOriginalFilename());
            user.setCvFilePath(fileName);
            user.setCvUploadedAt(LocalDateTime.now());
            userRepo.save(user);

            return ResponseEntity.ok(Map.of(
                "message",   "CV téléversé avec succès.",
                "fileName",  file.getOriginalFilename(),
                "uploadedAt", LocalDateTime.now().toString()
            ));

        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de l'upload : " + e.getMessage());
        }
    }

    // ── INFOS DU CV ───────────────────────────────────────────
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepo.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable."));

        if (user.getCvFileName() == null) {
            return ResponseEntity.ok(Map.of("hasCv", false));
        }

        return ResponseEntity.ok(Map.of(
            "hasCv",      true,
            "fileName",   user.getCvFileName(),
            "uploadedAt", user.getCvUploadedAt() != null
                          ? user.getCvUploadedAt().toString() : ""
        ));
    }

    // ── TÉLÉCHARGER ───────────────────────────────────────────
    @GetMapping("/download")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepo.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable."));

        if (user.getCvFilePath() == null)
            throw new RuntimeException("Aucun CV trouvé.");

        try {
            Path path = fileStorage.getCvPath(user.getCvFilePath());
            Resource resource = new UrlResource(path.toUri());

            if (!resource.exists())
                throw new RuntimeException("Fichier introuvable.");

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + user.getCvFileName() + "\"")
                .body(resource);

        } catch (Exception e) {
            throw new RuntimeException("Erreur téléchargement : " + e.getMessage());
        }
    }

    // ── SUPPRIMER ─────────────────────────────────────────────
    @DeleteMapping
    public ResponseEntity<Map<String, String>> delete(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepo.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable."));

        if (user.getCvFilePath() != null) {
            fileStorage.deleteCv(user.getCvFilePath());
            user.setCvFileName(null);
            user.setCvFilePath(null);
            user.setCvUploadedAt(null);
            userRepo.save(user);
        }

        return ResponseEntity.ok(Map.of("message", "CV supprimé."));
    }
}