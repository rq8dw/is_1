package ru.itmo.is.lab1.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.itmo.is.lab1.domain.Organization;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long>, JpaSpecificationExecutor<Organization> {

    @Override
    @EntityGraph(attributePaths = {"coordinates", "officialAddress", "postalAddress"})
    Page<Organization> findAll(Specification<Organization> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"coordinates", "officialAddress", "postalAddress"})
    Optional<Organization> findWithRelationsById(long id);

    @Query("select o from Organization o where o.coordinates.id = :coordinatesId")
    List<Organization> findAllByCoordinatesId(@Param("coordinatesId") long coordinatesId);

    @Query("""
            select o from Organization o
            where o.officialAddress.id = :addressId or o.postalAddress.id = :addressId
            """)
    List<Organization> findAllByAddressId(@Param("addressId") long addressId);

    /** Идентификаторы связанных объектов без загрузки самой организации. */
    @Query("""
            select c.id as coordinatesId, oa.id as officialAddressId, pa.id as postalAddressId
            from Organization o
                join o.coordinates c
                left join o.officialAddress oa
                left join o.postalAddress pa
            where o.id = :id
            """)
    Optional<RelationIds> findRelationIds(@Param("id") long id);

    @Query("select o.employeesCount from Organization o where o.id = :id")
    Optional<Long> findEmployeesCount(@Param("id") long id);

    @Query("select o.coordinates.id, count(o) from Organization o group by o.coordinates.id")
    List<Object[]> countUsagesByCoordinates();

    @Query("""
            select a.id, count(o) from Address a, Organization o
            where o.officialAddress = a or o.postalAddress = a
            group by a.id
            """)
    List<Object[]> countUsagesByAddress();

    interface RelationIds {
        Long getCoordinatesId();

        Long getOfficialAddressId();

        Long getPostalAddressId();
    }
}
