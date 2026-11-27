package com.example.demo.cuota;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pagos")
class Pago {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(name = "cuota_id", nullable = false)
    Long cuotaId;
    @Column(nullable = false, precision = 12, scale = 2)
    BigDecimal importe;
    @Column(nullable = false, length = 64)
    String referencia;

    protected Pago() {}

    Pago(Long cuotaId, BigDecimal importe, String referencia) {
        this.cuotaId = cuotaId;
        this.importe = importe;
        this.referencia = referencia;
    }
}
