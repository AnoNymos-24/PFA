package com.smartintern.backend.config;

import com.smartintern.backend.security.JwtService;
import com.smartintern.backend.service.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // ✅ CORRECTION : setAllowedOrigins avec l'URL exacte du frontend
                .setAllowedOrigins(
                    "http://127.0.0.1:5500",
                    "http://localhost:5500",
                    "http://localhost:3000",
                    "http://127.0.0.1:3000"
                )
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {

                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");

                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        try {
                            String email = jwtService.extractEmail(token);
                            if (email != null) {
                                UserDetails userDetails =
                                        userDetailsService.loadUserByUsername(email);
                                if (jwtService.validateToken(token, email)
                                        && userDetails.isEnabled()) {
                                    UsernamePasswordAuthenticationToken auth =
                                            new UsernamePasswordAuthenticationToken(
                                                    userDetails, null,
                                                    userDetails.getAuthorities());
                                    accessor.setUser(auth);
                                    log.debug("WebSocket auth OK : {}", email);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("WebSocket auth failed : {}", e.getMessage());
                        }
                    }
                }
                return message;
            }
        });
    }
}