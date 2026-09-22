package com.jarvis.config;

import com.jarvis.websocket.JarvisWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

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

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(10 * 1024 * 1024); // 10 MB
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024); // 10 MB
        container.setMaxSessionIdleTimeout(300000L);
        return container;
    }
}

