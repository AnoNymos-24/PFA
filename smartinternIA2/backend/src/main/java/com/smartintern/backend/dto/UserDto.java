package com.smartintern.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class UserDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserResponse {
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
        private String telephone;
        private String role;
        private String statut;
        private LocalDateTime createdAt;
    }

    @Data
    public static class RoleRequest {
        private String role;
    }

    @Data
    public static class StatutRequest {
        private String statut;
    }
}
