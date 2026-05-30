package com.smartintern.backend.controller;

import com.smartintern.backend.dto.messagerie.ConversationCreateRequest;
import com.smartintern.backend.dto.messagerie.ConversationDto;
import com.smartintern.backend.dto.messagerie.MessageDto;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.UserRepository;
import com.smartintern.backend.service.ConversationService;
import com.smartintern.backend.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messagerie")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MessagerieController {

    private final ConversationService conversationService;
    private final MessageService      messageService;
    private final UserRepository      userRepository;

    // ══════════════════════════════════════════════════════════════════════════
    // CONVERSATIONS
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/conversations/privee")
    public ResponseEntity<ConversationDto> creerConversationPrivee(
            @RequestBody ConversationCreateRequest body, Authentication auth) {
        User user = getUser(auth);
        ConversationDto dto = conversationService.creerOuRecupererConversationPrivee(
                user, body.getDestinataireId(), body.getStageId());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/conversations/reunion")
    public ResponseEntity<ConversationDto> creerReunion(
            @RequestBody ConversationCreateRequest body, Authentication auth) {
        User user = getUser(auth);
        ConversationDto dto = conversationService.creerReunion(
                user, body.getEntrepriseId(), body.getSujet());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDto>> listeConversations(Authentication auth) {
        return ResponseEntity.ok(conversationService.getConversationsDuConnecte(getUser(auth)));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<ConversationDto> getConversation(
            @PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(conversationService.getConversation(id, getUser(auth)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MESSAGES
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<Page<MessageDto>> getMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication auth) {
        return ResponseEntity.ok(messageService.getHistorique(getUser(auth), id, page, size));
    }

    @PutMapping("/conversations/{id}/lire")
    public ResponseEntity<Map<String, Long>> marquerLu(
            @PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(messageService.marquerToutLu(getUser(auth), id));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EXPORT HISTORIQUE
    // GET /api/messagerie/conversations/{id}/export
    // Retourne un fichier .txt avec l'historique complet de la conversation.
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/conversations/{id}/export")
    public ResponseEntity<byte[]> exporterHistorique(
            @PathVariable Long id, Authentication auth) {
        String contenu = messageService.exporterHistorique(getUser(auth), id);
        byte[] bytes   = contenu.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDisposition(
            ContentDisposition.attachment()
                .filename("historique-conversation-" + id + ".txt")
                .build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PIÈCES JOINTES
    // POST /api/messagerie/conversations/{id}/fichiers  — envoyer un fichier
    // GET  /api/messagerie/fichiers/{nomFichier}        — télécharger un fichier
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/conversations/{id}/fichiers")
    public ResponseEntity<MessageDto> envoyerFichier(
            @PathVariable Long id,
            @RequestParam("fichier") MultipartFile fichier,
            Authentication auth) throws IOException {
        return ResponseEntity.ok(messageService.envoyerFichier(getUser(auth), id, fichier));
    }

    @GetMapping("/fichiers/{nomFichier:.+}")
    public ResponseEntity<Resource> telechargerFichier(@PathVariable String nomFichier) {
        try {
            Path filePath = Paths.get("uploads/messages").resolve(nomFichier).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) return ResponseEntity.notFound().build();

            String contentType;
            try {
                contentType = Files.probeContentType(filePath);
            } catch (IOException e) {
                contentType = null;
            }
            if (contentType == null) contentType = "application/octet-stream";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONTACTS — liste des utilisateurs disponibles pour la messagerie
    // GET /api/messagerie/utilisateurs
    // Retourne tous les utilisateurs actifs sauf l'utilisateur connecté.
    // Utilisé par Messaging.js pour construire la liste de participants.
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/utilisateurs")
    public ResponseEntity<List<Map<String, Object>>> getUtilisateursDisponibles(
            Authentication auth) {
        User courant = getUser(auth);
        List<Map<String, Object>> contacts = userRepository.findAll().stream()
                .filter(u -> u.isEnabled() && !u.getId().equals(courant.getId()))
                .map(u -> Map.<String, Object>of(
                        "id",     u.getId(),
                        "prenom", u.getFirstName(),
                        "nom",    u.getLastName(),
                        "email",  u.getEmail(),
                        "role",   u.getRole() != null ? u.getRole().name() : ""
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(contacts);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPER
    // ══════════════════════════════════════════════════════════════════════════

    private User getUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException(
                        "Utilisateur non trouvé : " + auth.getName()));
    }
}
