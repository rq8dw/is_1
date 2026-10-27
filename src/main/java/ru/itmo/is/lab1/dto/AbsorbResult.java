package ru.itmo.is.lab1.dto;

public record AbsorbResult(OrganizationDto absorber, long absorbedId, long transferredEmployees) {
}
