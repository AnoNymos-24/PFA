package com.smartintern.backend.service;

import com.smartintern.backend.dto.DocumentDto;
import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service de génération et de vérification des documents.
 *
 * Flux de génération :
 *  1. L'utilisateur sélectionne un type de document (typeDocumentId).
 *  2. Le service trouve automatiquement le modèle Word actif pour ce type.
 *  3. Les données du profil (Etudiant, Etablissement) sont auto-extraites.
 *  4. Un UUID de document est pré-généré par Spring Boot.
 *  5. L'URL d'authentification publique est construite avec cet UUID.
 *  6. Le microservice Python est appelé : il remplit le template, génère le QR
 *     et retourne le fichier (DOCX/PDF).
 *  7. Le résultat est persisté en base (Document + DocumentGenere).
 *
 * Contraintes :
 *  - Un étudiant ne peut générer qu'une seule fois chaque type de document.
 *  - L'administrateur peut générer sans limitation.
 *  - Les documents expirés sont désactivés automatiquement (tâche planifiée).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    @Value("${cv.service.url:http://localhost:8000}")
    private String cvServiceUrl;

    @Value("${app.base.url:http://localhost:8081}")
    private String appBaseUrl;

    private final DocumentRepository          documentRepository;
    private final DocumentGenereRepository    documentGenereRepository;
    private final ModeleDocumentRepository    modeleDocumentRepository;
    private final UserRepository              userRepository;
    private final TypeDocumentRepository      typeDocumentRepository;
    private final RestTemplate                restTemplate;

    // ══════════════════════════════════════════════════════════════════════
    // GÉNÉRATION
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Génère un document pour l'utilisateur authentifié (étudiant) ou pour
     * un utilisateur cible désigné par l'admin.
     *
     * @param req         requête contenant typeDocumentId, userId (admin), données supplémentaires
     * @param emailUser   email de l'utilisateur qui fait la demande (extrait du JWT)
     * @return            réponse avec UUID, URLs, dates, statut
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public DocumentDto.DocumentGenerationResponse genererDocument(
            DocumentDto.DocumentGenerationRequest req,
            String emailUser) {

        // ── 1. Utilisateur demandeur ─────────────────────────────────────────
        User demandeur = userRepository.findByEmail(emailUser)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé : " + emailUser));

        boolean isAdmin = demandeur.getRole() == User.Role.ADMIN;

        // ── 2. Utilisateur cible (pour qui le document est généré) ───────────
        User utilisateur;
        if (isAdmin && req.getUserId() != null) {
            utilisateur = userRepository.findById(req.getUserId())
                    .orElseThrow(() -> new RuntimeException(
                            "Utilisateur cible non trouvé : " + req.getUserId()));
        } else {
            utilisateur = demandeur;
        }

        // ── 3. Type de document ──────────────────────────────────────────────
        TypeDocument typeDoc = typeDocumentRepository.findById(req.getTypeDocumentId())
                .orElseThrow(() -> new RuntimeException(
                        "Type de document non trouvé : " + req.getTypeDocumentId()));

        // ── 4. Unicité étudiant (un seul document par type) ──────────────────
        if (!isAdmin) {
            boolean dejaGenere = documentRepository.existsDocumentValideByUserIdAndTypeDocument(
                    utilisateur.getId(), typeDoc.getCode(), Document.Statut.VALIDE);
            if (dejaGenere) {
                throw new RuntimeException(
                        "Vous avez déjà généré un document de type « " + typeDoc.getNom() + " ». "
                        + "Un étudiant ne peut générer ce document qu'une seule fois.");
            }
        }

        // ── 5. Trouver le modèle actif ───────────────────────────────────────
        ModeleDocument modele = modeleDocumentRepository
                .findFirstByTypeDocumentIdAndStatutOrderByIdDesc(
                        req.getTypeDocumentId(), ModeleDocument.Statut.ACTIF)
                .orElseThrow(() -> new RuntimeException(
                        "Aucun modèle actif trouvé pour le type « " + typeDoc.getNom() + " ». "
                        + "Veuillez en créer un depuis l'interface d'administration."));

        if (modele.isExpire()) {
            throw new RuntimeException(
                    "Le modèle de document est expiré. "
                    + "Veuillez contacter l'administrateur.");
        }

        if (modele.getIdMicroservice() == null) {
            throw new RuntimeException(
                    "Le modèle n'est pas encore synchronisé avec le microservice de génération. "
                    + "Veuillez recréer le modèle depuis l'interface d'administration.");
        }

        // ── 6. Pré-générer UUID + URL d'authentification ─────────────────────
        String docUuid  = UUID.randomUUID().toString();
        String authUrl  = appBaseUrl + "/api/documents/" + docUuid + "/authentifier";

        // ── 7. Construire les données de remplissage ─────────────────────────
        Map<String, Object> donnees = construireDonnees(utilisateur, typeDoc, modele, req.getDonneesSupplementaires());

        // ── 8. Appeler le microservice Python ────────────────────────────────
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("id_modele_document", modele.getIdMicroservice());
        requestBody.put("data",               donnees);
        requestBody.put("qr_data",            authUrl);
        requestBody.put("output_format",      req.getOutputFormat() != null ? req.getOutputFormat() : "docx");
        requestBody.put("filename",           "document_" + docUuid.substring(0, 8));
        requestBody.put("doc_id",             docUuid);

        Map<String, Object> resultat = appellerMicroservice("/documents/generer", requestBody);

        // ── 9. Extraire les données de la réponse ─────────────────────────────
        // La réponse microservice : { success, document: { document_id, filename, format, size_bytes, download_url } }
        Map<String, Object> docData = resultat.containsKey("document")
                ? (Map<String, Object>) resultat.get("document")
                : resultat;

        String returnedDocId    = (String) docData.getOrDefault("document_id", docUuid);
        String downloadPath     = (String) docData.getOrDefault("download_url", "");
        String format           = (String) docData.getOrDefault("format", "docx");
        Object tailleObj        = docData.get("size_bytes");
        Long   taille           = tailleObj instanceof Number ? ((Number) tailleObj).longValue() : null;
        String downloadUrl      = cvServiceUrl + downloadPath;

        // ── 10. Persister en base ─────────────────────────────────────────────
        LocalDateTime now        = LocalDateTime.now();
        LocalDateTime expiredAt  = now.plusDays(modele.getDureeValiditeJours());

        Document document = documentRepository
                .findByUserIdAndTypeDocument(utilisateur.getId(), typeDoc.getCode())
                .stream()
                .filter(d -> d.getModeleDocument().getId().equals(modele.getId()))
                .findFirst()
                .orElseGet(() -> Document.builder()
                        .modeleDocument(modele)
                        .user(utilisateur)
                        .typeDocument(typeDoc.getCode())
                        .docUuid(returnedDocId)
                        .urlFichier(downloadUrl)
                        .statut(Document.Statut.VALIDE)
                        .build());

        document.setDocUuid(returnedDocId);
        document.setUrlFichier(downloadUrl);
        document = documentRepository.save(document);

        String nomEtablissement = extraireNomEtablissement(utilisateur);

        DocumentGenere dg = DocumentGenere.builder()
                .document(document)
                .docUuid(returnedDocId)
                .urlFichier(downloadUrl)
                .urlVerification(authUrl)
                .cheminFichier(downloadPath)
                .signature("")           // Pas de HMAC — le lien d'auth est le mécanisme de sécurité
                .expiredAt(expiredAt)
                .statut(DocumentGenere.Statut.VALIDE)
                .tailleOctets(taille)
                .nomEtablissement(nomEtablissement)
                .build();

        documentGenereRepository.save(dg);

        log.info("Document généré : uuid={} | type={} | user={} | expire={}",
                returnedDocId, typeDoc.getCode(), utilisateur.getEmail(),
                expiredAt.toLocalDate());

        return DocumentDto.DocumentGenerationResponse.builder()
                .success(true)
                .docUuid(returnedDocId)
                .urlTelechargement(downloadUrl)
                .urlAuthentification(authUrl)
                .typeDocument(typeDoc.getCode())
                .format(format)
                .statut("VALIDE")
                .dateCreation(now)
                .dateExpiration(expiredAt)
                .tailleOctets(taille)
                .message("Document généré avec succès")
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // PAGE D'AUTHENTIFICATION PUBLIQUE
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Retourne les données de la page d'authentification publique.
     * Appelé depuis l'endpoint scanné par le QR code.
     *
     * @param docUuid UUID du document
     * @return données d'authentification (identité, établissement, statut)
     */
    @Transactional
    public DocumentDto.DocumentAuthResponse getPageAuthentification(String docUuid) {

        DocumentGenere dg = documentGenereRepository.findByDocUuid(docUuid)
                .orElseThrow(() -> new RuntimeException(
                        "Document non trouvé : " + docUuid));

        Document doc  = dg.getDocument();
        User utilisateur = doc.getUser();

        // Vérifier et mettre à jour le statut d'expiration en temps réel
        boolean expire = dg.isExpire();
        if (expire && dg.getStatut() == DocumentGenere.Statut.VALIDE) {
            dg.setStatut(DocumentGenere.Statut.EXPIRE);
            dg.getDocument().setStatut(Document.Statut.EXPIRE);
            documentGenereRepository.save(dg);
            documentRepository.save(dg.getDocument());
            log.info("Document marqué expiré à l'accès : uuid={}", docUuid);
        }

        boolean valide = !expire && dg.getStatut() == DocumentGenere.Statut.VALIDE;

        // Informations du détenteur
        String matricule        = "";
        String nomEtablissement = dg.getNomEtablissement() != null ? dg.getNomEtablissement() : "";
        String logoEtablissement = "";

        if (utilisateur instanceof Etudiant etudiant) {
            matricule = etudiant.getCodeEtudiant() != null ? etudiant.getCodeEtudiant() : "";
            if (etudiant.getEtablissement() != null) {
                nomEtablissement  = etudiant.getEtablissement().getNom();
                logoEtablissement = etudiant.getEtablissement().getLogoUrl() != null
                        ? etudiant.getEtablissement().getLogoUrl() : "";
            }
        }

        // Informations du type de document
        TypeDocument typeDoc = doc.getModeleDocument() != null
                && doc.getModeleDocument().getTypeDocument() != null
                ? doc.getModeleDocument().getTypeDocument() : null;

        return DocumentDto.DocumentAuthResponse.builder()
                .docUuid(docUuid)
                .typeDocument(doc.getTypeDocument())
                .nomTypeDocument(typeDoc != null ? typeDoc.getNom() : doc.getTypeDocument())
                .dateCreation(dg.getCreatedAt())
                .dateExpiration(dg.getExpiredAt())
                .numeroVersion(dg.getNumeroVersion())
                .matricule(matricule)
                .nomComplet(utilisateur.getFirstName() + " " + utilisateur.getLastName())
                .email(utilisateur.getEmail())
                .nomEtablissement(nomEtablissement)
                .logoEtablissement(logoEtablissement)
                .statut(dg.getStatut().name())
                .valide(valide)
                .expire(expire)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // TÉLÉCHARGEMENT
    // ══════════════════════════════════════════════════════════════════════

    public byte[] telechargerDocument(String docUuid) {

        DocumentGenere dg = documentGenereRepository.findByDocUuid(docUuid)
                .orElseThrow(() -> new RuntimeException("Document non trouvé : " + docUuid));

        if (!dg.isValide()) {
            throw new RuntimeException("Document non valide (statut : " + dg.getStatut() + ")");
        }

        // Récupérer depuis le microservice
        try {
            String filename = docUuid + "." + (dg.getUrlFichier() != null && dg.getUrlFichier().endsWith(".pdf") ? "pdf" : "docx");
            String url = cvServiceUrl + "/documents/telecharger/" + filename;
            ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.warn("Microservice téléchargement indisponible : {}", e.getMessage());
        }

        // Fallback : chemin fichier local
        if (dg.getCheminFichier() != null) {
            try {
                return java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(dg.getCheminFichier()));
            } catch (Exception e) {
                log.error("Impossible de lire le fichier local : {}", dg.getCheminFichier());
            }
        }

        throw new RuntimeException("Fichier document introuvable : " + docUuid);
    }

    // ══════════════════════════════════════════════════════════════════════
    // HISTORIQUE
    // ══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<DocumentGenere> getHistoriqueUtilisateur(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé : " + email));
        return documentGenereRepository.findHistoriqueByUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public List<DocumentGenere> getDocumentsParModele(Long modeleId) {
        List<Document> documents = documentRepository.findByModeleDocumentId(modeleId);
        List<DocumentGenere> result = new ArrayList<>();
        for (Document doc : documents) {
            result.addAll(documentGenereRepository.findByDocumentId(doc.getId()));
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════
    // CONSTRUCTION DES DONNÉES (auto-remplissage profil)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Construit la map de remplissage des champs [champ] du template Word.
     *
     * Hiérarchie (priorité croissante) :
     *  1. Données communes (nom, prénom, dates)
     *  2. Données spécifiques à l'étudiant (matricule, filière, établissement)
     *  3. Données supplémentaires fournies par le caller (overrides)
     */
    private Map<String, Object> construireDonnees(
            User utilisateur,
            TypeDocument typeDoc,
            ModeleDocument modele,
            Map<String, Object> extras) {

        Map<String, Object> d = new HashMap<>();
        LocalDate today = LocalDate.now();

        // ── Données communes ─────────────────────────────────────────────────
        d.put("nom",             utilisateur.getLastName());
        d.put("prenom",          utilisateur.getFirstName());
        d.put("nom_complet",     utilisateur.getFirstName() + " " + utilisateur.getLastName());
        d.put("email",           utilisateur.getEmail());
        d.put("date_creation",   today.toString());
        d.put("date_du_jour",    today.toString());
        d.put("date_generation", today.toString());

        // ── Données type de document ─────────────────────────────────────────
        d.put("type_document",   typeDoc.getNom());
        d.put("titre_document",  typeDoc.getNom());
        d.put("code_type",       typeDoc.getCode());

        // ── Données spécifiques étudiant ─────────────────────────────────────
        if (utilisateur instanceof Etudiant etudiant) {
            d.put("matricule",          nvl(etudiant.getCodeEtudiant()));
            d.put("code_etudiant",      nvl(etudiant.getCodeEtudiant()));
            d.put("numero_matricule",   nvl(etudiant.getCodeEtudiant()));
            d.put("filiere",            nvl(etudiant.getFiliere()));
            d.put("classe",             nvl(etudiant.getClasse()));
            d.put("cin",                nvl(etudiant.getCin()));

            if (etudiant.getEtablissement() != null) {
                d.put("nom_etablissement",    etudiant.getEtablissement().getNom());
                d.put("etablissement",         etudiant.getEtablissement().getNom());
                d.put("adresse_etablissement", nvl(etudiant.getEtablissement().getAdresse()));
                d.put("logo_etablissement",    nvl(etudiant.getEtablissement().getLogoUrl()));
            }
        }

        // ── Date d'expiration du document ────────────────────────────────────
        LocalDate expirationDoc = today.plusDays(modele.getDureeValiditeJours());
        d.put("date_expiration", expirationDoc.toString());

        // ── Données supplémentaires (override) ───────────────────────────────
        if (extras != null && !extras.isEmpty()) {
            d.putAll(extras);
        }

        return d;
    }

    // ══════════════════════════════════════════════════════════════════════
    // UTILITAIRES
    // ══════════════════════════════════════════════════════════════════════

    private String extraireNomEtablissement(User utilisateur) {
        if (utilisateur instanceof Etudiant etudiant
                && etudiant.getEtablissement() != null) {
            return etudiant.getEtablissement().getNom();
        }
        return "SmartIntern";
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> appellerMicroservice(String path, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    cvServiceUrl + path, request, Map.class);

            if (response.getBody() == null) {
                throw new RuntimeException("Réponse vide du microservice");
            }

            Boolean success = (Boolean) response.getBody().get("success");
            if (Boolean.FALSE.equals(success)) {
                throw new RuntimeException("Microservice : "
                        + response.getBody().getOrDefault("detail", "erreur inconnue"));
            }

            return response.getBody();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur appel microservice {} : {}", path, e.getMessage());
            throw new RuntimeException("Microservice de génération indisponible : " + e.getMessage());
        }
    }
}
