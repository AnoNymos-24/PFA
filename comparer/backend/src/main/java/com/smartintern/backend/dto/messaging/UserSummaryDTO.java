package com.smartintern.backend.dto.messaging;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSummaryDTO {
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String role; // ex: "ROLE_ETUDIANT"
}