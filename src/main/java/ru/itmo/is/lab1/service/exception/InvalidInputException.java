package ru.itmo.is.lab1.service.exception;

import java.util.Map;

/** Ошибка пользовательского ввода, привязанная к конкретным полям формы. */
public class InvalidInputException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public InvalidInputException(Map<String, String> fieldErrors) {
        super("Проверьте правильность заполнения полей");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public InvalidInputException(String field, String message) {
        super(message);
        this.fieldErrors = Map.of(field, message);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
