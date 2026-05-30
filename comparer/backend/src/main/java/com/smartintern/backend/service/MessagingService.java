package com.smartintern.backend.service;

import com.smartintern.backend.dto.messaging.*;
import com.smartintern.backend.entity.Conversation;
import com.smartintern.backend.entity.Conversation.TypeConversation;
import com.smartintern.backend.entity.Message;
import com.smartintern.backend.entity.Message.TypeMessage;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.ConversationRepository;
import com.smartintern.backend.repository.MessageRepository;
import com.smartintern.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MessagingService {

    private final ConversationRepository conversationRepo;
    private final MessageRepository      messageRepo;
    private final UserRepository         userRepo;
    private final SimpMessagingTemplate  messagingTemplate;

    private static final String UPLOAD_DIR = "uploads/messages/";

    // ══════════════════════════════════════════════════════════════
    //  CONVERSATIONS
    // ══════════════════════════════════════════════════════════════

    public ConversationDTO creerConversation(CreateConversationRequest req, Long createurId) {

        User createur = getUser(createurId);

        // Récupérer les participants demandés
        Set<User> participants = new HashSet<>(
            userRepo.findAllById(req.getParticipantIds() != null ? req.getParticipantIds() : List.of())
        );
        // Le créateur est toujours participant
        participants.add(createur);

        // Si conversation PRIVE entre 2 personnes, vérifier qu'elle n'existe pas déjà
        if (req.getType() == TypeConversation.PRIVE && participants.size() == 2) {
            List<User> deux = new ArrayList<>(participants);
            Optional<Conversation> existing =
                conversationRepo.findPriveConversation(deux.get(0), deux.get(1));
            if (existing.isPresent()) {
                return toConversationDTO(existing.get(), createurId);
            }
        }

        Conversation conv = Conversation.builder()
            .nom(req.getNom())
            .description(req.getDescription())
            .type(req.getType() != null ? req.getType() : TypeConversation.GROUPE)
            .createur(createur)
            .participants(participants)
            .build();

        conv = conversationRepo.save(conv);

        // Notifier chaque participant via WebSocket
        ConversationDTO dto = toConversationDTO(conv, createurId);
        for (User p : participants) {
            messagingTemplate.convertAndSendToUser(
                p.getEmail(), "/queue/conversations", dto);
        }

        log.info("Conversation '{}' créée par {}", conv.getNom(), createur.getEmail());
        return dto;
    }

    public List<ConversationDTO> getMesConversations(Long userId) {
        User user = getUser(userId);
        return conversationRepo.findByParticipant(user)
            .stream()
            .map(c -> toConversationDTO(c, userId))
            .collect(Collectors.toList());
    }

    public ConversationDTO rejoindreConversation(Long convId, Long userId) {
        Conversation conv = getConversation(convId);
        User user = getUser(userId);

        conv.getParticipants().add(user);
        conv = conversationRepo.save(conv);

        // Message système pour informer les autres
        envoyerMessageSysteme(conv,
            user.getPrenom() + " " + user.getNom() + " a rejoint la conversation");

        return toConversationDTO(conv, userId);
    }

    // ══════════════════════════════════════════════════════════════
    //  MESSAGES
    // ══════════════════════════════════════════════════════════════

    public MessageDTO envoyerMessage(SendMessageRequest req, Long expediteurId) {
        verifierAcces(req.getConversationId(), expediteurId);

        Conversation conv = getConversation(req.getConversationId());
        User expediteur  = getUser(expediteurId);

        Message msg = Message.builder()
            .conversation(conv)
            .expediteur(expediteur)
            .contenu(req.getContenu())
            .type(req.getType() != null ? req.getType() : TypeMessage.TEXTE)
            .build();

        msg = messageRepo.save(msg);
        MessageDTO dto = toMessageDTO(msg);
        broadcastMessage(conv, dto);
        return dto;
    }

    public MessageDTO envoyerFichier(Long convId, Long expediteurId,
                                     MultipartFile fichier) throws IOException {
        verifierAcces(convId, expediteurId);

        Conversation conv      = getConversation(convId);
        User         expediteur = getUser(expediteurId);

        // Sauvegarde physique du fichier avec nom unique
        String nomUnique = UUID.randomUUID() + "_" + fichier.getOriginalFilename();
        Path uploadPath  = Paths.get(UPLOAD_DIR);
        Files.createDirectories(uploadPath);
        Files.copy(fichier.getInputStream(),
                   uploadPath.resolve(nomUnique),
                   StandardCopyOption.REPLACE_EXISTING);

        String contentType = fichier.getContentType();
        TypeMessage typeMsg = (contentType != null && contentType.startsWith("image/"))
            ? TypeMessage.IMAGE
            : TypeMessage.FICHIER;

        Message msg = Message.builder()
            .conversation(conv)
            .expediteur(expediteur)
            .contenu(fichier.getOriginalFilename())
            .fichierNom(fichier.getOriginalFilename())
            .fichierPath("/api/messaging/fichiers/" + nomUnique)
            .fichierType(contentType)
            .fichierTaille(fichier.getSize())
            .type(typeMsg)
            .build();

        msg = messageRepo.save(msg);
        MessageDTO dto = toMessageDTO(msg);
        broadcastMessage(conv, dto);
        return dto;
    }

    public Page<MessageDTO> getHistorique(Long convId, Long userId, int page, int size) {
        verifierAcces(convId, userId);
        // Marquer automatiquement comme lus à la lecture
        messageRepo.marquerTousLus(convId, userId);
        return messageRepo
            .findByConversationIdOrderBySentAtDesc(convId,
                PageRequest.of(page, size, Sort.by("sentAt").descending()))
            .map(this::toMessageDTO);
    }

    public void marquerLus(Long convId, Long userId) {
        verifierAcces(convId, userId);
        messageRepo.marquerTousLus(convId, userId);
        // Notifier les autres participants
        messagingTemplate.convertAndSend(
            "/topic/conversations/" + convId + "/lus",
            Map.of("convId", convId, "luPar", userId));
    }

    public void envoyerIndicateurTyping(Long convId, Long userId, boolean typing) {
        User user = userRepo.findById(userId).orElse(null);
        if (user != null) {
            messagingTemplate.convertAndSend(
                "/topic/conversations/" + convId + "/typing",
                Map.of(
                    "userId", userId,
                    "nom",    user.getPrenom(),
                    "typing", typing
                )
            );
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  EXPORT HISTORIQUE
    // ══════════════════════════════════════════════════════════════

    public String exporterHistorique(Long convId, Long userId) {
        verifierAcces(convId, userId);
        List<Message> messages = messageRepo.findByConversationIdOrderBySentAtAsc(convId);

        StringBuilder sb = new StringBuilder();
        sb.append("=== Historique de la conversation ===\n\n");

        for (Message m : messages) {
            if (m.getType() == TypeMessage.SYSTEME) {
                sb.append(String.format("[SYSTÈME] %s\n", m.getContenu()));
            } else if (m.getType() == TypeMessage.FICHIER || m.getType() == TypeMessage.IMAGE) {
                sb.append(String.format("[%s] %s %s : 📎 %s\n",
                    m.getSentAt(),
                    m.getExpediteur().getPrenom(),
                    m.getExpediteur().getNom(),
                    m.getFichierNom()));
            } else {
                sb.append(String.format("[%s] %s %s : %s\n",
                    m.getSentAt(),
                    m.getExpediteur().getPrenom(),
                    m.getExpediteur().getNom(),
                    m.getContenu()));
            }
        }
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════
    //  HELPERS PRIVÉS
    // ══════════════════════════════════════════════════════════════

    private void verifierAcces(Long convId, Long userId) {
        if (!conversationRepo.isParticipant(convId, userId)) {
            throw new RuntimeException("Accès refusé : vous n'êtes pas participant de cette conversation");
        }
    }

    private void broadcastMessage(Conversation conv, MessageDTO dto) {
        // Broadcast à tous les abonnés du topic de la conversation
        messagingTemplate.convertAndSend(
            "/topic/conversations/" + conv.getId(), dto);

        // Notification individuelle pour le badge non-lus
        for (User p : conv.getParticipants()) {
            messagingTemplate.convertAndSendToUser(
                p.getEmail(), "/queue/notifications", dto);
        }
    }

    private void envoyerMessageSysteme(Conversation conv, String texte) {
        Message msg = Message.builder()
            .conversation(conv)
            .expediteur(conv.getCreateur())
            .contenu(texte)
            .type(TypeMessage.SYSTEME)
            .build();
        messageRepo.save(msg);
        broadcastMessage(conv, toMessageDTO(msg));
    }

    private User getUser(Long id) {
        return userRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Utilisateur introuvable : " + id));
    }

    private Conversation getConversation(Long id) {
        return conversationRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Conversation introuvable : " + id));
    }

    // ══════════════════════════════════════════════════════════════
    //  MAPPERS  (Entité → DTO)
    // ══════════════════════════════════════════════════════════════

    private ConversationDTO toConversationDTO(Conversation c, Long userId) {
        Optional<Message> dernierMsg = messageRepo.findDernierMessage(c.getId());
        long nonLus = messageRepo.countNonLus(c.getId(), userId);

        return ConversationDTO.builder()
            .id(c.getId())
            .nom(c.getNom())
            .description(c.getDescription())
            .type(c.getType())
            .createdAt(c.getCreatedAt())
            .createur(toUserSummaryDTO(c.getCreateur()))
            .participants(c.getParticipants().stream()
                .map(this::toUserSummaryDTO)
                .collect(Collectors.toSet()))
            .dernierMessage(dernierMsg.map(this::toMessageDTO).orElse(null))
            .nonLus((int) nonLus)
            .build();
    }

    private MessageDTO toMessageDTO(Message m) {
        return MessageDTO.builder()
            .id(m.getId())
            .conversationId(m.getConversation().getId())
            .expediteur(toUserSummaryDTO(m.getExpediteur()))
            .contenu(m.getContenu())
            .fichierNom(m.getFichierNom())
            .fichierPath(m.getFichierPath())
            .fichierType(m.getFichierType())
            .fichierTaille(m.getFichierTaille())
            .type(m.getType())
            .sentAt(m.getSentAt())
            .lu(m.isLu())
            .build();
    }

    private UserSummaryDTO toUserSummaryDTO(User u) {
        if (u == null) return null;
        String role = u.getRoles().stream()
            .findFirst()
            .map(r -> r.getName().name())
            .orElse("");
        return UserSummaryDTO.builder()
            .id(u.getId())
            .nom(u.getNom())
            .prenom(u.getPrenom())
            .email(u.getEmail())
            .role(role)
            .build();
    }
}