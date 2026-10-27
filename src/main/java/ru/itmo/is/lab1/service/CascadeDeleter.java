package ru.itmo.is.lab1.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.is.lab1.domain.Organization;
import ru.itmo.is.lab1.realtime.ChangePublisher;
import ru.itmo.is.lab1.realtime.EntityChangedEvent.Change;
import ru.itmo.is.lab1.realtime.EntityChangedEvent.Entity;
import ru.itmo.is.lab1.repository.AddressRepository;
import ru.itmo.is.lab1.repository.CoordinatesRepository;
import ru.itmo.is.lab1.repository.OrganizationRepository;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Удаление организаций вместе со связанными объектами. Координаты и адреса
 * удаляются, если на них больше не ссылается ни одна организация: общий адрес
 * другой организации при этом не пропадает.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class CascadeDeleter {

    private final OrganizationRepository organizationRepository;
    private final CoordinatesRepository coordinatesRepository;
    private final AddressRepository addressRepository;
    private final ChangePublisher changePublisher;

    public CascadeDeleter(OrganizationRepository organizationRepository,
                          CoordinatesRepository coordinatesRepository,
                          AddressRepository addressRepository,
                          ChangePublisher changePublisher) {
        this.organizationRepository = organizationRepository;
        this.coordinatesRepository = coordinatesRepository;
        this.addressRepository = addressRepository;
        this.changePublisher = changePublisher;
    }

    public void deleteOrganizations(Collection<Organization> organizations) {
        if (organizations.isEmpty()) {
            return;
        }
        Set<Long> coordinatesIds = new LinkedHashSet<>();
        Set<Long> addressIds = new LinkedHashSet<>();
        List<Long> organizationIds = organizations.stream().map(Organization::getId).toList();
        for (Organization organization : organizations) {
            // getId() у ленивого прокси не инициализирует его
            coordinatesIds.add(organization.getCoordinates().getId());
            if (organization.getOfficialAddress() != null) {
                addressIds.add(organization.getOfficialAddress().getId());
            }
            if (organization.getPostalAddress() != null) {
                addressIds.add(organization.getPostalAddress().getId());
            }
        }

        organizationRepository.deleteAll(organizations);
        organizationRepository.flush();
        changePublisher.publish(Entity.ORGANIZATION, Change.DELETED, organizationIds);

        deleteUnusedRelatedObjects(coordinatesIds, addressIds);
    }

    /** Удаляет из переданных координат и адресов те, что больше никем не используются. */
    public void deleteUnusedRelatedObjects(Collection<Long> coordinatesIds, Collection<Long> addressIds) {
        List<Long> coordinatesCandidates = coordinatesIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!coordinatesCandidates.isEmpty()) {
            List<Long> unused = coordinatesRepository.findUnusedIds(coordinatesCandidates);
            if (!unused.isEmpty()) {
                coordinatesRepository.deleteAllByIdInBatch(unused);
                changePublisher.publish(Entity.COORDINATES, Change.DELETED, unused);
            }
        }

        List<Long> addressCandidates = addressIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!addressCandidates.isEmpty()) {
            List<Long> unused = addressRepository.findUnusedIds(addressCandidates);
            if (!unused.isEmpty()) {
                addressRepository.deleteAllByIdInBatch(unused);
                changePublisher.publish(Entity.ADDRESS, Change.DELETED, unused);
            }
        }
    }
}
