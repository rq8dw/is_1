package ru.itmo.is.lab1.service;

import ru.itmo.is.lab1.domain.Address;
import ru.itmo.is.lab1.domain.Coordinates;
import ru.itmo.is.lab1.domain.Organization;
import ru.itmo.is.lab1.dto.AddressDto;
import ru.itmo.is.lab1.dto.CoordinatesDto;
import ru.itmo.is.lab1.dto.OrganizationDto;

/** Вызывать внутри транзакции: связи организации загружаются лениво. */
final class DtoMapper {

    private DtoMapper() {
    }

    static OrganizationDto toDto(Organization organization) {
        return new OrganizationDto(
                organization.getId(),
                organization.getName(),
                toDto(organization.getCoordinates(), null),
                organization.getCreationDate(),
                toDto(organization.getOfficialAddress(), null),
                organization.getAnnualTurnover(),
                organization.getEmployeesCount(),
                organization.getRating(),
                organization.getFullName(),
                organization.getType(),
                toDto(organization.getPostalAddress(), null));
    }

    static CoordinatesDto toDto(Coordinates coordinates, Long usages) {
        return coordinates == null ? null
                : new CoordinatesDto(coordinates.getId(), coordinates.getX(), coordinates.getY(), usages);
    }

    static AddressDto toDto(Address address, Long usages) {
        return address == null ? null : new AddressDto(address.getId(), address.getZipCode(), usages);
    }
}
