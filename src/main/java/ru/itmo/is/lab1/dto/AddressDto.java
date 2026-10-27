package ru.itmo.is.lab1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @param usages сколько организаций ссылается на этот адрес (не передаётся во вложенных объектах)
 */
public record AddressDto(Long id, String zipCode,
                         @JsonInclude(JsonInclude.Include.NON_NULL) Long usages) {
}
