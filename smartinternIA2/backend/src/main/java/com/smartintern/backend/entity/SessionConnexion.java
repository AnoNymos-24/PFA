package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Enregistrement d'une session utilisateur.
 *
 * Index : (user_id, statut) → retrouver rapidement les sessions actives d'un user.
 */
@Entity
@Table(
    name = "sessions_connexion",
    indexes = {
        @Index(name = "idx_session_user_statut", columnList = "user_id, statut")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionConnexion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_session")
    private Long id;

    // ── Relations ────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ── Champs métier ────────────────────────────────────────────────────────

    /** Hash SHA-256 du JWT — jamais le token brut */
    @Column(name = "token_hash", length = 128)
    private String tokenHash;

    @Column(name = "date_connexion", nullable = false)
    private LocalDateTime dateConnexion;

    @Column(name = "date_deconnexion")
    private LocalDateTime dateDeconnexion;

    /** Durée en secondes — calculée à la fermeture */
    @Column(name = "duree_secondes")
    private Long dureeSecondes;

    @Column(name = "adresse_ip", length = 50)
    private String adresseIp;

    @Column(name = "appareil")
    private String appareil;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Statut statut = Statut.ACTIVE;

    // ── Énumérations ─────────────────────────────────────────────────────────

    public enum Statut {
        ACTIVE, FERMEE_MANUELLEMENT, EXPIREE, FERMEE_AUTO
    }
}
