package com.smartintern.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Map;

/**
 * U-06 — Intercepteur HTTP au handshake WebSocket.
 *
 * <p>Lors de l'upgrade HTTP → WebSocket, extrait le paramètre {@code ?token=xxx}
 * de la requête HTTP initiale et le stocke dans les attributs de session WebSocket
 * sous la clé {@code "jwtToken"}. L'intercepteur STOMP
 * ({@link WebSocketAuthInterceptor}) l'y retrouvera lors du frame {@code CONNECT}.
 */
@Slf4j
public class WebSocketHandshakeInterceptor extends HttpSessionHandshakeInterceptor {

    private static final String TOKEN_ATTR = "jwtToken";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {

        if (request instanceof ServletServerHttpRequest servletReq) {
            String token = servletReq.getServletRequest().getParameter("token");
            if (token != null && !token.isBlank()) {
                attributes.put(TOKEN_ATTR, token);
                log.debug("Handshake WS — token sauvegardé dans la session WebSocket");
            }
        }
        return super.beforeHandshake(request, response, wsHandler, attributes);
    }
}
