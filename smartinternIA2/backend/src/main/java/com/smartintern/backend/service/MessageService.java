package com.smartintern.backend.service;

import com.smartintern.backend.dto.messagerie.MessageDto;
import com.smartintern.backend.dto.messagerie.MessageSendRequest;
import com.smartintern.backend.entity.*;
import com.smartintern.backend.entity.Conversation.StatutConversation;
import com.smartintern.backend.entity.Message.TypeMessage;
import com.smartintern.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * U-07 — Service d'envoi et de lecture de messages.
 *
 * <h3>Gardes obligatoires avant tout envoi</h3>
 * <ol>
 *   <li>{@code participant.estActif = true} pour l'expéditeur</li>
 *   <li>{@code conversation.statut = ACTIVE}</li>
 *   <li>Si PRIVEE : stage lié doit être EN_COURS → sinon passage en LECTURE_SEULE + 403</li>
 *   <li>{@code contenu.length <= 2000} caractères</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int PAGE_SIZE_MAX = 25;

    private final MessageRepository                 messageRepository;
    private final ConversationRepository            conversationRepository;
    private final ParticipantConversationRepository participantRepository;
    private final ConversationService               conversationService;
    private final NotificationService               notificationService;
    private final SimpMessagingTemplate             messagingTemplate;

    // ════════════════════════════════════════════════════════════════════════
    // ENVOI D'UN MESSAGE
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Envoie un message dans une conversation.
     * Broadcasté via WebSocket ET sauvegardé en BDD.
     * Appelé depuis REST ({@code MessagerieController}) ET WebSocket ({@code MessagerieWsController}).
     */
    @Transactional
    public MessageDto envoyerMessage(User expediteur, MessageSendRequest request) {
        // 1. Charger la conversation
        Conversation conv = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new RuntimeException(
                        "Conversation introuvable : " + request.getConversationId()));

        // 2. Vérifier l'accès (participant de la conversation)
        conversationService.verifierAcces(expediteur, conv.getId());

        // 3. Vérifier que la conversation est ACTIVE
        if (conv.getStatut() != StatutConversation.ACTIVE) {
            throw new AccessDeniedException(
                    "La conversation est en " + conv.getStatut() + " — envoi interdit");
        }

        // 3b. Si PRIVEE : vérifier que le stage lié est toujours EN_COURS
        if (conv.getType() == Conversation.TypeConversation.PRIVEE
                && conv.getStage() != null
                && conv.getStage().getStatut() != Stage.Statut.EN_COURS) {
            conv.setStatut(StatutConversation.LECTURE_SEULE);
            conversationRepository.save(conv);
            throw new AccessDeniedException(
                    "Le stage lié est terminé — la conversation passe en lecture seule");
        }

        // 4. Vérifier que l'expéditeur est actif dans la conversation
        ParticipantConversation participant =
                participantRepository.findByConversationAndUtilisateur(conv, expediteur)
                        .orElseThrow(() -> new AccessDeniedException(
                                "Vous n'êtes pas participant de cette conversation"));
        if (!participant.isEstActif()) {
            throw new AccessDeniedException("Votre participation à cette conversation est inactive");
        }

        // 5. Valider le contenu
        String contenu = request.getContenu();
        if (contenu == null || contenu.isBlank()) {
            throw new IllegalArgumentException("Le contenu du message ne peut pas être vide");
        }
        if (contenu.length() > 2000) {
            throw new IllegalArgumentException("Le message dépasse 2000 caractères");
        }

        // 6. Persister le message
        Message msg = messageRepository.save(
                Message.builder()
                        .conversation(conv)
                        .expediteur(expediteur)
                        .contenu(contenu)
                        .envoieLe(LocalDateTime.now())
                        .lu(false)
                        .type(TypeMessage.TEXTE)
                        .build());

        // 7. Mettre à jour dernierMessageLe sur la conversation
        conv.setDernierMessageLe(msg.getEnvoieLe());
        conversationRepository.save(conv);

        // 8. Broadcaster via WebSocket STOMP
        MessageDto dto = toDto(msg);
        messagingTemplate.convertAndSend(
                "/topic/conversation." + conv.getId(), dto);

        // 9. Notifier chaque participant actif (sauf l'expéditeur)
        participantRepository.findByConversationAndEstActifTrue(conv).stream()
                .filter(p -> !p.getUtilisateur().getId().equals(expediteur.getId()))
                .forEach(p -> {
                    try {
                        notificationService.creer(
                                p.getUtilisateur(),
                                "Nouveau message de " + expediteur.getFirstName(),
                                "MESSAGERIE");
                    } catch (Exception e) {
                        log.warn("Erreur notification messagerie pour user={} : {}",
                                p.getUtilisateur().getId(), e.getMessage());
                    }
                });

        log.debug("Message envoyé — conv={} expediteur={} longueur={}",
                conv.getId(), expediteur.getEmail(), contenu.length());
        return dto;
    }

    // ════════════════════════════════════════════════════════════════════════
    // HISTORIQUE PAGINÉ
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Retourne l'historique paginé (anti-chronologique) et marque les messages lus.
     */
    @Transactional
    public Page<MessageDto> getHistorique(User user, Long conversationId, int page, int size) {
        conversationService.verifierAcces(user, conversationId);
        Conversation conv = conversationRepository.findById(conversationId).orElseThrow();

        int safeSize = Math.min(size, PAGE_SIZE_MAX);
        Page<Message> pageMessages = messageRepository.findByConversationOrderByEnvoieLeDesc(
                conv, PageRequest.of(page, safeSize));

        // Marquer les messages non-lus (de l'autre côté) comme lus
        messageRepository.marquerToutLu(conv, user);

        return pageMessages.map(this::toDto);
    }

    // ════════════════════════════════════════════════════════════════════════
    // MARQUER TOUT LU
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Marque tous les messages non lus d'une conversation comme lus pour l'utilisateur.
     * Retourne le compteur résiduel (toujours 0 après l'opération).
     */
    @Transactional
    public Map<String, Long> marquerToutLu(User user, Long conversationId) {
        conversationService.verifierAcces(user, conversationId);
        Conversation conv = conversationRepository.findById(conversationId).orElseThrow();
        messageRepository.marquerToutLu(conv, user);
        return Map.of("nonLusRestants", 0L);
    }

    // ════════════════════════════════════════════════════════════════════════
    // EXPORT HISTORIQUE
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Exporte l'historique d'une conversation sous forme de texte brut.
     * Retourne une chaîne formatée prête à être envoyée en pièce jointe .txt.
     */
    @Transactional(readOnly = true)
    public String exporterHistorique(User user, Long conversationId) {
        conversationService.verifierAcces(user, conversationId);
        Conversation conv = conversationRepository.findById(conversationId).orElseThrow();
        List<Message> messages = messageRepository.findByConversationOrderByEnvoieLeAsc(conv);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        StringBuilder sb = new StringBuilder();
        sb.append("=== Historique — conversation #").append(conversationId).append(" ===\n\n");

        for (Message m : messages) {
            if (m.getType() == TypeMessage.SYSTEME) {
                sb.append("[SYSTÈME] ").append(m.getContenu()).append("\n");
            } else if (m.getType() == TypeMessage.FICHIER || m.getType() == TypeMessage.IMAGE) {
                sb.append(String.format("[%s] %s %s : 📎 %s\n",
                        m.getEnvoieLe().format(fmt),
                        m.getExpediteur().getFirstName(),
                        m.getExpediteur().getLastName(),
                        m.getFichierNom() != null ? m.getFichierNom() : m.getContenu()));
            } else {
                sb.append(String.format("[%s] %s %s : %s\n",
                        m.getEnvoieLe().format(fmt),
                        m.getExpediteur().getFirstName(),
                        m.getExpediteur().getLastName(),
                        m.getContenu()));
            }
        }
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════════════
    // ENVOI FICHIER / IMAGE
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Envoie une pièce jointe dans une conversation.
     * Le fichier est sauvegardé dans {@code uploads/messages/}.
     * Les gardes d'accès sont identiques à {@link #envoyerMessage}.
     */
    @Transactional
    public MessageDto envoyerFichier(User expediteur, Long conversationId,
                                     MultipartFile fichier) throws IOException {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation introuvable : " + conversationId));

        conversationService.verifierAcces(expediteur, conv.getId());

        if (conv.getStatut() != StatutConversation.ACTIVE) {
            throw new AccessDeniedException(
                    "La conversation est en " + conv.getStatut() + " — envoi interdit");
        }

        ParticipantConversation participant =
                participantRepository.findByConversationAndUtilisateur(conv, expediteur)
                        .orElseThrow(() -> new AccessDeniedException(
                                "Vous n'êtes pas participant de cette conversation"));
        if (!participant.isEstActif()) {
            throw new AccessDeniedException("Votre participation à cette conversation est inactive");
        }

        // Sauvegarde physique avec nom unique
        String nomUnique = UUID.randomUUID() + "_" + fichier.getOriginalFilename();
        Path uploadPath = Paths.get("uploads/messages");
        Files.createDirectories(uploadPath);
        Files.copy(fichier.getInputStream(), uploadPath.resolve(nomUnique),
                StandardCopyOption.REPLACE_EXISTING);

        String contentType = fichier.getContentType();
        TypeMessage typeMsg = (contentType != null && contentType.startsWith("image/"))
                ? TypeMessage.IMAGE : TypeMessage.FICHIER;

        Message msg = messageRepository.save(Message.builder()
                .conversation(conv)
                .expediteur(expediteur)
                .contenu(fichier.getOriginalFilename() != null
                        ? fichier.getOriginalFilename() : "fichier")
                .fichierNom(fichier.getOriginalFilename())
                .fichierPath("/api/messagerie/fichiers/" + nomUnique)
                .fichierType(contentType)
                .fichierTaille(fichier.getSize())
                .envoieLe(LocalDateTime.now())
                .lu(false)
                .type(typeMsg)
                .build());

        conv.setDernierMessageLe(msg.getEnvoieLe());
        conversationRepository.save(conv);

        MessageDto dto = toDto(msg);
        messagingTemplate.convertAndSend("/topic/conversation." + conv.getId(), dto);

        log.debug("Fichier envoyé — conv={} expediteur={} fichier={}",
                conv.getId(), expediteur.getEmail(), fichier.getOriginalFilename());
        return dto;
    }

    // ════════════════════════════════════════════════════════════════════════
    // MAPPING DTO
    // ════════════════════════════════════════════════════════════════════════

    private MessageDto toDto(Message msg) {
        User exp = msg.getExpediteur();
        return MessageDto.builder()
                .id(msg.getId())
                .conversationId(msg.getConversation().getId())
                .expediteur(MessageDto.UserSummaryDto.builder()
                        .id(exp.getId())
                        .prenom(exp.getFirstName())
                        .nom(exp.getLastName())
                        .role(exp.getRole().name())
                        .build())
                .contenu(msg.getContenu())
                .envoieLe(msg.getEnvoieLe())
                .lu(msg.isLu())
                .type(msg.getType().name())
                .fichierNom(msg.getFichierNom())
                .fichierPath(msg.getFichierPath())
                .fichierType(msg.getFichierType())
                .fichierTaille(msg.getFichierTaille())
                .build();
    }
}
