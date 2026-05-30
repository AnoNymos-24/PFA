package com.smartintern.backend.controller;

import com.smartintern.backend.dto.*;
import com.smartintern.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<Map<String,String>> register(
            @Valid @RequestBody RegisterRequest req) {
        String msg = authService.register(req);
        return ResponseEntity.ok(Map.of("message", msg));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(
            @RequestBody Map<String, String> body) {
        AuthResponse res = authService.verifyEmail(
            body.get("email"), body.get("code")
        );
        return ResponseEntity.ok(res);
    }

    @PostMapping("/resend-code")
    public ResponseEntity<Map<String,String>> resendCode(
            @RequestBody Map<String, String> body) {
        authService.resendCode(body.get("email"));
        return ResponseEntity.ok(Map.of("message", "Code renvoyé avec succès."));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}