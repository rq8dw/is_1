package ru.itmo.is.lab1.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Либо {@code id} уже существующего адреса, либо {@code zipCode} для нового.
 * Отсутствие объекта целиком (null) означает, что адреса нет.
 */
public record AddressInput(
        @Positive(message = "Некорректный идентификатор адреса")
        Long id,

        @Size(max = 255, message = "Почтовый индекс не длиннее 255 символов")
        String zipCode
) {
}
