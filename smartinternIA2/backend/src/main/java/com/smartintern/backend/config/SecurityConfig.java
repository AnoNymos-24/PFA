package com.smartintern.backend.config;

import com.smartintern.backend.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.frontend.url:http://localhost:5500,http://127.0.0.1:5500}")
    private String frontendUrl;

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(@Lazy JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth

                // ── Public : auth ──────────────────────────────────────────
                .requestMatchers("/api/auth/**").permitAll()

                // ── WebSocket / SockJS (auth gérée par WebSocketAuthInterceptor) ──
                .requestMatchers("/ws/**").permitAll()

                // ── Authentification & vérification de documents (QR code scannable publiquement) ──
                .requestMatchers("/api/documents/*/authentifier").permitAll()
                .requestMatchers("/api/documents/*/verifier").permitAll()

                // ── Étudiant ──────────────────────────────────────────────
                .requestMatchers("/api/etudiant/**").hasRole("ETUDIANT")

                // ── Entreprise ────────────────────────────────────────────
                .requestMatchers("/api/entreprise/**").hasRole("ENTREPRISE")

                // ── Admin ─────────────────────────────────────────────────
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // ── Encadrant académique ───────────────────────────────────
                .requestMatchers("/api/encadrant-academique/**")
                    .hasRole("ENCADRANT_ACADEMIQUE")

                // ── Encadrant entreprise ───────────────────────────────────
                .requestMatchers("/api/encadrant-entreprise/**")
                    .hasRole("ENCADRANT_ENTREPRISE")

                // ── Documents — génération et téléchargement (tout utilisateur authentifié) ──
                .requestMatchers("/api/documents/**").authenticated()

                // ── Notifications (tout utilisateur authentifié) ───────────
                .requestMatchers("/api/notifications/**").authenticated()

                // ── Candidatures (étudiant + entreprise) ──────────────────
                .requestMatchers("/api/candidatures/**").authenticated()

                // ── Encadrants (entreprise crée ses propres encadrants) ────
                .requestMatchers("/api/encadrants/**").authenticated()

                // ── Rapports — accès multi-rôles ──────────────────────────
                .requestMatchers("/api/rapports/**").authenticated()

                // ── Messagerie (tout utilisateur authentifié) ─────────────
                .requestMatchers("/api/messagerie/**").authenticated()

                // ── Tout le reste nécessite une authentification ───────────
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(frontendUrl.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
