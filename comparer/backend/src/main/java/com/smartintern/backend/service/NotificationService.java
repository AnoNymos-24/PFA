package com.smartintern.backend.service;

import com.smartintern.backend.entity.Notification;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repo;

    public void creerNotification(User user, String objet, String message) {
        Notification n = new Notification();
        n.setUser(user);
        n.setObjet(objet);
        n.setMessage(message);
        n.setLu(false);
        n.setDate(LocalDateTime.now());

        repo.save(n);
    }
}