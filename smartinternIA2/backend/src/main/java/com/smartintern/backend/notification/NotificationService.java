package com.smartintern.backend.notification;

import java.util.Map;

public interface NotificationService {
    void envoyer(NotificationEvent event, Map<String, Object> variables, String destinataire);
}
