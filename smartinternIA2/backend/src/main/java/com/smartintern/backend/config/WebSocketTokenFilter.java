package com.smartintern.backend.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * U-06 — Filtre Servlet qui injecte le JWT depuis le paramètre URL {@code ?token=xxx}
 * dans le header {@code Authorization} pour les requêtes WebSocket ({@code /ws/**}).
 *
 * <p>Ce filtre s'exécute avec {@link Ordered#HIGHEST_PRECEDENCE}, <em>avant</em> la
 * chaîne Spring Security. Il permet aux clients browser d'établir la connexion SockJS
 * sans pouvoir positionner de header HTTP personnalisé, tout en réutilisant
 * le {@code JwtAuthFilter} existant sans modification de {@code SecurityConfig}.
 *
 * <p><b>Sécurité</b> : le token n'est ni stocké ni relayé à d'autres endpoints —
 * il est uniquement visible dans le header de la requête courante (mémoire JVM).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebSocketTokenFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) servletRequest;
        String uri   = req.getRequestURI();
        String token = req.getParameter("token");

        if (uri.startsWith("/ws") && token != null && !token.isBlank()) {
            log.trace("WebSocketTokenFilter — injection Authorization depuis ?token pour URI={}", uri);
            chain.doFilter(new TokenHeaderWrapper(req, token), servletResponse);
        } else {
            chain.doFilter(servletRequest, servletResponse);
        }
    }

    // ── Wrapper qui expose le token comme header Authorization ────────────────

    private static class TokenHeaderWrapper extends HttpServletRequestWrapper {

        private final String bearerHeader;

        TokenHeaderWrapper(HttpServletRequest request, String token) {
            super(request);
            this.bearerHeader = "Bearer " + token;
        }

        @Override
        public String getHeader(String name) {
            if ("Authorization".equalsIgnoreCase(name)) return bearerHeader;
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if ("Authorization".equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(bearerHeader));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            if (!names.contains("Authorization")) names.add("Authorization");
            return Collections.enumeration(names);
        }
    }
}
