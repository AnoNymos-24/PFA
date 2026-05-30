package com.smartintern.backend.controller;

import com.smartintern.backend.dto.AuthDto;
import com.smartintern.backend.entity.LogActivite.TypeAction;
import com.smartintern.backend.entity.SessionConnexion;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.security.JwtBlacklist;
import com.smartintern.backend.service.AuthService;
import com.smartintern.backend.service.LogActiviteService;
import com.smartintern.backend.service.SessionConnexionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService            authService;
    private final JwtBlacklist           jwtBlacklist;
    private final SessionConnexionService sessionConnexionService;
    private final LogActiviteService      logActiviteService;

    @PostMapping("/register")
    public ResponseEntity<AuthDto.AuthResponse> register(
            @Valid @RequestBody AuthDto.RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDto.AuthResponse> login(
            @Valid @RequestBody AuthDto.LoginRequest request,
            HttpServletRequest httpRequest) {

        AuthDto.AuthResponse response = authService.login(request);

        // Audit uniquement si le login a réussi (token valide, pas PENDING)
        if (response.getToken() != null && !response.getToken().equals("PENDING_VERIFICATION")) {
            authService.findUserByEmail(request.getEmail()).ifPresent(user -> {
                sessionConnexionService.ouvrirSession(user, response.getToken(), httpRequest);
                logActiviteService.loguer(user, TypeAction.CONNEXION, null, null,
                        "Connexion réussie", httpRequest);
            });
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            // Fermer la session en base avant de blacklister le token
            sessionConnexionService.fermerSession(token, SessionConnexion.Statut.FERMEE_MANUELLEMENT);

            // Audit de déconnexion (on n'a pas le User ici sans décodage du JWT,
            // mais on peut retrouver l'email depuis le contexte de sécurité si besoin —
            // pour l'instant on logue uniquement la fermeture de session)

            jwtBlacklist.blacklist(token);
        }
        return ResponseEntity.ok(Map.of("message", "Déconnexion réussie"));
    }
}
