package com.smartintern.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartintern.backend.dto.ModeleDocumentDto;
import com.smartintern.backend.entity.ModeleDocument;
import com.smartintern.backend.entity.TypeDocument;
import com.smartintern.backend.repository.ModeleDocumentRepository;
import com.smartintern.backend.repository.TypeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModeleDocumentService {

    @Value("${cv.service.url:http://localhost:8000}")
    private String cvServiceUrl;

    private final ModeleDocumentRepository modeleDocumentRepository;
    private final TypeDocumentRepository typeDocumentRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // ── Création ──────────────────────────────────────────────────────────

    @Transactional
    public ModeleDocumentDto.ModeleDocumentResponse creerModele(
            ModeleDocumentDto.ModeleDocumentRequest request,
            MultipartFile fichierModele,
            MultipartFile fichierHeader,
            MultipartFile fichierFooter) {

        TypeDocument typeDocument = typeDocumentRepository.findById(request.getTypeDocumentId())
                .orElseThrow(() -> new RuntimeException(
                        "Type de document non trouvé: " + request.getTypeDocumentId()));

        // Appel microservice Python (POST /modeles/)
        // Seul le fichier .docx et l'id du type sont envoyés.
        // Le microservice analyse le document et retourne id_modele_document + champs_detectes.
        Map<String, Object> resultatPython = appellerMicroserviceCreerModele(
                typeDocument.getId(), fichierModele);

        // Récupérer l'identifiant microservice (registre Python)
        Object idMicroObj = resultatPython.get("id_modele_document");
        Long idMicroservice = idMicroObj instanceof Number
                ? ((Number) idMicroObj).longValue() : null;
        if (idMicroservice == null) {
            throw new RuntimeException(
                    "Le microservice n'a pas retourné d'identifiant de modèle (id_modele_document).");
        }

        // URL du fichier modèle côté microservice
        String urlFichierModele = (String) resultatPython.getOrDefault("url_fichier_modele", "");

        // Sérialiser les champs détectés ([champ à remplir] dans le document Word)
        String champsDynJson = null;
        Object champsObj = resultatPython.get("champs_detectes");
        if (champsObj != null) {
            try {
                champsDynJson = objectMapper.writeValueAsString(champsObj);
            } catch (Exception e) {
                log.warn("Impossible de sérialiser champs_detectes : {}", e.getMessage());
            }
        }

        // Sérialiser signatureNumerique en JSON
        String signatureJson = null;
        if (request.getSignatureNumerique() != null) {
            try {
                signatureJson = objectMapper.writeValueAsString(request.getSignatureNumerique());
            } catch (Exception e) {
                log.warn("Impossible de sérialiser la signature : {}", e.getMessage());
            }
        }

        boolean aZoneQr = Boolean.TRUE.equals(resultatPython.get("a_zone_qrcode"));

        ModeleDocument modele = ModeleDocument.builder()
                .nom(request.getNom())
                .titreDocument(typeDocument.getNom())
                .cheminModele(urlFichierModele)     // URL relative côté microservice
                .cheminHeader(null)                 // Plus géré côté microservice
                .cheminFooter(null)
                .cheminScript(aZoneQr ? "zone_qr_detectee" : null) // indicateur zone QR
                .champsDynamiques(champsDynJson)
                .analyseIa(null)
                .dureeValiditeJours(request.getDureeValiditeJours())
                .dateExpiration(request.getDateExpiration())
                .signatureNumerique(signatureJson)
                .typeDocument(typeDocument)
                .statut(ModeleDocument.Statut.ACTIF)
                .idMicroservice(idMicroservice)
                .build();

        modele = modeleDocumentRepository.save(modele);
        log.info("Modèle créé: id={} | nom={}", modele.getId(), modele.getNom());

        return toResponse(modele);
    }

    // ── Lecture ───────────────────────────────────────────────────────────

    public List<ModeleDocumentDto.ModeleDocumentResponse> getAllModeles() {
        return modeleDocumentRepository.findAll().stream()
                .map(this::toResponse).toList();
    }

    public List<ModeleDocumentDto.ModeleDocumentResponse> getModelesByStatut(String statut) {
        return modeleDocumentRepository.findByStatut(ModeleDocument.Statut.valueOf(statut))
                .stream().map(this::toResponse).toList();
    }

    public ModeleDocumentDto.ModeleDocumentResponse getModele(Long id) {
        return toResponse(modeleDocumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Modèle non trouvé: " + id)));
    }

    public ModeleDocument getModeleEntity(Long id) {
        return modeleDocumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Modèle non trouvé: " + id));
    }

    // ── Mise à jour ───────────────────────────────────────────────────────

    @Transactional
    public ModeleDocumentDto.ModeleDocumentResponse updateModele(
            Long id, ModeleDocumentDto.ModeleDocumentUpdateRequest request) {

        ModeleDocument modele = modeleDocumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Modèle non trouvé: " + id));

        if (request.getNom() != null && !request.getNom().isBlank()) {
            modele.setNom(request.getNom());
        }
        if (request.getDureeValiditeJours() > 0) {
            modele.setDureeValiditeJours(request.getDureeValiditeJours());
        }
        if (request.getDateExpiration() != null) {
            modele.setDateExpiration(request.getDateExpiration());
        }
        if (request.getSignatureNumerique() != null) {
            try {
                modele.setSignatureNumerique(
                        objectMapper.writeValueAsString(request.getSignatureNumerique()));
            } catch (Exception e) {
                log.warn("Impossible de sérialiser la signature: {}", e.getMessage());
            }
        }

        return toResponse(modeleDocumentRepository.save(modele));
    }

    // ── Archivage ─────────────────────────────────────────────────────────

    @Transactional
    public void archiverModele(Long id) {
        ModeleDocument modele = modeleDocumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Modèle non trouvé: " + id));
        modele.setStatut(ModeleDocument.Statut.ARCHIVE);
        modeleDocumentRepository.save(modele);
        log.info("Modèle archivé: id={}", id);
    }

    // ── Types de documents ────────────────────────────────────────────────

    public List<TypeDocument> getAllTypesDocuments() {
        return typeDocumentRepository.findAll();
    }

    @Transactional
    public TypeDocument creerTypeDocument(String nom, String code, String description) {
        if (typeDocumentRepository.existsByCode(code)) {
            throw new RuntimeException("Code type document déjà existant: " + code);
        }
        return typeDocumentRepository.save(TypeDocument.builder()
                .nom(nom).code(code).description(description).build());
    }

    // ── Appel microservice Python (nouveau endpoint POST /modeles/) ──────────

    /**
     * Envoie le fichier .docx au microservice et récupère l'id_modele_document
     * (identifiant dans registry.json) ainsi que les champs détectés.
     *
     * Endpoint cible : POST /modeles/
     * Form data :
     *   - fichier           : le fichier Word (.docx)
     *   - id_type_document  : Long (identifiant du type de document côté Python)
     *
     * Réponse attendue :
     * {
     *   "success": true,
     *   "id_modele_document": 3,
     *   "url_fichier_modele": "/modeles/files/template_3.docx",
     *   "id_type_document": 1,
     *   "champs_detectes": ["nom_etudiant", "date_debut", ...],
     *   "a_zone_qrcode": true,
     *   "message": "..."
     * }
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> appellerMicroserviceCreerModele(
            Long typeDocumentId,
            MultipartFile fichierModele) {

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("id_type_document", typeDocumentId);
            body.add("fichier", toHttpEntity(fichierModele, MediaType.APPLICATION_OCTET_STREAM));

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    cvServiceUrl + "/modeles/", request, Map.class);

            if (response.getBody() == null || !Boolean.TRUE.equals(response.getBody().get("success"))) {
                String detail = response.getBody() != null
                        ? String.valueOf(response.getBody().get("message")) : "réponse vide";
                throw new RuntimeException("Microservice a retourné une erreur : " + detail);
            }

            return response.getBody();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur appel microservice POST /modeles/ : {}", e.getMessage());
            throw new RuntimeException("Microservice de création de modèle indisponible : " + e.getMessage());
        }
    }

    private HttpEntity<ByteArrayResource> toHttpEntity(MultipartFile file, MediaType mediaType) {
        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            HttpHeaders h = new HttpHeaders();
            h.setContentType(mediaType);
            return new HttpEntity<>(resource, h);
        } catch (Exception e) {
            throw new RuntimeException("Impossible de lire le fichier: " + e.getMessage());
        }
    }

    // ── Mapping entité → DTO ──────────────────────────────────────────────

    public ModeleDocumentDto.ModeleDocumentResponse toResponse(ModeleDocument m) {
        ModeleDocumentDto.SignatureNumeriqueDto sigDto = null;
        if (m.getSignatureNumerique() != null) {
            try {
                sigDto = objectMapper.readValue(m.getSignatureNumerique(),
                        ModeleDocumentDto.SignatureNumeriqueDto.class);
            } catch (Exception e) {
                log.warn("Impossible de désérialiser la signature: {}", e.getMessage());
            }
        }

        int nbDocs = (m.getDocuments() != null) ? m.getDocuments().size() : 0;

        return ModeleDocumentDto.ModeleDocumentResponse.builder()
                .id(m.getId())
                .nom(m.getNom())
                .titreDocument(m.getTitreDocument())
                .cheminModele(m.getCheminModele())
                .cheminHeader(m.getCheminHeader())
                .cheminFooter(m.getCheminFooter())
                .cheminScript(m.getCheminScript())
                .champsDynamiques(m.getChampsDynamiques())
                .dureeValiditeJours(m.getDureeValiditeJours())
                .version(m.getVersion())
                .statut(m.getStatut().name())
                .dateCreation(m.getDateCreation())
                .dateModification(m.getDateModification())
                .dateExpiration(m.getDateExpiration())
                .expire(m.isExpire())
                .signatureNumerique(sigDto)
                .typeDocumentCode(m.getTypeDocument() != null ? m.getTypeDocument().getCode() : null)
                .typeDocumentNom(m.getTypeDocument() != null ? m.getTypeDocument().getNom() : null)
                .nombreDocumentsGeneres(nbDocs)
                .build();
    }
}
