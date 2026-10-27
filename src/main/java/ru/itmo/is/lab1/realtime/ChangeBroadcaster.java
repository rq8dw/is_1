package ru.itmo.is.lab1.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Рассылает изменения только после успешного коммита, чтобы клиенты,
 * перезапросив данные, гарантированно увидели новое состояние БД.
 */
@Component
public class ChangeBroadcaster {

    private final UpdatesWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    public ChangeBroadcaster(UpdatesWebSocketHandler webSocketHandler, ObjectMapper objectMapper) {
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChange(EntityChangedEvent event) throws JsonProcessingException {
        webSocketHandler.broadcast(objectMapper.writeValueAsString(event));
    }
}
