package ru.itmo.is.lab1.realtime;

import java.util.List;

/** Сообщение, которое рассылается всем подключённым клиентам после фиксации транзакции. */
public record EntityChangedEvent(Entity entity, Change change, List<Long> ids) {

    public enum Entity {
        ORGANIZATION,
        COORDINATES,
        ADDRESS
    }

    public enum Change {
        CREATED,
        UPDATED,
        DELETED
    }
}
