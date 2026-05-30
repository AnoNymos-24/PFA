package com.smartintern.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String role;
    private String nom;
    private String prenom;
    private Long id;
    private boolean firstLogin;
}