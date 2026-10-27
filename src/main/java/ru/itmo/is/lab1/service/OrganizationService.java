package ru.itmo.is.lab1.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.is.lab1.domain.Address;
import ru.itmo.is.lab1.domain.Coordinates;
import ru.itmo.is.lab1.domain.Organization;
import ru.itmo.is.lab1.dto.AddressInput;
import ru.itmo.is.lab1.dto.CoordinatesInput;
import ru.itmo.is.lab1.dto.OrganizationDto;
import ru.itmo.is.lab1.dto.OrganizationRequest;
import ru.itmo.is.lab1.dto.PageDto;
import ru.itmo.is.lab1.realtime.ChangePublisher;
import ru.itmo.is.lab1.realtime.EntityChangedEvent.Change;
import ru.itmo.is.lab1.realtime.EntityChangedEvent.Entity;
import ru.itmo.is.lab1.repository.AddressRepository;
import ru.itmo.is.lab1.repository.CoordinatesRepository;
import ru.itmo.is.lab1.repository.OrganizationRepository;
import ru.itmo.is.lab1.repository.OrganizationSpecifications;
import ru.itmo.is.lab1.service.exception.InvalidInputException;
import ru.itmo.is.lab1.service.exception.NotFoundException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

@Service
public class OrganizationService {

    public static final int MAX_PAGE_SIZE = 100;

    /** Колонка таблицы на клиенте -> свойство сущности для сортировки. */
    private static final Map<String, String> SORT_PROPERTIES = Map.ofEntries(
            entry("id", "id"),
            entry("name", "name"),
            entry("coordinates", "coordinates.x"),
            entry("creationDate", "creationDate"),
            entry("officialAddress", "officialAddress.zipCode"),
            entry("annualTurnover", "annualTurnover"),
            entry("employeesCount", "employeesCount"),
            entry("rating", "rating"),
            entry("fullName", "fullName"),
            entry("type", "type"),
            entry("postalAddress", "postalAddress.zipCode"));

    private final OrganizationRepository organizationRepository;
    private final CoordinatesRepository coordinatesRepository;
    private final AddressRepository addressRepository;
    private final CascadeDeleter cascadeDeleter;
    private final ChangePublisher changePublisher;

    public OrganizationService(OrganizationRepository organizationRepository,
                               CoordinatesRepository coordinatesRepository,
                               AddressRepository addressRepository,
                               CascadeDeleter cascadeDeleter,
                               ChangePublisher changePublisher) {
        this.organizationRepository = organizationRepository;
        this.coordinatesRepository = coordinatesRepository;
        this.addressRepository = addressRepository;
        this.cascadeDeleter = cascadeDeleter;
        this.changePublisher = changePublisher;
    }

    @Transactional(readOnly = true)
    public PageDto<OrganizationDto> findPage(OrganizationFilter filter, int page, int size,
                                             String sortField, String sortDirection) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (page < 0) {
            errors.put("page", "Номер страницы не может быть отрицательным");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            errors.put("size", "Размер страницы должен быть от 1 до " + MAX_PAGE_SIZE);
        }
        String sortProperty = SORT_PROPERTIES.get(sortField);
        if (sortProperty == null) {
            errors.put("sort", "Сортировка по полю '" + sortField + "' не поддерживается");
        }
        Sort.Direction direction = Sort.Direction.fromOptionalString(sortDirection).orElse(null);
        if (direction == null) {
            errors.put("direction", "Направление сортировки: asc или desc");
        }
        if (!errors.isEmpty()) {
            throw new InvalidInputException(errors);
        }

        // Вторичная сортировка по id делает порядок строк между страницами стабильным
        Sort sort = Sort.by(direction, sortProperty).and(Sort.by("id"));
        Page<Organization> result = organizationRepository.findAll(
                OrganizationSpecifications.matching(filter.name(), filter.fullName(), filter.type(),
                        filter.officialZipCode(), filter.postalZipCode()),
                PageRequest.of(page, size, sort));
        return new PageDto<>(result.map(DtoMapper::toDto).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public OrganizationDto get(long id) {
        return DtoMapper.toDto(organizationRepository.findWithRelationsById(id)
                .orElseThrow(() -> NotFoundException.organization(id)));
    }

    @Transactional
    public OrganizationDto create(OrganizationRequest request) {
        Relations relations = resolveRelations(request);
        Organization organization = new Organization();
        applyRequest(organization, request, relations);
        organizationRepository.save(organization);
        changePublisher.publish(Entity.ORGANIZATION, Change.CREATED, organization.getId());
        return DtoMapper.toDto(organization);
    }

    @Transactional
    public OrganizationDto update(long id, OrganizationRequest request) {
        Organization organization = organizationRepository.findWithRelationsById(id)
                .orElseThrow(() -> NotFoundException.organization(id));
        Relations relations = resolveRelations(request);

        Long oldCoordinatesId = organization.getCoordinates().getId();
        List<Long> oldAddressIds = new ArrayList<>();
        if (organization.getOfficialAddress() != null) {
            oldAddressIds.add(organization.getOfficialAddress().getId());
        }
        if (organization.getPostalAddress() != null) {
            oldAddressIds.add(organization.getPostalAddress().getId());
        }

        applyRequest(organization, request, relations);
        organizationRepository.flush();
        changePublisher.publish(Entity.ORGANIZATION, Change.UPDATED, id);

        // Прежние координаты/адреса, на которые больше никто не ссылается, удаляются
        cascadeDeleter.deleteUnusedRelatedObjects(List.of(oldCoordinatesId), oldAddressIds);
        return DtoMapper.toDto(organization);
    }

    @Transactional
    public void delete(long id) {
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> NotFoundException.organization(id));
        cascadeDeleter.deleteOrganizations(List.of(organization));
    }

