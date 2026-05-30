package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * U-06/U-07 — Conversation (PRIVEE ou REUNION).
 * <ul>
 *   <li>PRIVEE  : entre deux utilisateurs liés par un Stage</li>
 *   <li>REUNION : liée à une Entreprise, participants = tous les actifs</li>
 * </ul>
 */
@Entity
@Table(name = "conversations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TypeConversation type;

    @Column(length = 255)
    private String sujet;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 15)
    private StatutConversation statut = StatutConversation.ACTIVE;

    /** Stage lié — nullable (REUNION n'a pas de stage individuel). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id")
    private Stage stage;

    /** Entreprise liée — renseignée pour REUNION. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entreprise_id")
    private Entreprise entreprise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cree_par_id", nullable = false)
    private User creePar;

    @Column(name = "cree_le", nullable = false)
    @Builder.Default
    private LocalDateTime creeLe = LocalDateTime.now();

    @Column(name = "dernier_message_le")
    private LocalDateTime dernierMessageLe;

    /** Liste des participants (chargement lazy — utilisé uniquement via repository). */
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ParticipantConversation> participants = new ArrayList<>();

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum TypeConversation {
        PRIVEE, REUNION
    }

    public enum StatutConversation {
        ACTIVE, LECTURE_SEULE, ARCHIVEE
    }
}
