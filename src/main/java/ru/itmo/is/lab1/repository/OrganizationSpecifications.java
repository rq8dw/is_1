package ru.itmo.is.lab1.repository;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.itmo.is.lab1.domain.Organization;
import ru.itmo.is.lab1.domain.OrganizationType;

import java.util.ArrayList;
import java.util.List;

/** Фильтры таблицы организаций: только полное совпадение значения. */
public final class OrganizationSpecifications {

    private OrganizationSpecifications() {
    }

    public static Specification<Organization> matching(String name,
                                                       String fullName,
                                                       OrganizationType type,
                                                       String officialZipCode,
                                                       String postalZipCode) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (name != null) {
                predicates.add(cb.equal(root.get("name"), name));
            }
            if (fullName != null) {
                predicates.add(cb.equal(root.get("fullName"), fullName));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (officialZipCode != null) {
                predicates.add(cb.equal(root.join("officialAddress", JoinType.INNER).get("zipCode"), officialZipCode));
            }
            if (postalZipCode != null) {
                predicates.add(cb.equal(root.join("postalAddress", JoinType.INNER).get("zipCode"), postalZipCode));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
