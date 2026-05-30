package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Role;
import com.smartintern.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // ✅ AJOUTEZ CETTE MÉTHODE
    Optional<User> findByEmail(String email);
    
    // Version existante
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailIgnoreCase(String email); 
    
    boolean existsByEmail(String email);

    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.name = :roleName")
    long countByRoleName(@Param("roleName") Role.RoleName roleName);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName")
    List<User> findByRoleName(@Param("roleName") Role.RoleName roleName);
}