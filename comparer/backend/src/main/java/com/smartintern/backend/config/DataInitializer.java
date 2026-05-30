package com.smartintern.backend.config;

import com.smartintern.backend.entity.Role;
import com.smartintern.backend.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepo;

    @Override
    public void run(String... args) {
        Arrays.stream(Role.RoleName.values()).forEach(name -> {
            if (roleRepo.findByName(name).isEmpty()) {
                Role r = new Role();
                r.setName(name);
                roleRepo.save(r);
            }
        });
        System.out.println("✅ Rôles initialisés");
    }
}