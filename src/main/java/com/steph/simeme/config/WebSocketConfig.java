package com.steph.simeme.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // Mengaktifkan server STOMP WebSocket di Spring Boot
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Mengaktifkan In-Memory Message Broker untuk saluran yang berawalan "/topic"
        config.enableSimpleBroker("/topic");
        
        // Prefix jika nanti frontend ingin mengirim pesan balik via WebSocket ke backend
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Pintu masuk (handshake URL) tempat Frontend menyambungkan WebSocket: ws://localhost:8080/ws-simeme
        registry.addEndpoint("/ws-simeme")
                .setAllowedOriginPatterns("*") // Mengizinkan koneksi dari semua origin (penting untuk UI nanti)
                .withSockJS(); // Fallback otomatis jika browser tidak mendukung WebSocket murni
    }
}