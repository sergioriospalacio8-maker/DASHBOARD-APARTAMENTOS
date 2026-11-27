package com.example.demo.residente;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ocupaciones")
class Ocupacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "apartamento_id", nullable = false, updatable = false)
    Long apartamentoId;

    @Column(name = "residente_id", nullable = false, updatable = false)
    Long residenteId;

    @Column(name = "fecha_inicio", nullable = false, updatable = false)
    LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    LocalDate fechaFin;

    protected Ocupacion() {}

    Ocupacion(Long apartamentoId, Long residenteId, LocalDate fechaInicio) {
        this.apartamentoId = apartamentoId;
        this.residenteId = residenteId;
        this.fechaInicio = fechaInicio;
    }
}
