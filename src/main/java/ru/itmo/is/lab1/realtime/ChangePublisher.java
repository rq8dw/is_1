package ru.itmo.is.lab1.realtime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import ru.itmo.is.lab1.realtime.EntityChangedEvent.Change;
import ru.itmo.is.lab1.realtime.EntityChangedEvent.Entity;

import java.util.Collection;
import java.util.List;

@Component
public class ChangePublisher {

    private final ApplicationEventPublisher eventPublisher;

    public ChangePublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publish(Entity entity, Change change, Collection<Long> ids) {
        if (!ids.isEmpty()) {
            eventPublisher.publishEvent(new EntityChangedEvent(entity, change, List.copyOf(ids)));
        }
    }

    public void publish(Entity entity, Change change, long id) {
        publish(entity, change, List.of(id));
    }
}
