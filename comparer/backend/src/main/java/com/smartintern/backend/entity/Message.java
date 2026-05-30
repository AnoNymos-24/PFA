package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Conversation à laquelle appartient ce message
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    // Expéditeur — référence vers votre User existant
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expediteur_id", nullable = false)
    private User expediteur;

    // Contenu texte du message
    @Column(columnDefinition = "TEXT")
    private String contenu;

    // Champs fichier (remplis uniquement si type = FICHIER ou IMAGE)
    @Column(name = "fichier_nom")
    private String fichierNom;

    @Column(name = "fichier_path")
    private String fichierPath;

    @Column(name = "fichier_type")
    private String fichierType;

    @Column(name = "fichier_taille")
    private Long fichierTaille;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeMessage type = TypeMessage.TEXTE;

    @Column(name = "sent_at", updatable = false)
    private LocalDateTime sentAt = LocalDateTime.now();

    @Column(name = "lu")
    private boolean lu = false;

    // ── Éviter les cycles infinis avec @Data + relations bidirectionnelles ──
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message)) return false;
        Message that = (Message) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    public enum TypeMessage {
        TEXTE,   // message texte simple
        FICHIER, // document (pdf, doc, zip...)
        IMAGE,   // image
        SYSTEME  // message automatique (ex: "X a rejoint la conversation")
    }
}