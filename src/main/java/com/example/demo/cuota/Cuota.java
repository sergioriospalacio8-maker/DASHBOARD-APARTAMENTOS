package com.example.demo.cuota;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cuotas")
class Cuota {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(name = "apartamento_id", nullable = false)
    Long apartamentoId;
    @Column(nullable = false, precision = 12, scale = 2)
    BigDecimal importe;
    @Column(nullable = false, length = 3)
    String moneda;

    protected Cuota() {}

    Cuota(Long apartamentoId, BigDecimal importe, String moneda) {
        this.apartamentoId = apartamentoId;
        this.importe = importe;
        this.moneda = moneda;
    }
}
