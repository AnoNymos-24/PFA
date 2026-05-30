package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * U-06 — Participant à une conversation.
 * Contrainte UNIQUE sur (conversation_id, utilisateur_id).
 */
@Entity
@Table(
    name = "participants_conversation",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_conv_user",
        columnNames = {"conversation_id", "utilisateur_id"}
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParticipantConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private User utilisateur;

    @Column(name = "a_rejoint", nullable = false)
    @Builder.Default
    private LocalDateTime aRejoint = LocalDateTime.now();

    /** Horodatage de la dernière lecture — permet de calculer les non-lus. */
    @Column(name = "derniere_lecture")
    private LocalDateTime derniereLecture;

    @Column(name = "est_actif", nullable = false)
    @Builder.Default
    private boolean estActif = true;
}
