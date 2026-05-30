package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Conversation;
import com.smartintern.backend.entity.ParticipantConversation;
import com.smartintern.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * U-06 — Repository pour les participants aux conversations.
 */
public interface ParticipantConversationRepository extends JpaRepository<ParticipantConversation, Long> {

    Optional<ParticipantConversation> findByConversationAndUtilisateur(Conversation conv, User user);

    boolean existsByConversationAndUtilisateur(Conversation conv, User user);

    List<ParticipantConversation> findByConversation(Conversation conv);

    List<ParticipantConversation> findByConversationAndEstActifTrue(Conversation conv);
}
