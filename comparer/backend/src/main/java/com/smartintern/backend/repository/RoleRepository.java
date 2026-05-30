package com.smartintern.backend.repository;

import com.smartintern.backend.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(Role.RoleName name);
}