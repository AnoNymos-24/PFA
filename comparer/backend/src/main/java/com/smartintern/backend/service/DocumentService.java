package com.smartintern.backend.service;

import com.smartintern.backend.entity.*;
import com.smartintern.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final StageRepository stageRepo;
    private final UserRepository  userRepo;

    private static final DateTimeFormatter FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public enum DocType {
        DEMANDE_STAGE, CONVENTION_STAGE, CAHIER_CHARGE, FICHE_EVALUATION, AUTORISATION_SOUTENANCE,ATTESTATION_STAGE
    }

   public static boolean canAccess(String role, DocType type) {
    return switch (type) {
        case DEMANDE_STAGE           -> role.equals("ROLE_ETUDIANT") || role.equals("ROLE_ADMIN");
        case CONVENTION_STAGE        -> role.equals("ROLE_ETUDIANT") || role.equals("ROLE_ENTREPRISE") || role.equals("ROLE_ADMIN");
        case CAHIER_CHARGE           -> role.equals("ROLE_ETUDIANT") || role.equals("ROLE_ENTREPRISE") || role.equals("ROLE_ADMIN");
        case FICHE_EVALUATION        -> role.equals("ROLE_ENCADRANT_ENTREPRISE") || role.equals("ROLE_ADMIN");
        case AUTORISATION_SOUTENANCE -> role.equals("ROLE_ENCADRANT_ACADEMIQUE") || role.equals("ROLE_ADMIN");
        case ATTESTATION_STAGE       -> role.equals("ROLE_ETUDIANT") || role.equals("ROLE_ENTREPRISE") || role.equals("ROLE_ADMIN");
    };
}

    public byte[] generer(DocType type, Long stageId, String emailDemandeur) throws Exception {
        Stage stage = stageRepo.findById(stageId)
                .orElseThrow(() -> new RuntimeException("Stage introuvable"));
        Map<String, String> vars = buildVars(stage);
        String templatePath = resolveTemplate(type);
        return fillTemplate(templatePath, vars);
    }

    private Map<String, String> buildVars(Stage stage) {
        Map<String, String> v = new HashMap<>();
        User etudiant   = stage.getCandidature().getEtudiant();
        Offre offre     = stage.getCandidature().getOffre();
        User entreprise = offre.getEntreprise();

        v.put("{{NOM_PRENOM_ETUDIANT}}", etudiant.getPrenom() + " " + etudiant.getNom());
        v.put("{{NOM_ETUDIANT}}",        etudiant.getNom());
        v.put("{{PRENOM_ETUDIANT}}",     etudiant.getPrenom());
        v.put("{{CIN}}",                 orEmpty(etudiant.getCarteEtudiant()));
        v.put("{{NIVEAU}}",              orEmpty(etudiant.getNiveau()));
        v.put("{{SPECIALITE}}",          orEmpty(etudiant.getSpecialite()));
        v.put("{{ANNEE_UNIV}}",          "2024-2025");
        v.put("{{EMAIL_ETUDIANT}}",      orEmpty(etudiant.getEmail()));

        String nomEnt = entreprise.getRaisonSociale() != null
                ? entreprise.getRaisonSociale() : orEmpty(entreprise.getNom());
        v.put("{{ENTREPRISE_NOM}}",     nomEnt);
        v.put("{{ENTREPRISE_ADRESSE}}", orEmpty(entreprise.getAdresse()));
        v.put("{{ENTREPRISE_TEL}}",     orEmpty(entreprise.getTelephone()));
        v.put("{{ENTREPRISE_EMAIL}}",   orEmpty(entreprise.getEmail()));

        v.put("{{STAGE_TITRE}}",  orEmpty(offre.getTitre()));
        v.put("{{STAGE_DEBUT}}", stage.getDateDebut() != null ? stage.getDateDebut().format(FR) : "");
        v.put("{{STAGE_FIN}}",   stage.getDateFin()   != null ? stage.getDateFin().format(FR)   : "");
        v.put("{{MISSION}}",     orEmpty(stage.getMissionDescription()));

        if (stage.getEncadrantEntreprise() != null) {
            User enc = stage.getEncadrantEntreprise();
            v.put("{{ENCADRANT_NOM}}",   enc.getPrenom() + " " + enc.getNom());
            v.put("{{ENCADRANT_TEL}}",   orEmpty(enc.getTelephone()));
            v.put("{{ENCADRANT_EMAIL}}", orEmpty(enc.getEmail()));
            v.put("{{ENCADRANT_TITRE}}", "Encadrant");
        } else {
            v.put("{{ENCADRANT_NOM}}", ""); v.put("{{ENCADRANT_TEL}}", "");
            v.put("{{ENCADRANT_EMAIL}}", ""); v.put("{{ENCADRANT_TITRE}}", "");
        }

        if (stage.getEncadrantAcademique() != null) {
            User enc = stage.getEncadrantAcademique();
            v.put("{{ENCADRANT_ACAD_NOM}}", enc.getPrenom() + " " + enc.getNom());
        } else {
            v.put("{{ENCADRANT_ACAD_NOM}}", "");
        }

        v.put("{{DATE_AUJOURD_HUI}}", LocalDate.now().format(FR));
        return v;
    }

   private String resolveTemplate(DocType type) {
    return switch (type) {
        case DEMANDE_STAGE           -> "/templates/demande_stage.docx";
        case CONVENTION_STAGE        -> "/templates/convention_stage.docx";
        case CAHIER_CHARGE           -> "/templates/cahier_charge.docx";
        case FICHE_EVALUATION        -> "/templates/fiche_evaluation.docx";
        case AUTORISATION_SOUTENANCE -> "/templates/autorisation_soutenance.docx";
        case ATTESTATION_STAGE       -> "/templates/attestation_stage.docx";
    };
}

    private byte[] fillTemplate(String resourcePath, Map<String, String> vars) throws Exception {
        InputStream in = getClass().getResourceAsStream(resourcePath);
        if (in == null) throw new RuntimeException("Template introuvable : " + resourcePath);

        XWPFDocument doc = new XWPFDocument(in);

        for (XWPFParagraph para : doc.getParagraphs()) {
            replaceParagraph(para, vars);
        }
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph para : cell.getParagraphs()) {
                        replaceParagraph(para, vars);
                    }
                }
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.write(out);
        doc.close();
        return out.toByteArray();
    }

    private void replaceParagraph(XWPFParagraph para, Map<String, String> vars) {
        String full = para.getText();
        if (full == null || full.isEmpty()) return;
        boolean changed = false;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            if (full.contains(e.getKey())) { full = full.replace(e.getKey(), e.getValue()); changed = true; }
        }
        if (!changed) return;
        for (int i = para.getRuns().size() - 1; i >= 0; i--) para.removeRun(i);
        para.createRun().setText(full);
    }

    private String orEmpty(String s) { return s != null ? s : ""; }
}