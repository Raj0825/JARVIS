package com.jarvis.config;

import com.jarvis.websocket.JarvisWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private JarvisWebSocketHandler handler;

    @Value("${jarvis.cors.allowed-origins}")
    private String allowedOriginsRaw;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = allowedOriginsRaw.split(",");
        registry.addHandler(handler, "/ws/jarvis")
                .setAllowedOrigins(origins);
    }
}
