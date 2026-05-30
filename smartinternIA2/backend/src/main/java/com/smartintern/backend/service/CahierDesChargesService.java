package com.smartintern.backend.service;

import com.smartintern.backend.dto.CahierDesChargesDto;
import com.smartintern.backend.entity.CahierDesCharges;
import com.smartintern.backend.entity.LogActivite.TypeAction;
import com.smartintern.backend.entity.Stage;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.CahierDesChargesRepository;
import com.smartintern.backend.repository.StageRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Service de gestion du Cahier des charges.
 * <p>
 * Règles métier :
 * <ul>
 *   <li>Un seul CDC par stage (contrainte 1:1 applicative).</li>
 *   <li>Seul l'encadrant entreprise du stage peut uploader et valider.</li>
 *   <li>Un CDC ne peut être validé que depuis l'état BROUILLON.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CahierDesChargesService {

    private static final String UPLOAD_DIR = "uploads/cdc/";
    private static final Set<String> TYPES_ACCEPTES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final CahierDesChargesRepository cdcRepository;
    private final StageRepository            stageRepository;
    private final LogActiviteService         logActiviteService;

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Téléverse le fichier du CDC et crée l'entité en statut BROUILLON.
     *
     * @param stageId   identifiant du stage
     * @param fichier   fichier PDF/Word uploadé
     * @param titre     titre du cahier des charges
     * @param encadrant utilisateur authentifié (doit être encadrant entreprise du stage)
     * @param request   requête HTTP pour le log
     * @return          entité CahierDesCharges persistée
     */
    @Transactional
    public CahierDesCharges televerser(Long stageId,
                                       MultipartFile fichier,
                                       String titre,
                                       User encadrant,
                                       HttpServletRequest request) {

        Stage stage = trouverStage(stageId);
        verifierEncadrant(stage, encadrant);

        // Unicité : un seul CDC par stage
        if (cdcRepository.findByStageId(stageId).isPresent()) {
            throw new IllegalStateException(
                    "Un cahier des charges existe déjà pour ce stage (id=" + stageId + ")");
        }

        // Validation du type de fichier
        String contentType = fichier.getContentType();
        if (contentType == null || !TYPES_ACCEPTES.contains(contentType)) {
            throw new IllegalStateException(
                    "Format non accepté. Formats autorisés : PDF, DOC, DOCX");
        }
        if (fichier.getSize() > 10 * 1024 * 1024) {
            throw new IllegalStateException("Le fichier ne doit pas dépasser 10 Mo");
        }

        // Sauvegarde sur disque
        String urlFichier = sauvegarderFichier(fichier, stageId);

        CahierDesCharges cdc = CahierDesCharges.builder()
                .stage(stage)
                .titre(titre)
                .statut(CahierDesCharges.Statut.BROUILLON)
                .urlFichier(urlFichier)
                .build();

        CahierDesCharges saved = cdcRepository.save(cdc);

        logActiviteService.loguer(encadrant, TypeAction.UPLOAD_CDC,
                "cahiers_des_charges", saved.getId(),
                "Upload CDC : " + titre + " (stage " + stageId + ")", request);

        log.info("CDC uploadé — stage={}, cdc={}, encadrant={}", stageId, saved.getId(), encadrant.getId());
        return saved;
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Valide un CDC (BROUILLON → VALIDE).
     * Seul l'encadrant entreprise du stage parent peut valider.
     */
    @Transactional
    public CahierDesCharges valider(Long idCdc,
                                    User encadrant,
                                    HttpServletRequest request) {

        CahierDesCharges cdc = trouverCdc(idCdc);
        verifierEncadrant(cdc.getStage(), encadrant);

        if (cdc.getStatut() != CahierDesCharges.Statut.BROUILLON) {
            throw new IllegalStateException(
                    "Le CDC est déjà validé (statut=" + cdc.getStatut() + ")");
        }

        cdc.setStatut(CahierDesCharges.Statut.VALIDE);
        cdc.setDateValidation(LocalDateTime.now());
        CahierDesCharges saved = cdcRepository.save(cdc);

        logActiviteService.loguer(encadrant, TypeAction.VALIDATION_CDC,
                "cahiers_des_charges", idCdc,
                "Validation CDC (stage " + cdc.getStage().getId() + ")", request);

        log.info("CDC validé — cdc={}, encadrant={}", idCdc, encadrant.getId());
        return saved;
    }

    // ── Consultation ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CahierDesCharges obtenirParStage(Long stageId) {
        return cdcRepository.findByStageId(stageId)
                .orElseThrow(() -> new RuntimeException(
                        "Aucun cahier des charges pour le stage id=" + stageId));
    }

    @Transactional(readOnly = true)
    public CahierDesCharges obtenir(Long idCdc) {
        return trouverCdc(idCdc);
    }

    // ── Mapping DTO ───────────────────────────────────────────────────────────

    public CahierDesChargesDto.Response toDto(CahierDesCharges cdc) {
        CahierDesChargesDto.Response dto = new CahierDesChargesDto.Response();
        dto.setId(cdc.getId());
        dto.setStageId(cdc.getStage() != null ? cdc.getStage().getId() : null);
        dto.setTitre(cdc.getTitre());
        dto.setDescription(cdc.getDescription());
        dto.setObjectifs(cdc.getObjectifs());
        dto.setContraintes(cdc.getContraintes());
        dto.setLivrables(cdc.getLivrables());
        dto.setStatut(cdc.getStatut() != null ? cdc.getStatut().name() : null);
        dto.setUrlFichier(cdc.getUrlFichier());
        dto.setDateValidation(cdc.getDateValidation());
        dto.setCreatedAt(cdc.getCreatedAt());
        dto.setUpdatedAt(cdc.getUpdatedAt());
        return dto;
    }

    // ── Helpers privés ────────────────────────────────────────────────────────

    private Stage trouverStage(Long stageId) {
        return stageRepository.findById(stageId)
                .orElseThrow(() -> new RuntimeException("Stage introuvable id=" + stageId));
    }

    private CahierDesCharges trouverCdc(Long idCdc) {
        return cdcRepository.findById(idCdc)
                .orElseThrow(() -> new RuntimeException("Cahier des charges introuvable id=" + idCdc));
    }

    /**
     * Vérifie que l'utilisateur est bien l'encadrant entreprise du stage.
     */
    private void verifierEncadrant(Stage stage, User encadrant) {
        if (stage.getEncadrantEntreprise() == null
                || !stage.getEncadrantEntreprise().getId().equals(encadrant.getId())) {
            throw new RuntimeException(
                    "Accès refusé : vous n'êtes pas l'encadrant entreprise de ce stage");
        }
    }

    /**
     * Sauvegarde le fichier sur disque et retourne le chemin relatif.
     */
    private String sauvegarderFichier(MultipartFile fichier, Long stageId) {
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String ext = determinerExtension(fichier.getContentType());
            String filename = "cdc_stage" + stageId + "_" + System.currentTimeMillis() + ext;
            Path filePath = uploadPath.resolve(filename);
            Files.write(filePath, fichier.getBytes());

            return UPLOAD_DIR + filename;

        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la sauvegarde du fichier : " + e.getMessage(), e);
        }
    }

    private String determinerExtension(String contentType) {
        if (contentType == null) return ".bin";
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "application/msword" -> ".doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            default -> ".bin";
        };
    }
}
