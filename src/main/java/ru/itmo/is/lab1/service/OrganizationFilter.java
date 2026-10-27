package ru.itmo.is.lab1.service;

import ru.itmo.is.lab1.domain.OrganizationType;

/** Фильтр таблицы: null — без ограничения, иначе полное совпадение. */
public record OrganizationFilter(String name,
                                 String fullName,
                                 OrganizationType type,
                                 String officialZipCode,
                                 String postalZipCode) {
}
