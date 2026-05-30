package com.smartintern.backend.controller;

import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.UserRepository;
import com.smartintern.backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UserController {

    private final AuthService    authService;
    private final UserRepository userRepo;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepo.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Introuvable"));

        Map<String, Object> result = new HashMap<>();
        result.put("id",              user.getId());
        result.put("nom",             user.getNom());
        result.put("prenom",          user.getPrenom());
        result.put("email",           user.getEmail());
        result.put("universite",      user.getUniversite()      != null ? user.getUniversite()      : "");
        result.put("carteEtudiant",   user.getCarteEtudiant()   != null ? user.getCarteEtudiant()   : "");
        result.put("niveau",          user.getNiveau()          != null ? user.getNiveau()          : "");
        result.put("specialite",      user.getSpecialite()      != null ? user.getSpecialite()      : "");
        result.put("raisonSociale",   user.getRaisonSociale()   != null ? user.getRaisonSociale()   : "");
        result.put("secteur",         user.getSecteur()         != null ? user.getSecteur()         : "");
        result.put("ville",           user.getVille()           != null ? user.getVille()           : "");
        result.put("adresse",         user.getAdresse()         != null ? user.getAdresse()         : "");
        result.put("telephone",       user.getTelephone()       != null ? user.getTelephone()       : "");
        result.put("matriculeFiscal", user.getMatriculeFiscal() != null ? user.getMatriculeFiscal() : "");

        return ResponseEntity.ok(result);
    }

    @PutMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body) {

        authService.changePassword(
            userDetails.getUsername(),
            body.get("oldPassword"),
            body.get("newPassword")
        );
        return ResponseEntity.ok(Map.of("message", "Mot de passe mis à jour."));
    }
}