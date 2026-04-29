package com.smartintern.backend.service;

import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    @Value("${cv.service.url:http://localhost:8000}")
    private String cvServiceUrl;

    private final DocumentRepository documentRepository;
    private final DocumentGenereRepository documentGenereRepository;
    private final ModeleDocumentRepository modeleDocumentRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;

    // ── Génération ────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> genererDocument(Long modeleId, String emailUser,
                                                Map<String, Object> donneesProfil,
                                                String nomEtablissement) {

        ModeleDocument modele = modeleDocumentRepository.findById(modeleId)
                .orElseThrow(() -> new RuntimeException("Modèle non trouvé: " + modeleId));

        if (modele.isExpire()) {
            throw new RuntimeException("Ce modèle de document est expiré");
        }

        User user = userRepository.findByEmail(emailUser)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + emailUser));

        // Construire le corps JSON pour le microservice Python
        Map<String, Object> body = new HashMap<>();
        body.put("modeleId", modele.getId().toString());
        body.put("cheminScript", modele.getCheminScript());
        body.put("cheminHeader", modele.getCheminHeader());
        body.put("cheminFooter", modele.getCheminFooter());
        body.put("typeDocument", modele.getTypeDocument() != null
                ? modele.getTypeDocument().getCode() : "document");
        body.put("donneesProfil", donneesProfil != null ? donneesProfil : new HashMap<>());
        body.put("nomEtablissement", nomEtablissement != null ? nomEtablissement : "SmartIntern");
        body.put("dureeValiditeJours", modele.getDureeValiditeJours());

        // Appel microservice Python
        Map<String, Object> resultat = appellerGeneration("/documents/generer", body);

        // Persister en base
        persistDocument(modele, user, resultat, null);

        return resultat;
    }

    @Transactional
    public Map<String, Object> regenererDocument(String docUuidOriginal, String emailUser,
                                                  Map<String, Object> donneesProfil,
                                                  String nomEtablissement) {

        DocumentGenere original = documentGenereRepository.findByDocUuid(docUuidOriginal)
                .orElseThrow(() -> new RuntimeException("Document original non trouvé: " + docUuidOriginal));

        Document doc = original.getDocument();
        ModeleDocument modele = doc.getModeleDocument();

        Map<String, Object> body = new HashMap<>();
        body.put("docUuidOriginal", docUuidOriginal);
        body.put("modeleId", modele.getId().toString());
        body.put("cheminScript", modele.getCheminScript());
        body.put("cheminHeader", modele.getCheminHeader());
        body.put("cheminFooter", modele.getCheminFooter());
        body.put("typeDocument", modele.getTypeDocument() != null
                ? modele.getTypeDocument().getCode() : "document");
        body.put("donneesProfil", donneesProfil != null ? donneesProfil : new HashMap<>());
        body.put("nomEtablissement", nomEtablissement != null ? nomEtablissement : "SmartIntern");
        body.put("dureeValiditeJours", modele.getDureeValiditeJours());

        Map<String, Object> resultat = appellerGeneration("/documents/regenerer", body);

        persistDocument(modele, doc.getUser(), resultat, docUuidOriginal);

        return resultat;
    }

    // ── Vérification ──────────────────────────────────────────────────────

    public Map<String, Object> verifierDocument(String docUuid) {
        DocumentGenere dg = documentGenereRepository.findByDocUuid(docUuid)
                .orElseThrow(() -> new RuntimeException("Document non trouvé: " + docUuid));

        // Appel microservice pour vérification HMAC
        String url = cvServiceUrl + "/documents/verifier/" + docUuid
                + "?date_generation=" + dg.getCreatedAt()
                + "&date_expiration=" + dg.getExpiredAt()
                + "&signature=" + dg.getSignature()
                + "&type_document=" + (dg.getDocument().getTypeDocument() != null
                        ? dg.getDocument().getTypeDocument() : "")
                + "&nom_etablissement=" + (dg.getNomEtablissement() != null
                        ? dg.getNomEtablissement() : "");

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getBody() != null ? response.getBody() : Map.of("statut", "ERREUR");
        } catch (Exception e) {
            log.warn("Microservice vérification indisponible: {}", e.getMessage());
            // Vérification locale si microservice inaccessible
            Map<String, Object> result = new HashMap<>();
            result.put("docUuid", docUuid);
            result.put("valide", dg.isValide());
            result.put("statut", dg.getStatut().name());
            result.put("expire", dg.isExpire());
            result.put("typeDocument", dg.getDocument().getTypeDocument());
            result.put("nomEtablissement", dg.getNomEtablissement());
            result.put("message", dg.isValide() ? "Document valide" : "Document invalide");
            return result;
        }
    }

    // ── Téléchargement ────────────────────────────────────────────────────

    public byte[] telechargerDocument(String docUuid) {
        DocumentGenere dg = documentGenereRepository.findByDocUuid(docUuid)
                .orElseThrow(() -> new RuntimeException("Document non trouvé: " + docUuid));

        if (!dg.isValide()) {
            throw new RuntimeException("Document non valide: " + dg.getStatut().name());
        }

        // Récupérer depuis le microservice Python
        try {
            String url = cvServiceUrl + "/documents/telecharger/" + docUuid;
            ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.warn("Microservice téléchargement indisponible: {}", e.getMessage());
        }

        // Fallback: lire depuis le chemin fichier local
        if (dg.getCheminFichier() != null) {
            try {
                return java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(dg.getCheminFichier()));
            } catch (Exception e) {
                log.error("Impossible de lire le fichier local: {}", dg.getCheminFichier());
            }
        }

        throw new RuntimeException("Fichier document introuvable: " + docUuid);
    }

    // ── Historique ────────────────────────────────────────────────────────

    public List<DocumentGenere> getHistoriqueUtilisateur(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + email));
        return documentGenereRepository.findHistoriqueByUserId(user.getId());
    }

    public List<DocumentGenere> getDocumentsParModele(Long modeleId) {
        return documentGenereRepository.findByDocumentId(modeleId);
    }

    // ── Persistance ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void persistDocument(ModeleDocument modele, User user,
                                  Map<String, Object> resultat, String docUuidOriginal) {

        Map<String, Object> docData = resultat.containsKey("document")
                ? (Map<String, Object>) resultat.get("document")
                : resultat;

        String docUuid = (String) docData.get("doc_uuid");
        String urlFichier = (String) docData.get("url_fichier");
        String urlVerification = (String) docData.get("url_verification");
        String cheminFichier = (String) docData.get("chemin_fichier");
        String signature = (String) docData.getOrDefault("signature", "");
        String typeDoc = modele.getTypeDocument() != null
                ? modele.getTypeDocument().getCode() : "";

        // Chercher un Document existant ou en créer un
        Document document = documentRepository
                .findByUserIdAndTypeDocument(user.getId(), typeDoc)
                .stream().filter(d -> d.getModeleDocument().getId().equals(modele.getId()))
                .findFirst()
                .orElseGet(() -> Document.builder()
                        .modeleDocument(modele)
                        .user(user)
                        .typeDocument(typeDoc)
                        .docUuid(docUuid)
                        .urlFichier(urlFichier)
                        .statut(Document.Statut.VALIDE)
                        .build());

        document = documentRepository.save(document);

        // Date d'expiration
        LocalDateTime expiredAt = null;
        try {
            String expStr = (String) docData.get("date_expiration");
            if (expStr != null) {
                expiredAt = LocalDateTime.parse(expStr.replace(" ", "T"));
            }
        } catch (Exception e) {
            expiredAt = LocalDateTime.now().plusDays(modele.getDureeValiditeJours());
        }

        Object tailleObj = docData.get("taille_octets");
        Long taille = tailleObj instanceof Number ? ((Number) tailleObj).longValue() : null;

        DocumentGenere dg = DocumentGenere.builder()
                .document(document)
                .docUuid(docUuid)
                .urlFichier(urlFichier)
                .urlVerification(urlVerification)
                .cheminFichier(cheminFichier)
                .signature(signature)
                .expiredAt(expiredAt)
                .statut(DocumentGenere.Statut.VALIDE)
                .docUuidOriginal(docUuidOriginal)
                .tailleOctets(taille)
                .nomEtablissement((String) docData.get("nom_etablissement"))
                .build();

        documentGenereRepository.save(dg);
        log.info("Document persisté: uuid={} | type={}", docUuid, typeDoc);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> appellerGeneration(String path, Map<String, Object> body) {
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
                throw new RuntimeException("Microservice a retourné une erreur: "
                        + response.getBody().get("detail"));
            }

            return response.getBody();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur appel microservice {}: {}", path, e.getMessage());
            throw new RuntimeException("Microservice de génération indisponible: " + e.getMessage());
        }
    }
}
