package com.smartintern.backend.service;

import com.smartintern.backend.dto.messagerie.ConversationDto;
import com.smartintern.backend.dto.messagerie.MessageDto;
import com.smartintern.backend.dto.messagerie.ParticipantDto;
import com.smartintern.backend.entity.*;
import com.smartintern.backend.entity.Conversation.StatutConversation;
import com.smartintern.backend.entity.Conversation.TypeConversation;
import com.smartintern.backend.entity.Message.TypeMessage;
import com.smartintern.backend.entity.User.Role;
import com.smartintern.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * U-06/U-07 — Service de gestion des conversations.
 *
 * <h3>Paires autorisées (PRIVEE)</h3>
 * <ul>
 *   <li>PAIR_1 : Étudiant ↔ EncadrantAcademique (même Stage)</li>
 *   <li>PAIR_2 : Étudiant ↔ EncadrantEntreprise (même Stage)</li>
 *   <li>PAIR_3 : Étudiant ↔ Admin (tout admin)</li>
 *   <li>PAIR_4 : EncadrantAcademique ↔ EncadrantEntreprise (même Stage)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository         conversationRepository;
    private final ParticipantConversationRepository participantRepository;
    private final MessageRepository              messageRepository;
    private final UserRepository                 userRepository;
    private final StageRepository                stageRepository;
    private final EntrepriseRepository           entrepriseRepository;

    // ════════════════════════════════════════════════════════════════════════
    // API PUBLIQUE
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Crée ou récupère une conversation PRIVEE (idempotent).
     */
    @Transactional
    public ConversationDto creerOuRecupererConversationPrivee(User initiateur,
                                                               Long destinataireId,
                                                               Long stageId) {
        // 1. Charger destinataire
        User destinataire = userRepository.findById(destinataireId)
                .orElseThrow(() -> new RuntimeException("Destinataire introuvable : " + destinataireId));

        // 2. Valider la paire de rôles
        validerPaireRoles(initiateur.getRole(), destinataire.getRole());

        // 3. Charger le stage et vérifier le statut EN_COURS
        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new RuntimeException("Stage introuvable : " + stageId));
        if (stage.getStatut() != Stage.Statut.EN_COURS) {
            throw new AccessDeniedException("Le stage n'est plus actif (statut=" + stage.getStatut() + ")");
        }

        // 4. Idempotence : vérifier si la conversation existe déjà
        Optional<Conversation> existante = conversationRepository.findPriveeEntre(initiateur, destinataire);
        if (existante.isPresent()) {
            log.debug("Conversation PRIVEE déjà existante — id={}", existante.get().getId());
            return toConversationDto(existante.get(), initiateur);
        }

        // 5. Créer la conversation
        Conversation conv = Conversation.builder()
                .type(TypeConversation.PRIVEE)
                .stage(stage)
                .statut(StatutConversation.ACTIVE)
                .creePar(initiateur)
                .creeLe(LocalDateTime.now())
                .build();
        conversationRepository.save(conv);

        // 6. Créer les 2 participants
        creerParticipant(conv, initiateur);
        creerParticipant(conv, destinataire);

        // 7. Message système de démarrage
        ajouterMessageSysteme(conv,
                initiateur.getFirstName() + " a démarré la conversation");
        conv.setDernierMessageLe(LocalDateTime.now());
        conversationRepository.save(conv);

        log.info("Conversation PRIVEE créée — id={} entre {} et {}", conv.getId(),
                initiateur.getEmail(), destinataire.getEmail());
        return toConversationDto(conv, initiateur);
    }

    /**
     * Crée une REUNION liée à une entreprise.
     */
    @Transactional
    public ConversationDto creerReunion(User initiateur, Long entrepriseId, String sujet) {
        Entreprise entreprise = entrepriseRepository.findById(entrepriseId)
                .orElseThrow(() -> new RuntimeException("Entreprise introuvable : " + entrepriseId));

        // Vérifier que l'initiateur est encadrant de cette entreprise OU étudiant actif
        verifierDroitReunion(initiateur, entreprise);

        // Collecter tous les participants liés à l'entreprise (stages EN_COURS)
        List<Stage> stagesActifs = stageRepository.findAll().stream()
                .filter(s -> s.getEntreprise().getId().equals(entrepriseId)
                          && s.getStatut() == Stage.Statut.EN_COURS)
                .toList();

        Conversation conv = Conversation.builder()
                .type(TypeConversation.REUNION)
                .entreprise(entreprise)
                .sujet(sujet)
                .statut(StatutConversation.ACTIVE)
                .creePar(initiateur)
                .creeLe(LocalDateTime.now())
                .build();
        conversationRepository.save(conv);

        // Dédupliquer les participants : initiateur + tous les acteurs des stages
        Set<Long> dejaAjoutes = new HashSet<>();

        ajouterParticipantSiNouvel(conv, initiateur, dejaAjoutes);
        for (Stage s : stagesActifs) {
            ajouterParticipantSiNouvel(conv, s.getEtudiant(), dejaAjoutes);
            if (s.getEncadrantEntreprise() != null) {
                ajouterParticipantSiNouvel(conv, s.getEncadrantEntreprise(), dejaAjoutes);
            }
        }

        ajouterMessageSysteme(conv,
                "Réunion '" + sujet + "' créée par " + initiateur.getFirstName());
        conv.setDernierMessageLe(LocalDateTime.now());
        conversationRepository.save(conv);

        log.info("Conversation REUNION créée — id={} entreprise={}", conv.getId(), entreprise.getNom());
        return toConversationDto(conv, initiateur);
    }

    /**
     * Liste des conversations du connecté, enrichies avec le compteur de non-lus.
     */
    @Transactional(readOnly = true)
    public List<ConversationDto> getConversationsDuConnecte(User user) {
        return conversationRepository.findByParticipant(user).stream()
                .map(c -> toConversationDto(c, user))
                .toList();
    }

    /**
     * Vérifie que l'utilisateur est participant actif de la conversation.
     * Lève {@link AccessDeniedException} sinon.
     */
    @Transactional(readOnly = true)
    public void verifierAcces(User user, Long conversationId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation introuvable : " + conversationId));

        boolean accesAutorise = participantRepository.existsByConversationAndUtilisateur(conv, user);
        if (!accesAutorise) {
            throw new AccessDeniedException("Accès refusé à la conversation " + conversationId);
        }
    }

    /**
     * Récupère une conversation par son ID (avec vérification d'accès).
     */
    @Transactional(readOnly = true)
    public ConversationDto getConversation(Long conversationId, User user) {
        verifierAcces(user, conversationId);
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation introuvable : " + conversationId));
        return toConversationDto(conv, user);
    }

    /**
     * Synchronise les statuts des conversations lorsque les stages associés se terminent.
     * Appelée par le scheduler toutes les 30 minutes.
     */
    @Transactional
    public void synchroniserStatutsConversations() {
        List<Conversation> actives = conversationRepository.findActivesAvecStage();
        int count = 0;
        for (Conversation conv : actives) {
            Stage s = conv.getStage();
            if (s != null && s.getStatut() != Stage.Statut.EN_COURS) {
                conv.setStatut(StatutConversation.LECTURE_SEULE);

                // Désactiver les participants dont le stage est terminé
                participantRepository.findByConversationAndEstActifTrue(conv)
                        .forEach(p -> {
                            p.setEstActif(false);
                            participantRepository.save(p);
                        });

                conversationRepository.save(conv);
                count++;
            }
        }
        if (count > 0) {
            log.info("Synchronisation messagerie — {} conversation(s) passée(s) en LECTURE_SEULE", count);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPERS INTERNES
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Valide que la paire (roleInitiateur, roleDestinataire) est autorisée.
     * Indépendant de l'ordre : la paire {A,B} == {B,A}.
     */
    private void validerPaireRoles(Role r1, Role r2) {
        Set<Role> paire = Set.of(r1, r2);
        boolean autorisee =
                paire.equals(Set.of(Role.ETUDIANT,            Role.ENCADRANT_ACADEMIQUE)) ||  // PAIR_1
                paire.equals(Set.of(Role.ETUDIANT,            Role.ENCADRANT_ENTREPRISE)) ||  // PAIR_2
                (paire.contains(Role.ETUDIANT) && paire.contains(Role.ADMIN))            ||  // PAIR_3
                paire.equals(Set.of(Role.ENCADRANT_ACADEMIQUE, Role.ENCADRANT_ENTREPRISE));   // PAIR_4

        if (!autorisee) {
            throw new AccessDeniedException(
                    "Paire de rôles non autorisée : " + r1 + " ↔ " + r2);
        }
    }

    private void verifierDroitReunion(User initiateur, Entreprise entreprise) {
        boolean autorise = switch (initiateur.getRole()) {
            case ENCADRANT_ENTREPRISE -> {
                if (initiateur instanceof EncadrantEntreprise ee) {
                    yield ee.getEntreprise() != null
                            && ee.getEntreprise().getId().equals(entreprise.getId());
                }
                yield false;
            }
            case ETUDIANT -> stageRepository.findAll().stream()
                    .anyMatch(s -> s.getEtudiant().getId().equals(initiateur.getId())
                               && s.getEntreprise().getId().equals(entreprise.getId())
                               && s.getStatut() == Stage.Statut.EN_COURS);
            default -> false;
        };

        if (!autorise) {
            throw new AccessDeniedException(
                    "Vous n'êtes pas autorisé à créer une réunion pour cette entreprise");
        }
    }

    private ParticipantConversation creerParticipant(Conversation conv, User user) {
        return participantRepository.save(
                ParticipantConversation.builder()
                        .conversation(conv)
                        .utilisateur(user)
                        .aRejoint(LocalDateTime.now())
                        .estActif(true)
                        .build());
    }

    private void ajouterParticipantSiNouvel(Conversation conv, User user, Set<Long> dejaAjoutes) {
        if (user != null && dejaAjoutes.add(user.getId())) {
            creerParticipant(conv, user);
        }
    }

    private Message ajouterMessageSysteme(Conversation conv, String texte) {
        return messageRepository.save(
                Message.builder()
                        .conversation(conv)
                        .expediteur(conv.getCreePar())
                        .contenu(texte)
                        .envoieLe(LocalDateTime.now())
                        .lu(false)
                        .type(TypeMessage.SYSTEME)
                        .build());
    }

    // ════════════════════════════════════════════════════════════════════════
    // MAPPING DTO
    // ════════════════════════════════════════════════════════════════════════

    public ConversationDto toConversationDto(Conversation conv, User contextUser) {
        List<ParticipantDto> participants = participantRepository.findByConversation(conv).stream()
                .map(p -> ParticipantDto.builder()
                        .utilisateurId(p.getUtilisateur().getId())
                        .prenom(p.getUtilisateur().getFirstName())
                        .nom(p.getUtilisateur().getLastName())
                        .role(p.getUtilisateur().getRole().name())
                        .estActif(p.isEstActif())
                        .build())
                .toList();

        long nbNonLus = contextUser != null
                ? messageRepository.countNonLus(conv, contextUser)
                : 0L;

        return ConversationDto.builder()
                .id(conv.getId())
                .type(conv.getType().name())
                .statut(conv.getStatut().name())
                .sujet(conv.getSujet())
                .participants(participants)
                .nbNonLus(nbNonLus)
                .dernierMessageLe(conv.getDernierMessageLe())
                .build();
    }
}