    private void applyRequest(Organization organization, OrganizationRequest request, Relations relations) {
        organization.setName(request.name());
        organization.setFullName(request.fullName());
        organization.setType(request.type());
        organization.setEmployeesCount(request.employeesCount());
        organization.setAnnualTurnover(request.annualTurnover());
        organization.setRating(request.rating());
        organization.setCoordinates(saveIfNew(relations.coordinates()));
        organization.setOfficialAddress(saveIfNew(relations.officialAddress()));
        organization.setPostalAddress(saveIfNew(relations.postalAddress()));
    }

    /**
     * Проверяет ссылки на вспомогательные объекты. Новые объекты возвращаются
     * несохранёнными: в БД они попадут, только если все поля корректны.
     */
    private Relations resolveRelations(OrganizationRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (request.rating() != null && !Float.isFinite(request.rating())) {
            errors.put("rating", "Рейтинг должен быть конечным числом");
        }
        Coordinates coordinates = resolveCoordinates(request.coordinates(), errors);
        Address officialAddress = resolveAddress(request.officialAddress(), "officialAddress", errors);
        Address postalAddress = resolveAddress(request.postalAddress(), "postalAddress", errors);
        if (!errors.isEmpty()) {
            throw new InvalidInputException(errors);
        }
        return new Relations(coordinates, officialAddress, postalAddress);
    }

    private Coordinates resolveCoordinates(CoordinatesInput input, Map<String, String> errors) {
        if (input == null) {
            errors.put("coordinates", "Координаты обязательны");
            return null;
        }
        if (input.id() != null) {
            Coordinates existing = coordinatesRepository.findById(input.id()).orElse(null);
            if (existing == null) {
                errors.put("coordinates", "Координаты с id = " + input.id() + " не найдены (возможно, удалены)");
            }
            return existing;
        }

        int errorsBefore = errors.size();
        if (input.x() == null) {
            errors.put("coordinates.x", "Укажите X");
        } else if (!Double.isFinite(input.x())) {
            errors.put("coordinates.x", "X должен быть конечным числом");
        } else if (input.x() > Coordinates.MAX_X) {
            errors.put("coordinates.x", "X не может быть больше 549");
        }
        if (input.y() == null) {
            errors.put("coordinates.y", "Укажите Y");
        } else if (!Double.isFinite(input.y())) {
            errors.put("coordinates.y", "Y должен быть конечным числом");
        }
        return errors.size() == errorsBefore ? new Coordinates(input.x(), input.y()) : null;
    }

    private Address resolveAddress(AddressInput input, String field, Map<String, String> errors) {
        if (input == null) {
            return null;
        }
        if (input.id() != null) {
            Address existing = addressRepository.findById(input.id()).orElse(null);
            if (existing == null) {
                errors.put(field, "Адрес с id = " + input.id() + " не найден (возможно, удалён)");
            }
            return existing;
        }
        if (input.zipCode() == null) {
            errors.put(field + ".zipCode", "Укажите почтовый индекс");
            return null;
        }
        return new Address(input.zipCode());
    }

    private Coordinates saveIfNew(Coordinates coordinates) {
        if (coordinates.getId() != null) {
            return coordinates;
        }
        Coordinates saved = coordinatesRepository.save(coordinates);
        changePublisher.publish(Entity.COORDINATES, Change.CREATED, saved.getId());
        return saved;
    }

    private Address saveIfNew(Address address) {
        if (address == null || address.getId() != null) {
            return address;
        }
        Address saved = addressRepository.save(address);
        changePublisher.publish(Entity.ADDRESS, Change.CREATED, saved.getId());
        return saved;
    }

    private record Relations(Coordinates coordinates, Address officialAddress, Address postalAddress) {
    }
}
