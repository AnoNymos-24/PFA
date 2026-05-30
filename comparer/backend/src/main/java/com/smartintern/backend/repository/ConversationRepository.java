package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Conversation;
import com.smartintern.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    // Toutes les conversations d'un utilisateur (où il est participant)
    @Query("""
        SELECT DISTINCT c FROM Conversation c
        JOIN c.participants p
        WHERE p = :user
        ORDER BY c.createdAt DESC
    """)
    List<Conversation> findByParticipant(@Param("user") User user);

    // Chercher une conversation privée existante entre 2 utilisateurs
    @Query("""
        SELECT c FROM Conversation c
        JOIN c.participants p1
        JOIN c.participants p2
        WHERE p1 = :user1
        AND p2 = :user2
        AND c.type = 'PRIVE'
    """)
    Optional<Conversation> findPriveConversation(
        @Param("user1") User user1,
        @Param("user2") User user2
    );

    // Vérifier si un utilisateur est participant d'une conversation
    @Query("""
        SELECT COUNT(c) > 0 FROM Conversation c
        JOIN c.participants p
        WHERE c.id = :convId
        AND p.id = :userId
    """)
    boolean isParticipant(
        @Param("convId") Long convId,
        @Param("userId") Long userId
    );
}