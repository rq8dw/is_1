package ru.itmo.is.lab1.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Держит открытые соединения браузеров и рассылает им уведомления об изменениях. */
@Component
public class UpdatesWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(UpdatesWebSocketHandler.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Декоратор сериализует отправку: WebSocketSession не потокобезопасен
        sessions.put(session.getId(),
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
    }

    public void broadcast(String payload) {
        TextMessage message = new TextMessage(payload);
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (IOException | IllegalStateException e) {
                log.debug("Не удалось отправить уведомление в сессию {}: {}", session.getId(), e.getMessage());
                sessions.remove(session.getId());
            }
        });
    }
}
