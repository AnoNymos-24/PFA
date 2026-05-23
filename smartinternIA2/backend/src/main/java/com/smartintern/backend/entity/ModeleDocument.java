package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "modeles_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModeleDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_modele")
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(name = "titre_document")
    private String titreDocument;

    @Column(name = "chemin_modele")
    private String cheminModele;

    @Column(name = "chemin_header")
    private String cheminHeader;

    @Column(name = "chemin_footer")
    private String cheminFooter;

    @Column(name = "chemin_script")
    private String cheminScript;

    @Column(name = "champs_dynamiques", columnDefinition = "TEXT")
    private String champsDynamiques;

    @Column(name = "analyse_ia", columnDefinition = "TEXT")
    private String analyseIa;

    @Builder.Default
    @Column(name = "duree_validite_jours")
    private int dureeValiditeJours = 365;

    @Builder.Default
    private int version = 1;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Statut statut = Statut.ACTIF;

    // ── Champs ajoutés ─────────────────────────────────────────────────────

    /** Date de création — renseignée automatiquement par @PrePersist */
    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    /** Date de dernière modification — mise à jour par @PreUpdate */
    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    /**
     * Date d'expiration fixée par l'admin.
     * Tous les documents générés depuis ce modèle seront invalides après.
     * Si null → calcul depuis dureeValiditeJours.
     */
    @Column(name = "date_expiration")
    private LocalDate dateExpiration;

    /**
     * Signature numérique (JSON) — informations d'authentification admin :
     * {
     *   "nomSignataire"    : "Dr. Ahmed Ben Ali",
     *   "titreSignataire"  : "Directeur des Études",
     *   "nomEtablissement" : "ITEAM University",
     *   "cheminLogo"       : "uploads/logos/logo.png",
     *   "cheminCachet"     : "uploads/cachets/cachet.png",
     *   "cheminSignature"  : "uploads/signatures/signature.png",
     *   "mentionLegale"    : "Ce document est officiel...",
     *   "hashCle"          : "sha256_hash"
     * }
     */
    @Column(name = "signature_numerique", columnDefinition = "TEXT")
    private String signatureNumerique;

    // ── Relations ──────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_document_id", nullable = false)
    private TypeDocument typeDocument;

    @OneToMany(mappedBy = "modeleDocument", fetch = FetchType.LAZY)
    private List<Document> documents;

    // ── Hooks JPA ──────────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        dateModification = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
        version++;
    }

    // ── Méthodes utilitaires ───────────────────────────────────────────────

    public boolean isExpire() {
        return dateExpiration != null && LocalDate.now().isAfter(dateExpiration);
    }

    public LocalDate calculerDateExpirationDocument() {
        return dateExpiration != null ? dateExpiration
                                      : LocalDate.now().plusDays(dureeValiditeJours);
    }

    public enum Statut {
        ACTIF, ARCHIVE, BROUILLON
    }
}