package com.smartintern.backend.service;

import com.smartintern.backend.dto.SessionConnexionDto;
import com.smartintern.backend.entity.SessionConnexion;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.SessionConnexionRepository;
import com.smartintern.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Service de gestion des sessions de connexion.
 *
 * Règle fondamentale : ouvrirSession() et fermerSession() ne remontent JAMAIS
 * d'exception à l'appelant — une défaillance de l'audit ne doit pas bloquer
 * l'authentification ou la déconnexion.
 *
 * Implémentation : TransactionTemplate (REQUIRES_NEW) pour que les exceptions
 * du commit soient rattrapées par le try/catch externe.
 * Voir LogActiviteService pour l'explication du pattern.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionConnexionService {

    private final SessionConnexionRepository sessionConnexionRepository;
    private final UserRepository             userRepository;
    private final PlatformTransactionManager transactionManager;

    // ── Ouverture de session ──────────────────────────────────────────────────

    /**
     * Crée un enregistrement de session et met à jour derniereConnexion sur le User.
     *
     * @param user      Utilisateur qui se connecte
     * @param jwtToken  Token JWT brut (sera hashé en SHA-256 avant stockage)
     * @param request   Requête HTTP pour extraire IP et User-Agent
     */
    public void ouvrirSession(User user, String jwtToken, HttpServletRequest request) {
        try {
            TransactionTemplate tt = new TransactionTemplate(transactionManager);
            tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            tt.execute(status -> {
                String tokenHash = sha256(jwtToken);
                String ip        = extractIp(request);
                String ua        = extractUserAgent(request);

                SessionConnexion session = SessionConnexion.builder()
                        .user(userRepository.getReferenceById(user.getId()))
                        .tokenHash(tokenHash)
                        .dateConnexion(LocalDateTime.now())
                        .adresseIp(ip)
                        .appareil(ua)
                        .statut(SessionConnexion.Statut.ACTIVE)
                        .build();

                sessionConnexionRepository.save(session);

                // Mise à jour atomique de derniereConnexion — évite le merge complet
                // sur JOINED inheritance (ADMIN n'a pas de sous-entité JPA).
                userRepository.updateDerniereConnexion(user.getId(), LocalDateTime.now());
                return null;
            });

        } catch (Exception e) {
            log.error("⚠️  Échec ouverture session [user={}] : {}", user.getId(), e.getMessage());
            // Exception intentionnellement avalée — ne pas bloquer le login
        }
    }

    // ── Fermeture de session ──────────────────────────────────────────────────

    /**
     * Clôture une session active identifiée par le hash du JWT.
     *
     * @param jwtToken  Token JWT brut (même valeur que lors de l'ouverture)
     * @param motif     Statut de fermeture (FERMEE_MANUELLEMENT, EXPIREE, FERMEE_AUTO)
     */
    public void fermerSession(String jwtToken, SessionConnexion.Statut motif) {
        try {
            TransactionTemplate tt = new TransactionTemplate(transactionManager);
            tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            tt.execute(status -> {
                String tokenHash = sha256(jwtToken);
                sessionConnexionRepository.findByTokenHash(tokenHash).ifPresent(session -> {
                    LocalDateTime maintenant = LocalDateTime.now();
                    long duree = ChronoUnit.SECONDS.between(session.getDateConnexion(), maintenant);
                    session.setDateDeconnexion(maintenant);
                    session.setDureeSecondes(duree);
                    session.setStatut(motif);
                    sessionConnexionRepository.save(session);
                });
                return null;
            });

        } catch (Exception e) {
            log.error("⚠️  Échec fermeture session : {}", e.getMessage());
            // Exception intentionnellement avalée — ne pas bloquer le logout
        }
    }

    // ── Requêtes utilisateur ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SessionConnexion> sessionsActivesDuUser(Long userId) {
        return sessionConnexionRepository.findByUserIdAndStatut(userId, SessionConnexion.Statut.ACTIVE);
    }

    // ── Requêtes admin ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<SessionConnexionDto.Response> findAll(Pageable pageable) {
        return sessionConnexionRepository.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<SessionConnexionDto.Response> findByUserId(Long userId, Pageable pageable) {
        return sessionConnexionRepository.findByUserIdOrderByDateConnexionDesc(userId, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<SessionConnexionDto.Response> findActives() {
        return sessionConnexionRepository.findByStatut(SessionConnexion.Statut.ACTIVE)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SessionConnexionDto.Response toDto(SessionConnexion s) {
        User u = s.getUser();
        return new SessionConnexionDto.Response(
                s.getId(),
                u != null ? u.getId()        : null,
                u != null ? u.getEmail()     : null,
                u != null ? u.getFirstName() : null,
                u != null ? u.getLastName()  : null,
                s.getDateConnexion(),
                s.getDateDeconnexion(),
                s.getDureeSecondes(),
                s.getAdresseIp(),
                s.getAppareil(),
                s.getStatut() != null ? s.getStatut().name() : null
        );
    }

    /**
     * Hash SHA-256 du token JWT — jamais le token brut stocké en base.
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 indisponible sur cette JVM", e);
        }
    }

    private String extractIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractUserAgent(HttpServletRequest request) {
        if (request == null) return null;
        String ua = request.getHeader("User-Agent");
        if (ua == null) return null;
        return ua.length() > 255 ? ua.substring(0, 255) : ua;
    }
}
