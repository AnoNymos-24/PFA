package com.smartintern.backend.service;

import com.smartintern.backend.entity.Document;
import com.smartintern.backend.entity.DocumentGenere;
import com.smartintern.backend.repository.DocumentGenereRepository;
import com.smartintern.backend.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Tâche planifiée de désactivation automatique des documents expirés.
 *
 * Exécutée toutes les heures.
 * Pour chaque DocumentGenere dont {@code expiredAt < now} et statut = VALIDE :
 *   1. Passe le statut à EXPIRE.
 *   2. Passe également le Document parent à EXPIRE si tous ses DocumentGenere sont expirés.
 *
 * Cela garantit que :
 *   - L'URL d'authentification du document devient inaccessible après expiration.
 *   - La génération de nouveaux documents pour des étudiants possédant un document
 *     expiré peut leur être à nouveau autorisée (selon la politique choisie).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentExpirationTask {

    private final DocumentGenereRepository documentGenereRepository;
    private final DocumentRepository       documentRepository;

    /**
     * Exécutée toutes les heures (cron : minute 0 de chaque heure).
     * Marque les documents échus comme EXPIRE.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expireDocumentsEchus() {

        List<DocumentGenere> expires = documentGenereRepository.findExpiredDocuments();

        if (expires.isEmpty()) {
            log.debug("Vérification expiration : aucun document à expirer.");
            return;
        }

        int count = 0;
        for (DocumentGenere dg : expires) {
            dg.setStatut(DocumentGenere.Statut.EXPIRE);
            documentGenereRepository.save(dg);

            // Marquer aussi le Document parent si nécessaire
            Document doc = dg.getDocument();
            if (doc != null && doc.getStatut() == Document.Statut.VALIDE) {
                boolean tousExpires = doc.getDocumentsGeneres() != null
                        && doc.getDocumentsGeneres().stream()
                               .allMatch(g -> g.getStatut() != DocumentGenere.Statut.VALIDE
                                           || g.isExpire());
                if (tousExpires) {
                    doc.setStatut(Document.Statut.EXPIRE);
                    documentRepository.save(doc);
                }
            }
            count++;
        }

        log.info("Tâche expiration : {} document(s) passé(s) au statut EXPIRE.", count);
    }
}
