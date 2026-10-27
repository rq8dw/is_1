package ru.itmo.is.lab1.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;

@Entity
@Table(name = "coordinates")
public class Coordinates {

    public static final double MAX_X = 549;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Max(value = (long) MAX_X, message = "X не может быть больше 549")
    @Column(name = "x", nullable = false)
    private double x;

    @Column(name = "y", nullable = false)
    private double y;

    protected Coordinates() {
    }

    public Coordinates(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Long getId() {
        return id;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }
}
