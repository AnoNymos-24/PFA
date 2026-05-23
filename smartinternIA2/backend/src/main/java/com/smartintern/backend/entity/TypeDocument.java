package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "types_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TypeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_type_document")
    private Long id;

    @Column(nullable = false, unique = true)
    private String nom;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Code interne : "convention_stage", "attestation_stage"...
    @Column(nullable = false, unique = true)
    private String code;

    @OneToMany(mappedBy = "typeDocument", fetch = FetchType.LAZY)
    private List<ModeleDocument> modeles;
}