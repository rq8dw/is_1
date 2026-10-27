package ru.itmo.is.lab1.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AbsorbRequest(
        @NotNull(message = "Укажите id поглощающей организации")
        @Positive(message = "id должен быть больше 0")
        Long absorberId,

        @NotNull(message = "Укажите id поглощаемой организации")
        @Positive(message = "id должен быть больше 0")
        Long absorbedId
) {
}
