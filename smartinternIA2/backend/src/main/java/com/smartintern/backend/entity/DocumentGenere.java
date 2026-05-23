package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents_generes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentGenere {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_document_genere")
    private Long id;

    @Column(name = "doc_uuid", unique = true, nullable = false)
    private String docUuid;

    @Column(name = "url_fichier")
    private String urlFichier;

    @Column(name = "url_verification")
    private String urlVerification;

    @Column(name = "chemin_fichier")
    private String cheminFichier;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Column(name = "signature", nullable = false)
    private String signature;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Statut statut = Statut.VALIDE;

    @Builder.Default
    @Column(name = "numero_version")
    private int numeroVersion = 1;

    @Column(name = "doc_uuid_original")
    private String docUuidOriginal;

    @Column(name = "taille_octets")
    private Long tailleOctets;

    @Column(name = "nom_etablissement")
    private String nomEtablissement;

    // ── Relations ──────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (expiredAt != null && LocalDateTime.now().isAfter(expiredAt)) {
            statut = Statut.EXPIRE;
        }
    }

    public boolean isExpire() {
        return expiredAt != null && LocalDateTime.now().isAfter(expiredAt);
    }

    public boolean isValide() {
        return statut == Statut.VALIDE && !isExpire();
    }

    public enum Statut {
        VALIDE, EXPIRE, REVOQUE, FALSIFIE
    }
}