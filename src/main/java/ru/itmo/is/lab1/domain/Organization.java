package ru.itmo.is.lab1.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.ZonedDateTime;

@Entity
@Table(name = "organization")
public class Organization {

    // Генерируется БД (identity); положительность гарантирует CHECK (id > 0)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @NotBlank(message = "Название не может быть пустым")
    @Size(max = 255, message = "Название не длиннее 255 символов")
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @NotNull(message = "Координаты обязательны")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coordinates_id", nullable = false)
    private Coordinates coordinates;

    @NotNull
    @Column(name = "creation_date", nullable = false, updatable = false)
    private ZonedDateTime creationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "official_address_id")
    private Address officialAddress;

    @Positive(message = "Годовой оборот должен быть больше 0")
    @Column(name = "annual_turnover")
    private Long annualTurnover;

    @Positive(message = "Количество сотрудников должно быть больше 0")
    @Column(name = "employees_count", nullable = false)
    private long employeesCount;

    @Positive(message = "Рейтинг должен быть больше 0")
    @Column(name = "rating")
    private Float rating;

    @NotNull(message = "Полное название не может быть null")
    @Size(max = 255, message = "Полное название не длиннее 255 символов")
    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @NotNull(message = "Тип организации обязателен")
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private OrganizationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "postal_address_id")
    private Address postalAddress;

    @PrePersist
    void onCreate() {
        if (creationDate == null) {
            creationDate = ZonedDateTime.now();
        }
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Coordinates getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(Coordinates coordinates) {
        this.coordinates = coordinates;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public Address getOfficialAddress() {
        return officialAddress;
    }

    public void setOfficialAddress(Address officialAddress) {
        this.officialAddress = officialAddress;
    }

    public Long getAnnualTurnover() {
        return annualTurnover;
    }

    public void setAnnualTurnover(Long annualTurnover) {
        this.annualTurnover = annualTurnover;
    }

    public long getEmployeesCount() {
        return employeesCount;
    }

    public void setEmployeesCount(long employeesCount) {
        this.employeesCount = employeesCount;
    }

    public Float getRating() {
        return rating;
    }

    public void setRating(Float rating) {
        this.rating = rating;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public OrganizationType getType() {
        return type;
    }

    public void setType(OrganizationType type) {
        this.type = type;
    }

    public Address getPostalAddress() {
        return postalAddress;
    }

    public void setPostalAddress(Address postalAddress) {
        this.postalAddress = postalAddress;
    }
}
