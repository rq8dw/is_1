package ru.itmo.is.lab1.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.is.lab1.domain.OrganizationType;
import ru.itmo.is.lab1.dto.AbsorbResult;
import ru.itmo.is.lab1.dto.OrganizationDto;
import ru.itmo.is.lab1.realtime.ChangePublisher;
import ru.itmo.is.lab1.realtime.EntityChangedEvent.Change;
import ru.itmo.is.lab1.realtime.EntityChangedEvent.Entity;
import ru.itmo.is.lab1.repository.OrganizationRepository;
import ru.itmo.is.lab1.repository.OrganizationRepository.RelationIds;
import ru.itmo.is.lab1.repository.SpecialOperationsRepository;
import ru.itmo.is.lab1.service.exception.InvalidInputException;
import ru.itmo.is.lab1.service.exception.NotFoundException;

import java.util.ArrayList;
import java.util.List;

/** Специальные операции. Сама логика реализована функциями БД. */
@Service
public class SpecialOperationsService {

    private final SpecialOperationsRepository specialOperationsRepository;
    private final OrganizationRepository organizationRepository;
    private final CascadeDeleter cascadeDeleter;
    private final ChangePublisher changePublisher;

    public SpecialOperationsService(SpecialOperationsRepository specialOperationsRepository,
                                    OrganizationRepository organizationRepository,
                                    CascadeDeleter cascadeDeleter,
                                    ChangePublisher changePublisher) {
        this.specialOperationsRepository = specialOperationsRepository;
        this.organizationRepository = organizationRepository;
        this.cascadeDeleter = cascadeDeleter;
        this.changePublisher = changePublisher;
    }

    @Transactional(readOnly = true)
    public long countByType(OrganizationType type) {
        requireType(type);
        return specialOperationsRepository.countByType(type);
    }

    @Transactional(readOnly = true)
    public long countFullNameGreaterThan(String fullName) {
        if (fullName == null) {
            throw new InvalidInputException("fullName", "Укажите значение fullName для сравнения");
        }
        return specialOperationsRepository.countFullNameGreaterThan(fullName);
    }

    @Transactional(readOnly = true)
    public List<OrganizationDto> findTypeLessThan(OrganizationType type) {
        requireType(type);
        return specialOperationsRepository.findTypeLessThan(type).stream()
                .map(DtoMapper::toDto)
                .toList();
    }

    @Transactional
    public OrganizationDto addEmployee(long organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw NotFoundException.organization(organizationId);
        }
        specialOperationsRepository.addEmployee(organizationId);
        changePublisher.publish(Entity.ORGANIZATION, Change.UPDATED, organizationId);
        return loadFresh(organizationId);
    }

    @Transactional
    public AbsorbResult absorb(long absorberId, long absorbedId) {
        if (absorberId == absorbedId) {
            throw new InvalidInputException("absorbedId", "Организация не может поглотить саму себя");
        }
        if (!organizationRepository.existsById(absorberId)) {
            throw new InvalidInputException("absorberId",
                    "Поглощающая организация с id = " + absorberId + " не найдена");
        }
        RelationIds absorbedRelations = organizationRepository.findRelationIds(absorbedId)
                .orElseThrow(() -> new InvalidInputException("absorbedId",
                        "Поглощаемая организация с id = " + absorbedId + " не найдена"));
        long employeesBefore = organizationRepository.findEmployeesCount(absorberId).orElseThrow();

        long employeesAfter = specialOperationsRepository.absorb(absorberId, absorbedId);

        changePublisher.publish(Entity.ORGANIZATION, Change.DELETED, absorbedId);
        changePublisher.publish(Entity.ORGANIZATION, Change.UPDATED, absorberId);
        List<Long> addressIds = new ArrayList<>();
        addressIds.add(absorbedRelations.getOfficialAddressId());
        addressIds.add(absorbedRelations.getPostalAddressId());
        cascadeDeleter.deleteUnusedRelatedObjects(List.of(absorbedRelations.getCoordinatesId()), addressIds);

        return new AbsorbResult(loadFresh(absorberId), absorbedId, employeesAfter - employeesBefore);
    }

    private OrganizationDto loadFresh(long id) {
        return DtoMapper.toDto(organizationRepository.findWithRelationsById(id)
                .orElseThrow(() -> NotFoundException.organization(id)));
    }

    private static void requireType(OrganizationType type) {
        if (type == null) {
            throw new InvalidInputException("type", "Выберите тип организации");
        }
    }
}
