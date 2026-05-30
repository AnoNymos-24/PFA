package com.smartintern.backend.service;

import com.smartintern.backend.dto.OffreRequest;
import com.smartintern.backend.dto.OffreResponse;
import com.smartintern.backend.entity.Offre;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.OffreRepository;
import com.smartintern.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OffreService {

    private final OffreRepository offreRepo;
    private final UserRepository  userRepo;

    // ── PUBLIER ───────────────────────────────────────────────
    public OffreResponse publier(OffreRequest req, String emailEntreprise) {
        User entreprise = userRepo.findByEmail(emailEntreprise)
                .orElseThrow(() -> new RuntimeException("Entreprise introuvable."));

        Offre offre = new Offre();
        offre.setTitre(req.getTitre());
        offre.setDescription(req.getDescription());
        offre.setType(req.getType());
        offre.setDuree(req.getDuree());
        offre.setLieu(req.getLieu());
        offre.setNiveauRequis(req.getNiveauRequis());
        offre.setSpecialite(req.getSpecialite());
        offre.setCompetences(req.getCompetences());
        offre.setDateExpiration(req.getDateExpiration());
        offre.setEntreprise(entreprise);
        offre.setStatut(Offre.StatutOffre.ACTIVE);

        return OffreResponse.from(offreRepo.save(offre));
    }

    // ── MODIFIER ──────────────────────────────────────────────
    public OffreResponse modifier(Long id, OffreRequest req, String emailEntreprise) {
        Offre offre = offreRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Offre introuvable."));

        // Vérifier que c'est bien l'entreprise propriétaire
        if (!offre.getEntreprise().getEmail().equals(emailEntreprise)) {
            throw new RuntimeException("Accès non autorisé.");
        }

        offre.setTitre(req.getTitre());
        offre.setDescription(req.getDescription());
        offre.setType(req.getType());
        offre.setDuree(req.getDuree());
        offre.setLieu(req.getLieu());
        offre.setNiveauRequis(req.getNiveauRequis());
        offre.setSpecialite(req.getSpecialite());
        offre.setCompetences(req.getCompetences());
        offre.setDateExpiration(req.getDateExpiration());

        return OffreResponse.from(offreRepo.save(offre));
    }

    // ── CLÔTURER ──────────────────────────────────────────────
    public void cloturer(Long id, String emailEntreprise) {
        Offre offre = offreRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Offre introuvable."));

        if (!offre.getEntreprise().getEmail().equals(emailEntreprise)) {
            throw new RuntimeException("Accès non autorisé.");
        }

        offre.setStatut(Offre.StatutOffre.CLOTUREE);
        offreRepo.save(offre);
    }

    // ── SUPPRIMER ─────────────────────────────────────────────
    public void supprimer(Long id, String emailEntreprise) {
        Offre offre = offreRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Offre introuvable."));

        if (!offre.getEntreprise().getEmail().equals(emailEntreprise)) {
            throw new RuntimeException("Accès non autorisé.");
        }

        offreRepo.delete(offre);
    }

    // ── LISTER TOUTES LES OFFRES ACTIVES ──────────────────────
    public List<OffreResponse> listerActives() {
        return offreRepo.findByStatutOrderByCreatedAtDesc(Offre.StatutOffre.ACTIVE)
                .stream().map(OffreResponse::from).collect(Collectors.toList());
    }

    // ── OFFRES D'UNE ENTREPRISE ───────────────────────────────
    public List<OffreResponse> listerParEntreprise(String emailEntreprise) {
        User entreprise = userRepo.findByEmail(emailEntreprise)
                .orElseThrow(() -> new RuntimeException("Entreprise introuvable."));
        return offreRepo.findByEntrepriseOrderByCreatedAtDesc(entreprise)
                .stream().map(OffreResponse::from).collect(Collectors.toList());
    }

    // ── RECHERCHE ─────────────────────────────────────────────
    public List<OffreResponse> rechercher(String q) {
        return offreRepo.search(q)
                .stream().map(OffreResponse::from).collect(Collectors.toList());
    }

    // ── FILTRER ───────────────────────────────────────────────
    public List<OffreResponse> filtrer(String lieu, String type,
                                        String duree, String niveau) {
        String l = (lieu   != null && !lieu.isBlank())   ? lieu   : null;
        String t = (type   != null && !type.isBlank())   ? type   : null;
        String d = (duree  != null && !duree.isBlank())  ? duree  : null;
        String n = (niveau != null && !niveau.isBlank()) ? niveau : null;

        return offreRepo.filter(l, t, d, n)
                .stream().map(OffreResponse::from).collect(Collectors.toList());
    }

    // ── DÉTAIL D'UNE OFFRE ────────────────────────────────────
    public OffreResponse detail(Long id) {
        return OffreResponse.from(
            offreRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Offre introuvable."))
        );
    }
}