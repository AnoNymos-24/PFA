package com.smartintern.backend.service;

import com.smartintern.backend.dto.NotificationDto;
import com.smartintern.backend.entity.Notification;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.NotificationRepository;
import com.smartintern.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // ── Récupérer les notifications de l'utilisateur connecté ────────────────
    public List<NotificationDto.NotificationResponse> getMesNotifications(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toResponse).toList();
    }

    // ── Marquer une notification comme lue ────────────────────────────────────
    public NotificationDto.NotificationResponse marquerCommeLue(Long id, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification non trouvée"));
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Non autorisé");
        }
        notification.setLu(true);
        notificationRepository.save(notification);
        return toResponse(notification);
    }

    // ── Marquer toutes les notifications comme lues ───────────────────────────
    public Map<String, Object> marquerToutesCommeLues(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        List<Notification> notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId());
        notifications.forEach(n -> n.setLu(true));
        notificationRepository.saveAll(notifications);
        return Map.of("message", "Toutes les notifications marquées comme lues",
                      "count", notifications.size());
    }

    // ── Nombre de notifications non lues ─────────────────────────────────────
    public Map<String, Long> getNonLues(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        long count = notificationRepository.countByUserIdAndLuFalse(user.getId());
        return Map.of("nonLues", count);
    }

    // ── Créer une notification (appelée en interne par d'autres services) ─────
    public void creer(User user, String message, String type) {
        Notification notification = Notification.builder()
                .user(user)
                .message(message)
                .type(type)
                .build();
        notificationRepository.save(notification);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────
    private NotificationDto.NotificationResponse toResponse(Notification n) {
        return NotificationDto.NotificationResponse.builder()
                .id(n.getId())
                .message(n.getMessage())
                .type(n.getType())
                .lu(n.isLu())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
