package ru.itmo.is.lab1.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import ru.itmo.is.lab1.domain.Organization;
import ru.itmo.is.lab1.domain.OrganizationType;

import java.util.List;

/** Вызовы функций БД из V2__special_operations.sql. */
@Repository
public class SpecialOperationsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public long countByType(OrganizationType type) {
        return singleLong(entityManager
                .createNativeQuery("select count_organizations_by_type(cast(:type as varchar))")
                .setParameter("type", type.name())
                .getSingleResult());
    }

    public long countFullNameGreaterThan(String fullName) {
        return singleLong(entityManager
                .createNativeQuery("select count_organizations_full_name_greater(cast(:fullName as varchar))")
                .setParameter("fullName", fullName)
                .getSingleResult());
    }

    @SuppressWarnings("unchecked")
    public List<Organization> findTypeLessThan(OrganizationType type) {
        return entityManager
                .createNativeQuery("select * from find_organizations_type_less_than(cast(:type as varchar))",
                        Organization.class)
                .setParameter("type", type.name())
                .getResultList();
    }

    public long addEmployee(long organizationId) {
        return singleLong(entityManager
                .createNativeQuery("select add_organization_employee(cast(:id as bigint))")
                .setParameter("id", organizationId)
                .getSingleResult());
    }

    public long absorb(long absorberId, long absorbedId) {
        return singleLong(entityManager
                .createNativeQuery("select absorb_organization(cast(:absorber as bigint), cast(:absorbed as bigint))")
                .setParameter("absorber", absorberId)
                .setParameter("absorbed", absorbedId)
                .getSingleResult());
    }

    private static long singleLong(Object result) {
        return ((Number) result).longValue();
    }
}
