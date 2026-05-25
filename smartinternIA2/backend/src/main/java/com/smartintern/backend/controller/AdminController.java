package com.smartintern.backend.controller;

import com.smartintern.backend.dto.DemandeStageDto;
import com.smartintern.backend.dto.UserDto;
import com.smartintern.backend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminController {

    private final AdminService adminService;

    // ── AD-02 : Lister tous les utilisateurs ─────────────────────────────────
    @GetMapping("/api/admin/users")
    public ResponseEntity<List<UserDto.UserResponse>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    // ── AD-02 : Lister par rôle ───────────────────────────────────────────────
    @GetMapping("/api/admin/users/role/{role}")
    public ResponseEntity<List<UserDto.UserResponse>> getUsersByRole(
            @PathVariable String role) {
        return ResponseEntity.ok(adminService.getUsersByRole(role));
    }

    // ── AD-02 : Détail d'un utilisateur ──────────────────────────────────────
    @GetMapping("/api/admin/users/{id}")
    public ResponseEntity<UserDto.UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getUserById(id));
    }

    // ── AD-01 : Attribuer / changer le rôle d'un utilisateur ─────────────────
    @PatchMapping("/api/admin/users/{id}/role")
    public ResponseEntity<UserDto.UserResponse> changerRole(
            @PathVariable Long id,
            @RequestBody UserDto.RoleRequest request) {
        return ResponseEntity.ok(adminService.changerRole(id, request.getRole()));
    }

    // ── AD-02 : Changer le statut du compte (activer, suspendre, etc.) ────────
    @PatchMapping("/api/admin/users/{id}/statut")
    public ResponseEntity<UserDto.UserResponse> changerStatut(
            @PathVariable Long id,
            @RequestBody UserDto.StatutRequest request) {
        return ResponseEntity.ok(adminService.changerStatut(id, request.getStatut()));
    }

    // ── AD-05 : Générer une demande de stage pour un étudiant ────────────────
    @PostMapping("/api/admin/demandes-stage")
    public ResponseEntity<DemandeStageDto.DemandeStageResponse> creerDemandeStage(
            @RequestBody DemandeStageDto.DemandeStageRequest request) {
        return ResponseEntity.ok(adminService.creerDemandeStage(request));
    }

    // ── AD-05 : Lister toutes les demandes de stage ───────────────────────────
    @GetMapping("/api/admin/demandes-stage")
    public ResponseEntity<List<DemandeStageDto.DemandeStageResponse>> getAllDemandesStage() {
        return ResponseEntity.ok(adminService.getAllDemandesStage());
    }
}
