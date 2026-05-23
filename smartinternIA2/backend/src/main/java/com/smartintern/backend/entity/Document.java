package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_document")
    private Long id;

    @Column(name = "url_fichier")
    private String urlFichier;

    @Column(name = "doc_uuid", unique = true)
    private String docUuid;

    @Builder.Default
    @Column(name = "numero_version")
    private int numeroVersion = 1;

    @Column(name = "type_document")
    private String typeDocument;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Statut statut = Statut.VALIDE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Relations ──────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modele_document_id", nullable = false)
    private ModeleDocument modeleDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<DocumentGenere> documentsGeneres;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Statut {
        VALIDE, EXPIRE, REVOQUE
    }
}