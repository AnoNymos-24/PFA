package com.smartintern.backend.dto.messaging;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserContactDTO {
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String role;
}