package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // Historique paginé d'une conversation (du plus récent au plus ancien)
    Page<Message> findByConversationIdOrderBySentAtDesc(
        Long conversationId,
        Pageable pageable
    );

    // Tous les messages d'une conversation (du plus ancien au plus récent)
    List<Message> findByConversationIdOrderBySentAtAsc(Long conversationId);

    // Compter les messages non lus pour un utilisateur dans une conversation
    @Query("""
        SELECT COUNT(m) FROM Message m
        WHERE m.conversation.id = :convId
        AND m.lu = false
        AND m.expediteur.id != :userId
    """)
    long countNonLus(
        @Param("convId") Long convId,
        @Param("userId") Long userId
    );

    // Marquer tous les messages d'une conversation comme lus
    @Modifying
    @Query("""
        UPDATE Message m SET m.lu = true
        WHERE m.conversation.id = :convId
        AND m.expediteur.id != :userId
        AND m.lu = false
    """)
    void marquerTousLus(
        @Param("convId") Long convId,
        @Param("userId") Long userId
    );

    // Récupérer le dernier message d'une conversation (pour la prévisualisation)
    @Query("""
        SELECT m FROM Message m
        WHERE m.conversation.id = :convId
        ORDER BY m.sentAt DESC
        LIMIT 1
    """)
    Optional<Message> findDernierMessage(@Param("convId") Long convId);
}