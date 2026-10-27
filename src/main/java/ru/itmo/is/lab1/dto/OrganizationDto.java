package ru.itmo.is.lab1.dto;

import ru.itmo.is.lab1.domain.OrganizationType;

import java.time.ZonedDateTime;

public record OrganizationDto(
        long id,
        String name,
        CoordinatesDto coordinates,
        ZonedDateTime creationDate,
        AddressDto officialAddress,
        Long annualTurnover,
        long employeesCount,
        Float rating,
        String fullName,
        OrganizationType type,
        AddressDto postalAddress
) {
}
