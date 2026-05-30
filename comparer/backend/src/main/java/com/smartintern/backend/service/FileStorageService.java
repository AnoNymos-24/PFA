package com.smartintern.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${app.upload.cv-dir}")
    private String cvDir;

    public String saveCv(MultipartFile file, Long userId) throws IOException {
        // Vérifier que c'est bien un PDF
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new RuntimeException("Seuls les fichiers PDF sont acceptés.");
        }

        // Vérifier la taille (max 5 Mo)
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new RuntimeException("Le fichier dépasse la taille maximale de 5 Mo.");
        }

        // Créer le dossier si inexistant
        Path uploadPath = Paths.get(cvDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Générer un nom unique
        String fileName = "cv_" + userId + "_" + UUID.randomUUID() + ".pdf";
        Path filePath = uploadPath.resolve(fileName);

        // Sauvegarder
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return fileName;
    }

    public void deleteCv(String fileName) {
        try {
            Path filePath = Paths.get(cvDir).resolve(fileName);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            System.err.println("Erreur suppression fichier : " + e.getMessage());
        }
    }

    public Path getCvPath(String fileName) {
        return Paths.get(cvDir).resolve(fileName);
    }
}