package com.smartintern.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "langues")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Langue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_langue")
    private Long id;

    @Column(nullable = false)
    private String langue;

    // Débutant | Intermédiaire | Avancé | Courant | Natif
    private String niveau;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cv_standardise_id", nullable = false)
    private CvStandardise cvStandardise;
}