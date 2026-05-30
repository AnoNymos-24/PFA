package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

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

    @Column(nullable = false)
    private String nom;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeConversation type = TypeConversation.GROUPE;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Créateur de la conversation — référence vers votre User existant
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "createur_id")
    private User createur;

    // Participants — table de jointure dédiée
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "conversation_participants",
        joinColumns = @JoinColumn(name = "conversation_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> participants = new HashSet<>();

    // Messages de la conversation — ordonnés par date croissante
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sentAt ASC")
    private List<Message> messages = new ArrayList<>();

    // ── Éviter les cycles infinis avec @Data + relations bidirectionnelles ──
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Conversation)) return false;
        Conversation that = (Conversation) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    public enum TypeConversation {
        PRIVE,   // 1 contre 1
        GROUPE,  // groupe général
        STAGE    // lié à un stage
    }
}