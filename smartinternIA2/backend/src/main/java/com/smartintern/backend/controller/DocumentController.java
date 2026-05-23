package com.smartintern.backend.controller;

import com.smartintern.backend.dto.ModeleDocumentDto;
import com.smartintern.backend.entity.DocumentGenere;
import com.smartintern.backend.entity.TypeDocument;
import com.smartintern.backend.service.DocumentService;
import com.smartintern.backend.service.ModeleDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DocumentController {

    private final ModeleDocumentService modeleDocumentService;
    private final DocumentService documentService;

    // ════════════════════════════════════════════════════════════════════════
    // ADMIN — Gestion des modèles de documents
    // ════════════════════════════════════════════════════════════════════════

    @PostMapping(value = "/api/admin/modeles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ModeleDocumentDto.ModeleDocumentResponse> creerModele(
            @RequestPart("request") @Valid ModeleDocumentDto.ModeleDocumentRequest request,
            @RequestPart("fichierModele") MultipartFile fichierModele,
            @RequestPart(value = "fichierHeader", required = false) MultipartFile fichierHeader,
            @RequestPart(value = "fichierFooter", required = false) MultipartFile fichierFooter) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(modeleDocumentService.creerModele(
                        request, fichierModele, fichierHeader, fichierFooter));
    }

    @GetMapping("/api/admin/modeles")
    public ResponseEntity<List<ModeleDocumentDto.ModeleDocumentResponse>> getAllModeles(
            @RequestParam(required = false) String statut) {

        if (statut != null) {
            return ResponseEntity.ok(modeleDocumentService.getModelesByStatut(statut));
        }
        return ResponseEntity.ok(modeleDocumentService.getAllModeles());
    }

    @GetMapping("/api/admin/modeles/{id}")
    public ResponseEntity<ModeleDocumentDto.ModeleDocumentResponse> getModele(@PathVariable Long id) {
        return ResponseEntity.ok(modeleDocumentService.getModele(id));
    }

    @PatchMapping("/api/admin/modeles/{id}")
    public ResponseEntity<ModeleDocumentDto.ModeleDocumentResponse> updateModele(
            @PathVariable Long id,
            @RequestBody ModeleDocumentDto.ModeleDocumentUpdateRequest request) {
        return ResponseEntity.ok(modeleDocumentService.updateModele(id, request));
    }

    @DeleteMapping("/api/admin/modeles/{id}")
    public ResponseEntity<Map<String, String>> archiverModele(@PathVariable Long id) {
        modeleDocumentService.archiverModele(id);
        return ResponseEntity.ok(Map.of("message", "Modèle archivé avec succès"));
    }

    // ── Types de documents ────────────────────────────────────────────────

    @GetMapping("/api/admin/types-documents")
    public ResponseEntity<List<TypeDocument>> getAllTypesDocuments() {
        return ResponseEntity.ok(modeleDocumentService.getAllTypesDocuments());
    }

    @PostMapping("/api/admin/types-documents")
    public ResponseEntity<TypeDocument> creerTypeDocument(
            @RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(modeleDocumentService.creerTypeDocument(
                        body.get("nom"), body.get("code"), body.get("description")));
    }

    // ════════════════════════════════════════════════════════════════════════
    // DOCUMENTS — Génération et téléchargement
    // ════════════════════════════════════════════════════════════════════════

    @PostMapping("/api/documents/generer")
    public ResponseEntity<Map<String, Object>> genererDocument(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {

        Long modeleId = Long.valueOf(body.get("modeleId").toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> donnees = (Map<String, Object>) body.getOrDefault("donneesProfil", Map.of());
        String nomEtablissement = (String) body.getOrDefault("nomEtablissement", "SmartIntern");

        return ResponseEntity.ok(documentService.genererDocument(
                modeleId, authentication.getName(), donnees, nomEtablissement));
    }

    @PostMapping("/api/documents/{uuid}/regenerer")
    public ResponseEntity<Map<String, Object>> regenererDocument(
            @PathVariable String uuid,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {

        @SuppressWarnings("unchecked")
        Map<String, Object> donnees = (Map<String, Object>) body.getOrDefault("donneesProfil", Map.of());
        String nomEtablissement = (String) body.getOrDefault("nomEtablissement", "SmartIntern");

        return ResponseEntity.ok(documentService.regenererDocument(
                uuid, authentication.getName(), donnees, nomEtablissement));
    }

    @GetMapping("/api/documents/{uuid}/telecharger")
    public ResponseEntity<byte[]> telechargerDocument(@PathVariable String uuid) {
        byte[] contenu = documentService.telechargerDocument(uuid);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"document_" + uuid + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(contenu);
    }

    @GetMapping("/api/documents/{uuid}/verifier")
    public ResponseEntity<Map<String, Object>> verifierDocument(@PathVariable String uuid) {
        return ResponseEntity.ok(documentService.verifierDocument(uuid));
    }

    // ── Historique étudiant ───────────────────────────────────────────────

    @GetMapping("/api/etudiant/documents")
    public ResponseEntity<List<DocumentGenere>> getMesDocuments(Authentication authentication) {
        return ResponseEntity.ok(documentService.getHistoriqueUtilisateur(authentication.getName()));
    }

    // ── Historique admin par modèle ───────────────────────────────────────

    @GetMapping("/api/admin/modeles/{id}/documents")
    public ResponseEntity<List<DocumentGenere>> getDocumentsParModele(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocumentsParModele(id));
    }
}
