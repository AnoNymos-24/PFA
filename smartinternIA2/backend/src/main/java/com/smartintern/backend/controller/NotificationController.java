package com.smartintern.backend.controller;

import com.smartintern.backend.dto.NotificationDto;
import com.smartintern.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;

    // ── Mes notifications ─────────────────────────────────────────────────────
    @GetMapping("/api/notifications")
    public ResponseEntity<List<NotificationDto.NotificationResponse>> getMesNotifications(
            Authentication authentication) {
        return ResponseEntity.ok(
                notificationService.getMesNotifications(authentication.getName()));
    }

    // ── Nombre de notifications non lues ─────────────────────────────────────
    @GetMapping("/api/notifications/non-lues")
    public ResponseEntity<Map<String, Long>> getNonLues(Authentication authentication) {
        return ResponseEntity.ok(
                notificationService.getNonLues(authentication.getName()));
    }

    // ── Marquer une notification comme lue ────────────────────────────────────
    @PatchMapping("/api/notifications/{id}/lire")
    public ResponseEntity<NotificationDto.NotificationResponse> marquerCommeLue(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(
                notificationService.marquerCommeLue(id, authentication.getName()));
    }

    // ── Marquer toutes comme lues ─────────────────────────────────────────────
    @PatchMapping("/api/notifications/lire-tout")
    public ResponseEntity<Map<String, Object>> marquerToutesCommeLues(
            Authentication authentication) {
        return ResponseEntity.ok(
                notificationService.marquerToutesCommeLues(authentication.getName()));
    }
}
