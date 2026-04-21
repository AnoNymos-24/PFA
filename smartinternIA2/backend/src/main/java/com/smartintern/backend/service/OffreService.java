package com.smartintern.backend.service;

import com.smartintern.backend.dto.OffreDto;
import com.smartintern.backend.entity.Offre;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.OffreRepository;
import com.smartintern.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OffreService {

    private final OffreRepository offreRepository;
    private final UserRepository userRepository;

    public OffreDto.OffreResponse createOffre(OffreDto.OffreRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé"));

        Offre offre = Offre.builder()
                .titre(request.getTitre())
                .description(request.getDescription())
                .entreprise(user.getFirstName())
                .ville(request.getVille())
                .domaine(request.getDomaine())
                .dureeMois(request.getDureeMois())
                .dateLimite(request.getDateLimite())
                .statut(Offre.Statut.ACTIVE)
                .user(user)
                .build();

        offreRepository.save(offre);
        return toResponse(offre);
    }

    public List<OffreDto.OffreResponse> getMesOffres(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé"));
        return offreRepository.findByUserId(user.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<OffreDto.OffreResponse> getAllOffresActives() {
        return offreRepository.findByStatut(Offre.Statut.ACTIVE)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public OffreDto.OffreResponse updateOffre(Long id, OffreDto.OffreRequest request, String email) {
        Offre offre = offreRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Offre non trouvée"));

        if (!offre.getUser().getEmail().equals(email))
            throw new RuntimeException("Non autorisé");

        offre.setTitre(request.getTitre());
        offre.setDescription(request.getDescription());
        offre.setVille(request.getVille());
        offre.setDomaine(request.getDomaine());
        offre.setDureeMois(request.getDureeMois());
        offre.setDateLimite(request.getDateLimite());
        offreRepository.save(offre);
        return toResponse(offre);
    }

    public void deleteOffre(Long id, String email) {
        Offre offre = offreRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Offre non trouvée"));
        if (!offre.getUser().getEmail().equals(email))
            throw new RuntimeException("Non autorisé");
        offreRepository.delete(offre);
    }

    private OffreDto.OffreResponse toResponse(Offre o) {
        return OffreDto.OffreResponse.builder()
                .id(o.getId())
                .titre(o.getTitre())
                .description(o.getDescription())
                .entreprise(o.getEntreprise())
                .ville(o.getVille())
                .domaine(o.getDomaine())
                .dureeMois(o.getDureeMois())
                .statut(o.getStatut().name())
                .dateLimite(o.getDateLimite())
                .createdAt(o.getCreatedAt())
                .build();
    }
}