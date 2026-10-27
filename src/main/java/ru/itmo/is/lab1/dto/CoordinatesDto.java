package ru.itmo.is.lab1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @param usages сколько организаций ссылается на эти координаты (не передаётся во вложенных объектах)
 */
public record CoordinatesDto(Long id, double x, double y,
                             @JsonInclude(JsonInclude.Include.NON_NULL) Long usages) {
}
