package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Conversation;
import com.smartintern.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * U-06 — Repository pour les conversations.
 */
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * Toutes les conversations où l'utilisateur est participant actif,
     * triées par dernière activité décroissante.
     */
    @Query("""
        SELECT c FROM Conversation c
        JOIN c.participants p
        WHERE p.utilisateur = :user AND p.estActif = true
        ORDER BY c.dernierMessageLe DESC NULLS LAST
        """)
    List<Conversation> findByParticipant(@Param("user") User user);

    /**
     * Vérifie l'existence d'une conversation PRIVEE entre deux utilisateurs spécifiques.
     * Utilisé pour garantir l'idempotence à la création.
     */
    @Query("""
        SELECT c FROM Conversation c
        WHERE c.type = 'PRIVEE'
          AND EXISTS (
              SELECT p1 FROM ParticipantConversation p1
              WHERE p1.conversation = c AND p1.utilisateur = :u1
          )
          AND EXISTS (
              SELECT p2 FROM ParticipantConversation p2
              WHERE p2.conversation = c AND p2.utilisateur = :u2
          )
        """)
    Optional<Conversation> findPriveeEntre(@Param("u1") User u1, @Param("u2") User u2);

    /**
     * Toutes les conversations ACTIVE ayant un stage lié — pour le scheduler de synchronisation.
     */
    @Query("SELECT c FROM Conversation c WHERE c.statut = 'ACTIVE' AND c.stage IS NOT NULL")
    List<Conversation> findActivesAvecStage();
}
