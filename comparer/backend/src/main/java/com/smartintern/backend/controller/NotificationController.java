package com.smartintern.backend.controller;

import com.smartintern.backend.entity.Notification;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.NotificationRepository;
import com.smartintern.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationRepository notificationRepo;
    private final UserRepository userRepo;

    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications(
            @AuthenticationPrincipal UserDetails user) {

        User u = userRepo.findByEmail(user.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(
                notificationRepo.findByUserEmailOrderByDateDesc(u.getEmail())
        );
    }

    @PutMapping("/mark-all-read")
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal UserDetails user) {

        User u = userRepo.findByEmail(user.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Notification> notifs =
                notificationRepo.findByUserEmailOrderByDateDesc(u.getEmail());

        notifs.forEach(n -> n.setLu(true));
        notificationRepo.saveAll(notifs);

        return ResponseEntity.ok().build();
    }
}