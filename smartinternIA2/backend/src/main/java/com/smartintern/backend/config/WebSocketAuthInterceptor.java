package com.smartintern.backend.config;

import com.smartintern.backend.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;
import java.util.Map;

/**
 * U-06 — Intercepteur STOMP : valide le JWT au moment du frame {@code CONNECT}.
 *
 * <p>Ordre de recherche du token :
 * <ol>
 *   <li>Header STOMP natif {@code Authorization: Bearer xxx}</li>
 *   <li>Attribut de session WebSocket {@code "jwtToken"} (positionné par le handshake interceptor)</li>
 * </ol>
 *
 * <p>Si le token est valide : positionne le {@link java.security.Principal} STOMP
 * via {@code StompHeaderAccessor.setUser(...)}.
 * <br>Si le token est invalide ou absent : lève {@link MessagingException} → déconnexion propre.
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtils             jwtUtils;
    private final UserDetailsService   userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message; // Pas un CONNECT — laisser passer
        }

        String token = extractToken(accessor);
        if (token == null || token.isBlank()) {
            throw new MessagingException("Token JWT absent de la connexion STOMP");
        }

        try {
            String email = jwtUtils.extractEmail(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            if (!jwtUtils.isTokenValid(token, userDetails)) {
                throw new MessagingException("Token JWT invalide ou expiré");
            }

            // Construire l'authentification Spring Security pour STOMP
            List<SimpleGrantedAuthority> authorities = userDetails.getAuthorities().stream()
                    .map(a -> new SimpleGrantedAuthority(a.getAuthority()))
                    .toList();
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(email, null, authorities);

            accessor.setUser(auth);
            log.debug("WebSocket CONNECT authentifié — user={}", email);

        } catch (MessagingException e) {
            throw e; // Re-lever sans modification
        } catch (Exception e) {
            log.warn("WebSocket CONNECT — échec validation JWT : {}", e.getMessage());
            throw new MessagingException("Token JWT invalide : " + e.getMessage());
        }

        return message;
    }

    // ── Extraction du token (header STOMP > attribut session) ────────────────

    private String extractToken(StompHeaderAccessor accessor) {
        // 1. Header natif STOMP : Authorization: Bearer xxx
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 2. Attribut de session (positionné par WebSocketHandshakeInterceptor)
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null) {
            Object sessionToken = attrs.get("jwtToken");
            if (sessionToken instanceof String s && !s.isBlank()) {
                return s;
            }
        }

        return null;
    }
}
