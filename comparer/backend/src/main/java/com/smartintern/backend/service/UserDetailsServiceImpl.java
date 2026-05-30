package com.smartintern.backend.service;

import com.smartintern.backend.entity.User;
import com.smartintern.backend.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepo;

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepo.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // ✅ CORRECTION : Garder le nom du rôle SANS préfixe ROLE_ (hasRole s'en charge)
        List<SimpleGrantedAuthority> authorities = user.getRoles()
            .stream()
            .map(role -> new SimpleGrantedAuthority(role.getName().name()))
            .collect(Collectors.toList());

        System.out.println("=== CHARGEMENT USER ===");
        System.out.println("Email: " + user.getEmail());
        System.out.println("Authorities (sans ROLE_): " + authorities);

        return new org.springframework.security.core.userdetails.User(
            user.getEmail(),
            user.getPassword(),
            authorities
        );
    }
}