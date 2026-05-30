package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * U-07 — Message dans une conversation.
 * Contenu limité à 2000 caractères (validé en service).
 */
@Entity
@Table(name = "messages",
       indexes = {
           @Index(name = "idx_messages_conv_date", columnList = "conversation_id, envoie_le DESC"),
           @Index(name = "idx_messages_nonlus",    columnList = "conversation_id, lu, expediteur_id")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expediteur_id", nullable = false)
    private User expediteur;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String contenu;

    @Column(name = "envoie_le", nullable = false)
    @Builder.Default
    private LocalDateTime envoieLe = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private boolean lu = false;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 10)
    private TypeMessage type = TypeMessage.TEXTE;

    // ── Pièce jointe (rempli uniquement si type = FICHIER ou IMAGE) ───────────

    @Column(name = "fichier_nom")
    private String fichierNom;

    @Column(name = "fichier_path")
    private String fichierPath;

    @Column(name = "fichier_type", length = 100)
    private String fichierType;

    @Column(name = "fichier_taille")
    private Long fichierTaille;

    // ── Enum ──────────────────────────────────────────────────────────────────

    public enum TypeMessage {
        TEXTE,   // message texte simple
        SYSTEME, // message système automatique (ex : "X a rejoint")
        FICHIER, // document (PDF, DOCX, ZIP…)
        IMAGE    // image (JPEG, PNG, GIF…)
    }
}
