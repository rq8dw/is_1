package ru.itmo.is.lab1.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.is.lab1.dto.AddressDto;
import ru.itmo.is.lab1.dto.CoordinatesDto;
import ru.itmo.is.lab1.repository.AddressRepository;
import ru.itmo.is.lab1.repository.CoordinatesRepository;
import ru.itmo.is.lab1.repository.OrganizationRepository;
import ru.itmo.is.lab1.service.exception.NotFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Вспомогательные объекты (координаты и адреса), к которым можно привязать организацию. */
@Service
public class RelatedObjectService {

    private final CoordinatesRepository coordinatesRepository;
    private final AddressRepository addressRepository;
    private final OrganizationRepository organizationRepository;
    private final CascadeDeleter cascadeDeleter;

    public RelatedObjectService(CoordinatesRepository coordinatesRepository,
                                AddressRepository addressRepository,
                                OrganizationRepository organizationRepository,
                                CascadeDeleter cascadeDeleter) {
        this.coordinatesRepository = coordinatesRepository;
        this.addressRepository = addressRepository;
        this.organizationRepository = organizationRepository;
        this.cascadeDeleter = cascadeDeleter;
    }

    @Transactional(readOnly = true)
    public List<CoordinatesDto> listCoordinates() {
        Map<Long, Long> usages = toUsageMap(organizationRepository.countUsagesByCoordinates());
        return coordinatesRepository.findAllByOrderById().stream()
                .map(c -> DtoMapper.toDto(c, usages.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AddressDto> listAddresses() {
        Map<Long, Long> usages = toUsageMap(organizationRepository.countUsagesByAddress());
        return addressRepository.findAllByOrderById().stream()
                .map(a -> DtoMapper.toDto(a, usages.getOrDefault(a.getId(), 0L)))
                .toList();
    }

    /** Удаляет координаты вместе со всеми организациями, которые на них ссылаются. */
    @Transactional
    public void deleteCoordinates(long id) {
        if (!coordinatesRepository.existsById(id)) {
            throw new NotFoundException("Координаты с id = " + id + " не найдены");
        }
        cascadeDeleter.deleteOrganizations(organizationRepository.findAllByCoordinatesId(id));
        cascadeDeleter.deleteUnusedRelatedObjects(List.of(id), List.of());
    }

    /** Удаляет адрес вместе со всеми организациями, которые на него ссылаются. */
    @Transactional
    public void deleteAddress(long id) {
        if (!addressRepository.existsById(id)) {
            throw new NotFoundException("Адрес с id = " + id + " не найден");
        }
        cascadeDeleter.deleteOrganizations(organizationRepository.findAllByAddressId(id));
        cascadeDeleter.deleteUnusedRelatedObjects(List.of(), List.of(id));
    }

    private static Map<Long, Long> toUsageMap(List<Object[]> rows) {
        Map<Long, Long> usages = new HashMap<>();
        for (Object[] row : rows) {
            usages.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return usages;
    }
}
