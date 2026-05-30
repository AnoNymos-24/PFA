package com.smartintern.backend.security;

import com.smartintern.backend.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
 
protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain filterChain)
        throws ServletException, IOException {

    String path   = request.getServletPath();
    String method = request.getMethod();

    // Laisser passer WebSocket (pas de JWT)
    if (path.startsWith("/ws")) {
        filterChain.doFilter(request, response);
        return;
    }

    // Routes publiques SANS vérification JWT
    if (path.startsWith("/api/auth") ||
        path.startsWith("/api/offres/public") ||
        method.equals("OPTIONS")) {
        filterChain.doFilter(request, response);
        return;
    }

    // Pour TOUTES les autres routes, y compris /api/messaging, on vérifie le JWT
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token manquant");
        return;
    }

    String token = authHeader.substring(7);
    try {
        String email = jwtService.extractEmail(token);
        boolean valid = jwtService.validateToken(token, email);

        System.out.println("=== JWT email=" + email + " valid=" + valid + " path=" + path);

        if (!valid) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token invalide");
            return;
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            if (!userDetails.isEnabled()) {
                sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "Compte désactivé");
                return;
            }
            var authToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
            System.out.println("=== JWT auth OK pour : " + email + " — roles : " + userDetails.getAuthorities());
        }

        filterChain.doFilter(request, response);

    } catch (Exception e) {
        System.err.println("=== JWT ERREUR : " + e.getMessage());
        sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token invalide ou expiré");
    }
}
    private void sendErrorResponse(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
            "{\"status\": %d, \"message\": \"%s\", \"timestamp\": %d}",
            status, message, System.currentTimeMillis()
        ));
        response.getWriter().flush();
    }
}