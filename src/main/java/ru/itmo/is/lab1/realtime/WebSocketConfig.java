package ru.itmo.is.lab1.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final UpdatesWebSocketHandler updatesHandler;

    public WebSocketConfig(UpdatesWebSocketHandler updatesHandler) {
        this.updatesHandler = updatesHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(updatesHandler, "/ws/updates");
    }
}
