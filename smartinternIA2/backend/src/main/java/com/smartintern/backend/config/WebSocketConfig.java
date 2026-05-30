package com.smartintern.backend.config;

import com.smartintern.backend.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.*;

/**
 * U-06 — Configuration WebSocket / STOMP.
 *
 * <ul>
 *   <li>Broker simple en mémoire sur {@code /topic} (broadcast) et {@code /queue} (point-à-point)</li>
 *   <li>Préfixe applicatif {@code /app} pour les {@code @MessageMapping}</li>
 *   <li>Préfixe utilisateur {@code /user} pour les destinations personnelles</li>
 *   <li>Endpoint SockJS sur {@code /ws} (avec fallback long-polling)</li>
 *   <li>Authentification JWT via {@link WebSocketAuthInterceptor} sur le canal entrant</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtils           jwtUtils;
    private final UserDetailsService userDetailsService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new WebSocketHandshakeInterceptor())
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor());
    }

    @Bean
    public WebSocketAuthInterceptor webSocketAuthInterceptor() {
        return new WebSocketAuthInterceptor(jwtUtils, userDetailsService);
    }
}
