package com.smartintern.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.CvStandardiseRepository;
import com.smartintern.backend.repository.EtudiantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CvStandardiseService {

    private final CvStandardiseRepository cvStandardiseRepository;
    private final EtudiantRepository etudiantRepository;
    private final ObjectMapper objectMapper;

    /**
     * Persiste les données IA extraites du CV dans les tables structurées.
     * Crée ou met à jour le CvStandardise lié à l'étudiant.
     */
    @Transactional
    public CvStandardise persisterCvExtrait(String emailEtudiant,
                                             CvExtractionService.CvResponse cvResponse,
                                             String fichierOriginal) {

        Etudiant etudiant = etudiantRepository.findByEmail(emailEtudiant)
                .orElseThrow(() -> new UsernameNotFoundException("Étudiant non trouvé: " + emailEtudiant));

        // Supprimer l'ancien CV structuré s'il existe (cascade ALL orphanRemoval)
        if (etudiant.getCvStandardise() != null) {
            cvStandardiseRepository.delete(etudiant.getCvStandardise());
            cvStandardiseRepository.flush();
        }

        CvExtractionService.CvStandardise cvDto = cvResponse.getCvStandardise();

        // Sérialiser les données brutes
        String jsonBrut = null;
        try {
            jsonBrut = objectMapper.writeValueAsString(cvDto);
        } catch (Exception e) {
            log.warn("Impossible de sérialiser le CV en JSON brut: {}", e.getMessage());
        }

        CvStandardise cv = CvStandardise.builder()
                .etudiant(etudiant)
                .fichierOriginal(fichierOriginal)
                .scoreCompletude(cvDto != null && cvDto.getProfil() != null
                        ? calculerScore(cvDto) : 0f)
                .statutExtraction(CvStandardise.StatutExtraction.EXTRAIT)
                .donneesJsonBrutes(jsonBrut)
                .niveauQualite(determinerNiveauQualite(
                        cvDto != null && cvDto.getProfil() != null ? calculerScore(cvDto) : 0f))
                .build();

        cv = cvStandardiseRepository.save(cv);

        if (cvDto == null) {
            return cv;
        }

        // ── Profil ─────────────────────────────────────────────────────────
        if (cvDto.getProfil() != null) {
            cv.setProfil(mapProfil(cvDto.getProfil(), cv));
        }

        // ── Expériences ────────────────────────────────────────────────────
        if (cvDto.getExperiences() != null) {
            List<Experience> experiences = new ArrayList<>();
            for (int i = 0; i < cvDto.getExperiences().size(); i++) {
                experiences.add(mapExperience(cvDto.getExperiences().get(i), cv, i));
            }
            cv.setExperiences(experiences);
        }

        // ── Formations ─────────────────────────────────────────────────────
        if (cvDto.getFormations() != null) {
            List<Formation> formations = new ArrayList<>();
            for (int i = 0; i < cvDto.getFormations().size(); i++) {
                formations.add(mapFormation(cvDto.getFormations().get(i), cv, i));
            }
            cv.setFormations(formations);
        }

        // ── Compétences ────────────────────────────────────────────────────
        List<Competence> competences = new ArrayList<>();
        if (cvDto.getCompetences() != null) {
            CvExtractionService.Competences comp = cvDto.getCompetences();
            ajouterCompetences(competences, comp.getTechniques(), Competence.TypeCompetence.TECHNIQUE, cv);
            ajouterCompetences(competences, comp.getSoftSkills(), Competence.TypeCompetence.SOFT_SKILL, cv);
            ajouterCompetences(competences, comp.getOutils(), Competence.TypeCompetence.OUTIL, cv);
            ajouterCompetences(competences, comp.getAutres(), Competence.TypeCompetence.AUTRE, cv);
        }
        cv.setCompetences(competences);

        // ── Langues ────────────────────────────────────────────────────────
        if (cvDto.getLangues() != null) {
            final CvStandardise cvFinal = cv;
            List<Langue> langues = cvDto.getLangues().stream()
                    .map(l -> mapLangue(l, cvFinal))
                    .toList();
            cv.setLangues(langues);
        }

        return cvStandardiseRepository.save(cv);
    }

    // ── Mapping helpers ────────────────────────────────────────────────────

    private Profil mapProfil(CvExtractionService.Profil dto, CvStandardise cv) {
        return Profil.builder()
                .cvStandardise(cv)
                .nom(dto.getNom())
                .prenom(dto.getPrenom())
                .email(dto.getEmail())
                .telephone(dto.getTelephone())
                .adresse(dto.getAdresse())
                .nationalite(dto.getNationalite())
                .dateNaissance(dto.getDateNaissance())
                .titreProfessionnel(dto.getTitreProfessionnel())
                .resume(dto.getResume())
                .build();
    }

    private Experience mapExperience(CvExtractionService.Experience dto, CvStandardise cv, int ordre) {
        return Experience.builder()
                .cvStandardise(cv)
                .poste(dto.getPoste() != null ? dto.getPoste() : "")
                .entreprise(dto.getEntreprise() != null ? dto.getEntreprise() : "")
                .periode(dto.getPeriode())
                .dateDebut(dto.getDateDebut())
                .dateFin(dto.getDateFin())
                .description(dto.getDescription())
                .lieu(dto.getLieu())
                .ordre(ordre)
                .build();
    }

    private Formation mapFormation(CvExtractionService.Formation dto, CvStandardise cv, int ordre) {
        return Formation.builder()
                .cvStandardise(cv)
                .diplome(dto.getDiplome() != null ? dto.getDiplome() : "")
                .etablissement(dto.getEtablissement() != null ? dto.getEtablissement() : "")
                .periode(dto.getPeriode())
                .dateDebut(dto.getDateDebut())
                .dateFin(dto.getDateFin())
                .specialite(dto.getSpecialite())
                .lieu(dto.getLieu())
                .ordre(ordre)
                .build();
    }

    private void ajouterCompetences(List<Competence> liste, List<String> noms,
                                     Competence.TypeCompetence type, CvStandardise cv) {
        if (noms == null) return;
        for (String nom : noms) {
            if (nom != null && !nom.isBlank()) {
                liste.add(Competence.builder()
                        .cvStandardise(cv)
                        .nom(nom.trim())
                        .type(type)
                        .build());
            }
        }
    }

    private Langue mapLangue(CvExtractionService.Langue dto, CvStandardise cv) {
        return Langue.builder()
                .cvStandardise(cv)
                .langue(dto.getLangue() != null ? dto.getLangue() : "")
                .niveau(dto.getNiveau())
                .build();
    }

    private float calculerScore(CvExtractionService.CvStandardise cv) {
        float score = 0;
        if (cv.getProfil() != null) {
            CvExtractionService.Profil p = cv.getProfil();
            if (p.getNom() != null)    score += 10;
            if (p.getPrenom() != null) score += 10;
            if (p.getEmail() != null)  score += 5;
        }
        if (cv.getFormations() != null && !cv.getFormations().isEmpty()) score += 20;
        if (cv.getExperiences() != null && !cv.getExperiences().isEmpty()) score += 20;
        if (cv.getCompetences() != null) {
            CvExtractionService.Competences c = cv.getCompetences();
            if (c.getTechniques() != null && !c.getTechniques().isEmpty()) score += 15;
            if (c.getSoftSkills() != null && !c.getSoftSkills().isEmpty()) score += 5;
            if (c.getOutils() != null && !c.getOutils().isEmpty()) score += 5;
        }
        if (cv.getLangues() != null && !cv.getLangues().isEmpty()) score += 5;
        if (cv.getCertifications() != null && !cv.getCertifications().isEmpty()) score += 5;
        return Math.min(score, 100f);
    }

    private String determinerNiveauQualite(float score) {
        if (score >= 85) return "EXCELLENT";
        if (score >= 65) return "BON";
        if (score >= 40) return "MOYEN";
        return "INSUFFISANT";
    }

    public CvStandardise getCvByEtudiantEmail(String email) {
        return cvStandardiseRepository.findByEtudiantEmail(email)
                .orElseThrow(() -> new RuntimeException("CV structuré non trouvé pour: " + email));
    }

    /**
     * Crée ou réinitialise un enregistrement CvStandardise en statut EN_COURS.
     * Appelé avant le lancement de l'extraction asynchrone pour que le frontend
     * puisse immédiatement interroger le statut.
     */
    @Transactional
    public CvStandardise initialiserEnCours(String emailEtudiant, String fichierOriginal) {
        Etudiant etudiant = etudiantRepository.findByEmail(emailEtudiant)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé: " + emailEtudiant));

        // Supprimer l'ancien si existant
        cvStandardiseRepository.findByEtudiantEmail(emailEtudiant)
                .ifPresent(cv -> {
                    cvStandardiseRepository.delete(cv);
                    cvStandardiseRepository.flush();
                });

        CvStandardise cv = CvStandardise.builder()
                .etudiant(etudiant)
                .fichierOriginal(fichierOriginal)
                .statutExtraction(CvStandardise.StatutExtraction.EN_COURS)
                .build();

        return cvStandardiseRepository.save(cv);
    }

    public CvStandardise.StatutExtraction getStatutExtraction(String email) {
        return cvStandardiseRepository.findByEtudiantEmail(email)
                .map(CvStandardise::getStatutExtraction)
                .orElse(null);
    }
}
