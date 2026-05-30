package com.smartintern.backend.controller;

import com.smartintern.backend.service.DocumentService;
import com.smartintern.backend.service.DocumentService.DocType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService service;

    @GetMapping("/{type}/stage/{stageId}")
    public ResponseEntity<?> generer(
            @PathVariable String type,
            @PathVariable Long stageId,
            @AuthenticationPrincipal UserDetails user) {

        if (user == null) return ResponseEntity.status(401).build();

        DocType docType;
        try { docType = DocType.valueOf(type.toUpperCase()); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body("Type invalide"); }

        String role = user.getAuthorities().stream()
                .findFirst().map(a -> a.getAuthority()).orElse("");

        if (!DocumentService.canAccess(role, docType))
            return ResponseEntity.status(403).body("Accès non autorisé");

        try {
            byte[] bytes = service.generer(docType, stageId, user.getUsername());
            String filename = docType.name().toLowerCase() + ".docx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Erreur : " + e.getMessage());
        }
    }
}