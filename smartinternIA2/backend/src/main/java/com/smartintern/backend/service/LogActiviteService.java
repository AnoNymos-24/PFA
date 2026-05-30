package com.smartintern.backend.service;

import com.smartintern.backend.dto.LogActiviteDto;
import com.smartintern.backend.entity.LogActivite;
import com.smartintern.backend.entity.LogActivite.TypeAction;
import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.LogActiviteRepository;
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

/**
 * Service d'audit applicatif.
 *
 * Règle fondamentale : loguer() ne remonte JAMAIS d'exception à l'appelant.
 * L'audit est une dépendance souple — une défaillance de la persistance du log
 * ne doit pas annuler l'action métier en cours.
 *
 * Implémentation : TransactionTemplate (REQUIRES_NEW) au lieu de @Transactional.
 * Raison : avec @Transactional(REQUIRES_NEW) + try/catch, si Hibernate marque
 * la transaction rollback-only avant que Java lève l'exception, le proxy Spring
 * lance UnexpectedRollbackException APRÈS le retour de la méthode, hors du
 * try/catch. TransactionTemplate lance l'exception DANS execute(), elle est
 * donc captée par le try/catch externe — aucune exception n'échappe à l'appelant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogActiviteService {

    private final LogActiviteRepository      logActiviteRepository;
    private final UserRepository             userRepository;
    private final PlatformTransactionManager transactionManager;

    // ── Persistance d'un log ──────────────────────────────────────────────────

    /**
     * Enregistre une action avec contexte HTTP (IP, User-Agent).
     *
     * @param user         Utilisateur auteur de l'action
     * @param action       Type d'action (énumération TypeAction)
     * @param entiteCible  Nom de l'entité cible (ex : "sprints", "taches"), null si global
     * @param entiteId     Identifiant de l'entité cible, null si global
     * @param description  Description libre de l'action
     * @param request      Requête HTTP pour extraire IP et User-Agent (null si scheduler)
     */
    public void loguer(User user,
                       TypeAction action,
                       String entiteCible,
                       Long entiteId,
                       String description,
                       HttpServletRequest request) {
        try {
            TransactionTemplate tt = new TransactionTemplate(transactionManager);
            tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            tt.execute(status -> {
                // Re-attache l'entité User dans la nouvelle transaction
                User userRef = userRepository.getReferenceById(user.getId());

                String ip        = extractIp(request);
                String userAgent = extractUserAgent(request);

                LogActivite entree = LogActivite.builder()
                        .user(userRef)
                        .action(action)
                        .entiteCible(entiteCible)
                        .entiteId(entiteId)
                        .description(description)
                        .adresseIp(ip)
                        .appareil(userAgent)
                        .build();

                logActiviteRepository.save(entree);
                return null;
            });

        } catch (Exception e) {
            // Exception intentionnellement avalée — ne pas propager à l'appelant.
            // TransactionTemplate lance l'exception dans execute() → capturée ici.
            log.error("⚠️  Échec persistance log activité [user={}, action={}] : {}",
                    user.getId(), action, e.getMessage());
        }
    }

    /**
     * Surcharge sans contexte HTTP — pour les schedulers ou traitements batch.
     * IP et appareil seront null.
     */
    public void loguer(User user,
                       TypeAction action,
                       String entiteCible,
                       Long entiteId,
                       String description) {
        loguer(user, action, entiteCible, entiteId, description, null);
    }

    // ── Requêtes admin ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<LogActiviteDto.Response> findAll(Pageable pageable) {
        return logActiviteRepository.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<LogActiviteDto.Response> findByUserId(Long userId, Pageable pageable) {
        return logActiviteRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<LogActiviteDto.Response> findByEntite(String entiteCible,
                                                       Long entiteId,
                                                       Pageable pageable) {
        return logActiviteRepository.findByEntiteCibleAndEntiteId(entiteCible, entiteId, pageable)
                .map(this::toDto);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LogActiviteDto.Response toDto(LogActivite l) {
        User u = l.getUser();
        return new LogActiviteDto.Response(
                l.getId(),
                u != null ? u.getId()         : null,
                u != null ? u.getEmail()      : null,
                u != null ? u.getFirstName()  : null,
                u != null ? u.getLastName()   : null,
                l.getAction() != null ? l.getAction().name() : null,
                l.getEntiteCible(),
                l.getEntiteId(),
                l.getDescription(),
                l.getAdresseIp(),
                l.getAppareil(),
                l.getCreatedAt()
        );
    }

    /**
     * Extrait l'IP réelle en tenant compte des proxies (X-Forwarded-For).
     */
    private String extractIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Extrait le User-Agent tronqué à 255 caractères.
     */
    private String extractUserAgent(HttpServletRequest request) {
        if (request == null) return null;
        String ua = request.getHeader("User-Agent");
        if (ua == null) return null;
        return ua.length() > 255 ? ua.substring(0, 255) : ua;
    }
}
