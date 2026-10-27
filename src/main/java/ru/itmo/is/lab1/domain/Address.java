package ru.itmo.is.lab1.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "address")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Почтовый индекс не может быть null")
    @Size(max = 255, message = "Почтовый индекс не длиннее 255 символов")
    @Column(name = "zip_code", nullable = false, length = 255)
    private String zipCode;

    protected Address() {
    }

    public Address(String zipCode) {
        this.zipCode = zipCode;
    }

    public Long getId() {
        return id;
    }

    public String getZipCode() {
        return zipCode;
    }
}
