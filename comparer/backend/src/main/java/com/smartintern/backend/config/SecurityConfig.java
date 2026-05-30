package com.smartintern.backend.config;

import com.smartintern.backend.security.JwtAuthFilter;
import com.smartintern.backend.service.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth

                // ✅ WEBSOCKET — doit être AVANT tout le reste
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/ws/info/**").permitAll()

                // Routes publiques
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/offres/public/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/candidatures/uploads/cv/**").permitAll()

                // Admin
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/stats").hasRole("ADMIN")

                // Entreprise
                .requestMatchers("/api/offres/mes-offres").hasRole("ENTREPRISE")

                // Missions
                .requestMatchers(HttpMethod.GET, "/api/missions/etudiant/mes-missions").hasRole("ETUDIANT")
                .requestMatchers(HttpMethod.PUT, "/api/missions/{missionId}/demarrer").hasRole("ETUDIANT")
                .requestMatchers(HttpMethod.POST, "/api/missions/{missionId}/compte-rendu").hasRole("ETUDIANT")
                .requestMatchers(HttpMethod.GET, "/api/missions/encadrant/mes-missions")
                    .hasAnyRole("ENCADRANT_ENTREPRISE", "ENCADRANT_ACADEMIQUE")
                .requestMatchers(HttpMethod.PUT, "/api/missions/compte-rendu/{compteRenduId}/valider")
                    .hasAnyRole("ENCADRANT_ENTREPRISE", "ENCADRANT_ACADEMIQUE")
                .requestMatchers(HttpMethod.PUT, "/api/missions/compte-rendu/{compteRenduId}/rejeter")
                    .hasAnyRole("ENCADRANT_ENTREPRISE", "ENCADRANT_ACADEMIQUE")
                .requestMatchers("/api/missions/**").authenticated()

                // Contacts (messagerie — accessible à tous les rôles)
                .requestMatchers("/api/users/contacts").authenticated()
                

                // Messagerie
                    // Messagerie - ACCÈS COMPLET POUR TOUS LES RÔLES AUTHENTIFIÉS
.requestMatchers("/api/messaging/**").authenticated()
                    
                // Autres routes authentifiées
                .requestMatchers("/api/candidatures/**").authenticated()
                .requestMatchers("/api/stages/**").authenticated()
                .requestMatchers("/api/rapports/**").authenticated()
                .requestMatchers("/api/offres/**").authenticated()

                .anyRequest().authenticated()
            )
            .userDetailsService(userDetailsService)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // ✅ CORRECTION CORS : allowCredentials=true exige des origines explicites
        config.setAllowedOrigins(Arrays.asList(
            "http://127.0.0.1:5500",
            "http://localhost:5500",
            "http://localhost:3000",
            "http://127.0.0.1:3000"
        ));
        config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS","PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        // ✅ CORRECTION : true obligatoire pour SockJS
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}