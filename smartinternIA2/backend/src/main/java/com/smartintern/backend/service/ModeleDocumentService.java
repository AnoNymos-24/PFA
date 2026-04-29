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
import java.util.UUID;

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

        String modeleId = UUID.randomUUID().toString();

        // Appel microservice Python
        Map<String, Object> resultatPython = appellerMicroserviceCreerModele(
                modeleId, request.getNom(), typeDocument.getCode(),
                request.getDureeValiditeJours(), fichierModele, fichierHeader, fichierFooter);

        // Sérialiser signatureNumerique en JSON
        String signatureJson = null;
        if (request.getSignatureNumerique() != null) {
            try {
                signatureJson = objectMapper.writeValueAsString(request.getSignatureNumerique());
            } catch (Exception e) {
                log.warn("Impossible de sérialiser la signature: {}", e.getMessage());
            }
        }

        // Sérialiser les champs dynamiques
        String champsDynJson = null;
        Object champsObj = resultatPython.get("champs_dynamiques");
        if (champsObj != null) {
            try {
                champsDynJson = objectMapper.writeValueAsString(champsObj);
            } catch (Exception e) {
                log.warn("Impossible de sérialiser champsDynamiques: {}", e.getMessage());
            }
        }

        String analyseIaJson = null;
        Object analyseObj = resultatPython.get("analyse_complete");
        if (analyseObj != null) {
            try {
                analyseIaJson = objectMapper.writeValueAsString(analyseObj);
            } catch (Exception e) {
                log.warn("Impossible de sérialiser analyseIa: {}", e.getMessage());
            }
        }

        ModeleDocument modele = ModeleDocument.builder()
                .nom(request.getNom())
                .titreDocument((String) resultatPython.get("titre_document"))
                .cheminModele((String) resultatPython.get("chemin_modele"))
                .cheminHeader((String) resultatPython.get("chemin_header"))
                .cheminFooter((String) resultatPython.get("chemin_footer"))
                .cheminScript((String) resultatPython.get("chemin_script"))
                .champsDynamiques(champsDynJson)
                .analyseIa(analyseIaJson)
                .dureeValiditeJours(request.getDureeValiditeJours())
                .dateExpiration(request.getDateExpiration())
                .signatureNumerique(signatureJson)
                .typeDocument(typeDocument)
                .statut(ModeleDocument.Statut.ACTIF)
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

    // ── Appel microservice Python ─────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> appellerMicroserviceCreerModele(
            String modeleId, String nomModele, String typeDocument,
            int dureeValiditeJours,
            MultipartFile fichierModele, MultipartFile fichierHeader,
            MultipartFile fichierFooter) {

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("modele_id", modeleId);
            body.add("nom_modele", nomModele);
            body.add("type_document", typeDocument);
            body.add("duree_validite_jours", dureeValiditeJours);

            // Fichier modèle (obligatoire)
            body.add("fichier_modele", toHttpEntity(fichierModele,
                    MediaType.APPLICATION_PDF));

            // Header (optionnel)
            if (fichierHeader != null && !fichierHeader.isEmpty()) {
                body.add("fichier_header", toHttpEntity(fichierHeader, MediaType.IMAGE_PNG));
            }

            // Footer (optionnel)
            if (fichierFooter != null && !fichierFooter.isEmpty()) {
                body.add("fichier_footer", toHttpEntity(fichierFooter, MediaType.IMAGE_PNG));
            }

            HttpEntity<MultiValueMap<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    cvServiceUrl + "/modeles/creer", request, Map.class);

            if (response.getBody() == null || !(Boolean) response.getBody().get("success")) {
                throw new RuntimeException("Le microservice a retourné une erreur");
            }

            Map<String, Object> modeleData = (Map<String, Object>) response.getBody().get("modele");
            if (modeleData == null) {
                throw new RuntimeException("Réponse microservice invalide: 'modele' absent");
            }
            return modeleData;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur appel microservice /modeles/creer: {}", e.getMessage());
            throw new RuntimeException("Microservice de création de modèle indisponible: " + e.getMessage());
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
