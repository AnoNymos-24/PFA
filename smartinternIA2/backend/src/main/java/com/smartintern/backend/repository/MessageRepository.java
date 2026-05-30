package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Conversation;
import com.smartintern.backend.entity.Message;
import com.smartintern.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * U-07 — Repository pour les messages.
 */
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Historique paginé anti-chronologique — le client inverse l'affichage.
     */
    Page<Message> findByConversationOrderByEnvoieLeDesc(Conversation conv, Pageable pageable);

    /**
     * Tous les messages d'une conversation triés chronologiquement — pour l'export texte.
     */
    List<Message> findByConversationOrderByEnvoieLeAsc(Conversation conv);

    /**
     * Compteur de messages non lus dans une conversation pour un utilisateur donné.
     * On exclut les messages envoyés par l'utilisateur lui-même.
     */
    @Query("""
        SELECT COUNT(m) FROM Message m
        WHERE m.conversation = :conv
          AND m.expediteur != :user
          AND m.lu = false
        """)
    long countNonLus(@Param("conv") Conversation conv, @Param("user") User user);

    /**
     * Marque en masse comme lus tous les messages non lus d'une conversation
     * dont l'expéditeur est différent de l'utilisateur courant.
     */
    @Modifying
    @Query("""
        UPDATE Message m SET m.lu = true
        WHERE m.conversation = :conv
          AND m.expediteur != :user
          AND m.lu = false
        """)
    void marquerToutLu(@Param("conv") Conversation conv, @Param("user") User user);
}
