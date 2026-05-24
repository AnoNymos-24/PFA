package com.smartintern.backend.controller;

import com.smartintern.backend.dto.DocumentDto;
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

/**
 * Contrôleur REST — Gestion des modèles et génération de documents.
 *
 * Endpoints admin (modèles) :
 *   POST   /api/admin/modeles                   — Créer un modèle (.docx upload)
 *   GET    /api/admin/modeles                   — Lister les modèles
 *   GET    /api/admin/modeles/{id}              — Détail d'un modèle
 *   PATCH  /api/admin/modeles/{id}              — Modifier un modèle
 *   DELETE /api/admin/modeles/{id}              — Archiver un modèle
 *   GET    /api/admin/types-documents           — Types disponibles
 *   POST   /api/admin/types-documents           — Créer un type
 *   GET    /api/admin/modeles/{id}/documents    — Documents générés depuis un modèle
 *   POST   /api/admin/documents/generer         — Générer pour n'importe quel utilisateur (sans limite)
 *
 * Endpoints étudiant :
 *   POST   /api/etudiant/documents/generer      — Générer son propre document (unicité par type)
 *   GET    /api/etudiant/documents              — Historique de ses documents
 *
 * Endpoints publics :
 *   GET    /api/documents/{uuid}/authentifier   — Page d'authentification (QR scan, sans auth)
 *   GET    /api/documents/{uuid}/telecharger    — Télécharger le fichier (authentifié)
 */
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DocumentController {

    private final ModeleDocumentService modeleDocumentService;
    private final DocumentService       documentService;

    // ════════════════════════════════════════════════════════════════════════
    // ADMIN — Modèles de documents
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
    public ResponseEntity<ModeleDocumentDto.ModeleDocumentResponse> getModele(
            @PathVariable Long id) {
        return ResponseEntity.ok(modeleDocumentService.getModele(id));
    }

    @PatchMapping("/api/admin/modeles/{id}")
    public ResponseEntity<ModeleDocumentDto.ModeleDocumentResponse> updateModele(
            @PathVariable Long id,
            @RequestBody ModeleDocumentDto.ModeleDocumentUpdateRequest request) {
        return ResponseEntity.ok(modeleDocumentService.updateModele(id, request));
    }

    @DeleteMapping("/api/admin/modeles/{id}")
    public ResponseEntity<Void> archiverModele(@PathVariable Long id) {
        modeleDocumentService.archiverModele(id);
        return ResponseEntity.noContent().build();
    }

    // ── Types de documents ────────────────────────────────────────────────

    @GetMapping("/api/admin/types-documents")
    public ResponseEntity<List<TypeDocument>> getAllTypesDocuments() {
        return ResponseEntity.ok(modeleDocumentService.getAllTypesDocuments());
    }

    @PostMapping("/api/admin/types-documents")
    public ResponseEntity<TypeDocument> creerTypeDocument(
            @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(modeleDocumentService.creerTypeDocument(
                        body.get("nom"), body.get("code"), body.get("description")));
    }

    @GetMapping("/api/admin/modeles/{id}/documents")
    public ResponseEntity<List<DocumentGenere>> getDocumentsParModele(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocumentsParModele(id));
    }

    // ════════════════════════════════════════════════════════════════════════
    // ADMIN — Génération de documents (sans contrainte d'unicité)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * L'admin génère un document pour un utilisateur donné.
     *
     * Corps JSON :
     * {
     *   "typeDocumentId": 1,
     *   "userId": 42,                           // obligatoire pour admin
     *   "donneesSupplementaires": { "sujet": "Stage en développement" },
     *   "outputFormat": "docx"
     * }
     */
    @PostMapping("/api/admin/documents/generer")
    public ResponseEntity<DocumentDto.DocumentGenerationResponse> genererDocumentAdmin(
            @RequestBody @Valid DocumentDto.DocumentGenerationRequest request,
            Authentication authentication) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.genererDocument(request, authentication.getName()));
    }

    // ════════════════════════════════════════════════════════════════════════
    // ÉTUDIANT — Génération de son propre document (unicité par type)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Un étudiant génère son propre document.
     * - Les données sont auto-remplies depuis le profil (nom, prénom, matricule, établissement…).
     * - Un même type de document ne peut être généré qu'une seule fois par étudiant.
     *
     * Corps JSON :
     * {
     *   "typeDocumentId": 1,
     *   "donneesSupplementaires": { "date_debut": "2025-06-01" },
     *   "outputFormat": "docx"
     * }
     */
    @PostMapping("/api/etudiant/documents/generer")
    public ResponseEntity<DocumentDto.DocumentGenerationResponse> genererDocumentEtudiant(
            @RequestBody @Valid DocumentDto.DocumentGenerationRequest request,
            Authentication authentication) {

        // Forcer userId = null (l'étudiant génère pour lui-même)
        request.setUserId(null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.genererDocument(request, authentication.getName()));
    }

    @GetMapping("/api/etudiant/documents")
    public ResponseEntity<List<DocumentGenere>> getMesDocuments(Authentication authentication) {
        return ResponseEntity.ok(documentService.getHistoriqueUtilisateur(authentication.getName()));
    }

    // ════════════════════════════════════════════════════════════════════════
    // PUBLIC — Authentification du document via QR code
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Page d'authentification publique — scannée depuis le QR code.
     *
     * Retourne les informations du document pour vérification :
     *   - Identifiant du document (UUID)
     *   - Type de document
     *   - Dates de création et d'expiration
     *   - Numéro matricule du détenteur
     *   - Nom et prénom du détenteur
     *   - Nom et logo de l'établissement
     *   - Statut (VALIDE / EXPIRE / REVOQUE)
     *
     * Si le document est expiré, son statut est automatiquement mis à jour
     * et la réponse indique expire=true avec statut EXPIRE.
     */
    @GetMapping("/api/documents/{uuid}/authentifier")
    public ResponseEntity<DocumentDto.DocumentAuthResponse> authentifierDocument(
            @PathVariable String uuid) {

        DocumentDto.DocumentAuthResponse response = documentService.getPageAuthentification(uuid);

        // Retourner 410 Gone si le document est expiré
        if (response.isExpire()) {
            return ResponseEntity.status(HttpStatus.GONE).body(response);
        }

        // Retourner 404 si révoqué
        if ("REVOQUE".equals(response.getStatut())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        return ResponseEntity.ok(response);
    }

    // ════════════════════════════════════════════════════════════════════════
    // TÉLÉCHARGEMENT (utilisateur authentifié)
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping("/api/documents/{uuid}/telecharger")
    public ResponseEntity<byte[]> telechargerDocument(@PathVariable String uuid) {

        byte[] contenu = documentService.telechargerDocument(uuid);

        // Déterminer le type MIME selon l'extension connue
        boolean isPdf = uuid.endsWith(".pdf") || contenu.length > 4
                && contenu[0] == '%' && contenu[1] == 'P' && contenu[2] == 'D' && contenu[3] == 'F';

        MediaType mediaType = isPdf
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        String extension = isPdf ? "pdf" : "docx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"document_" + uuid + "." + extension + "\"")
                .contentType(mediaType)
                .body(contenu);
    }
}
