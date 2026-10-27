package ru.itmo.is.lab1.domain;

/**
 * Порядок констант важен: операция "type меньше заданного" сравнивает типы
 * по порядку объявления (см. функцию БД organization_type_rank).
 */
public enum OrganizationType {
    GOVERNMENT,
    TRUST,
    PRIVATE_LIMITED_COMPANY
}
