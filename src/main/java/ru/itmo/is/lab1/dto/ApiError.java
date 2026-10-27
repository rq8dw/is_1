package ru.itmo.is.lab1.dto;

import java.time.Instant;
import java.util.Map;

/**
 * @param fieldErrors сообщения по полям формы; ключ — путь к полю, например {@code coordinates.x}
 */
public record ApiError(int status, String message, Map<String, String> fieldErrors, Instant timestamp) {

    public ApiError(int status, String message, Map<String, String> fieldErrors) {
        this(status, message, fieldErrors, Instant.now());
    }

    public ApiError(int status, String message) {
        this(status, message, Map.of());
    }
}
