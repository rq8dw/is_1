package ru.itmo.is.lab1.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

/**
 * Либо {@code id} уже существующих координат, либо значения {@code x}/{@code y} для новых.
 */
public record CoordinatesInput(
        @Positive(message = "Некорректный идентификатор координат")
        Long id,

        @Max(value = 549, message = "X не может быть больше 549")
        Double x,

        Double y
) {
}
