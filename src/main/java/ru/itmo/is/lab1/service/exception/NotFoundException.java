package ru.itmo.is.lab1.service.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException organization(long id) {
        return new NotFoundException("Организация с id = " + id + " не найдена (возможно, её удалил другой пользователь)");
    }
}
