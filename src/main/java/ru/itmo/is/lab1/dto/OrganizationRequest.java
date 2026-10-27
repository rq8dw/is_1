package ru.itmo.is.lab1.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import ru.itmo.is.lab1.domain.OrganizationType;

/** Тело запроса на создание и изменение организации. */
public record OrganizationRequest(
        @NotBlank(message = "Название не может быть пустым")
        @Size(max = 255, message = "Название не длиннее 255 символов")
        String name,

        @NotNull(message = "Координаты обязательны")
        @Valid
        CoordinatesInput coordinates,

        @Valid
        AddressInput officialAddress,

        @Positive(message = "Годовой оборот должен быть больше 0")
        Long annualTurnover,

        @NotNull(message = "Укажите количество сотрудников")
        @Positive(message = "Количество сотрудников должно быть больше 0")
        Long employeesCount,

        @Positive(message = "Рейтинг должен быть больше 0")
        Float rating,

        @NotNull(message = "Полное название не может быть null")
        @Size(max = 255, message = "Полное название не длиннее 255 символов")
        String fullName,

        @NotNull(message = "Выберите тип организации")
        OrganizationType type,

        @Valid
        AddressInput postalAddress
) {
}
