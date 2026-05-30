package com.smartintern.backend.controller;

import com.smartintern.backend.dto.messaging.*;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.service.MessagingService;
import com.smartintern.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/messaging")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class MessagingController {

    private final MessagingService messagingService;
    private final UserRepository userRepository;

    private static final String UPLOAD_DIR = "uploads/messages/";

    // ── Helper : récupère l'utilisateur connecté via le SecurityContext ──
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé : " + email));
    }

    // ══════════════════════════════════════════════════════════════
    //  CONVERSATIONS
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/conversations")
    public ResponseEntity<ConversationDTO> creerConversation(
            @RequestBody CreateConversationRequest req) {
        User user = getCurrentUser();
        return ResponseEntity.ok(messagingService.creerConversation(req, user.getId()));
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDTO>> getMesConversations() {
        User user = getCurrentUser();
        return ResponseEntity.ok(messagingService.getMesConversations(user.getId()));
    }

    @PostMapping("/conversations/{convId}/rejoindre")
    public ResponseEntity<ConversationDTO> rejoindreConversation(
            @PathVariable Long convId) {
        User user = getCurrentUser();
        return ResponseEntity.ok(messagingService.rejoindreConversation(convId, user.getId()));
    }

    // ══════════════════════════════════════════════════════════════
    //  MESSAGES
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/conversations/{convId}/messages")
    public ResponseEntity<MessageDTO> envoyerMessage(
            @PathVariable Long convId,
            @RequestBody SendMessageRequest req) {
        User user = getCurrentUser();
        req.setConversationId(convId);
        return ResponseEntity.ok(messagingService.envoyerMessage(req, user.getId()));
    }

    @PostMapping("/conversations/{convId}/fichiers")
    public ResponseEntity<MessageDTO> envoyerFichier(
            @PathVariable Long convId,
            @RequestParam("fichier") MultipartFile fichier) throws IOException {
        User user = getCurrentUser();
        return ResponseEntity.ok(messagingService.envoyerFichier(convId, user.getId(), fichier));
    }

    @GetMapping("/conversations/{convId}/messages")
    public ResponseEntity<Page<MessageDTO>> getHistorique(
            @PathVariable Long convId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {
        User user = getCurrentUser();
        return ResponseEntity.ok(messagingService.getHistorique(convId, user.getId(), page, size));
    }

    @PutMapping("/conversations/{convId}/lus")
    public ResponseEntity<Void> marquerLus(@PathVariable Long convId) {
        User user = getCurrentUser();
        messagingService.marquerLus(convId, user.getId());
        return ResponseEntity.ok().build();
    }

    // ══════════════════════════════════════════════════════════════
    //  EXPORT HISTORIQUE
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/conversations/{convId}/export")
    public ResponseEntity<byte[]> exporterHistorique(@PathVariable Long convId) {
        User user = getCurrentUser();
        String contenu = messagingService.exporterHistorique(convId, user.getId());
        byte[] bytes = contenu.getBytes();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDisposition(
            ContentDisposition.attachment()
                .filename("historique-conversation-" + convId + ".txt")
                .build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    // ══════════════════════════════════════════════════════════════
    //  TÉLÉCHARGEMENT FICHIERS
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/fichiers/{nomFichier:.+}")
    public ResponseEntity<Resource> telechargerFichier(@PathVariable String nomFichier) {
        try {
            Path filePath = Paths.get(UPLOAD_DIR).resolve(nomFichier).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
        } catch (MalformedURLException e) {
            log.error("Fichier introuvable : {}", nomFichier);
            return ResponseEntity.badRequest().build();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  UTILISATEURS DISPONIBLES
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/users")
    public ResponseEntity<List<UserContactDTO>> getAvailableUsers() {
        User currentUser = getCurrentUser();
        List<User> users = userRepository.findAll();
        List<UserContactDTO> contacts = new ArrayList<>();
        for (User user : users) {
            if (!user.getId().equals(currentUser.getId())) {
                UserContactDTO dto = new UserContactDTO();
                dto.setId(user.getId());
                dto.setNom(user.getNom());
                dto.setPrenom(user.getPrenom());
                dto.setEmail(user.getEmail());
                dto.setRole("");
                contacts.add(dto);
            }
        }
        return ResponseEntity.ok(contacts);
    }
}