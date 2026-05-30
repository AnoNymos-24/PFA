package com.smartintern.backend.controller;

import com.smartintern.backend.repository.UserRepository;
import com.smartintern.backend.service.AuthService;
import com.smartintern.backend.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/encadrants")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class EncadrantController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @PostMapping("/creer")
    public ResponseEntity<Map<String, Object>> creerEncadrantEntreprise(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails user) {
        String nom    = body.get("nom");
        String prenom = body.get("prenom");
        String email  = body.get("email");
        if (nom == null || prenom == null || email == null ||
            nom.isBlank() || prenom.isBlank() || email.isBlank()) {
            return ResponseEntity.badRequest()
                   .body(Map.of("message", "Nom, prénom et email sont obligatoires."));
        }
        return ResponseEntity.ok(
            authService.registerByAdmin(nom, prenom, email, "ENCADRANT_ENTREPRISE")
        );
    }

    @GetMapping("/entreprise")
    public ResponseEntity<List<Map<String, Object>>> getEncadrantsEntreprise(
            @AuthenticationPrincipal UserDetails user) {
        List<Map<String, Object>> result = userRepository
            .findByRoleName(Role.RoleName.ROLE_ENCADRANT_ENTREPRISE)
            .stream()
            .map(e -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id",                e.getId());
                map.put("nom",               e.getNom());
                map.put("prenom",            e.getPrenom());
                map.put("email",             e.getEmail());
                map.put("enabled",           e.isEnabled());
                map.put("mustChangePassword", e.isFirstLogin());
                return map;
            }).toList();
        return ResponseEntity.ok(result);
    }
}