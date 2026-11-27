package com.example.demo.incidencia;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "incidencias_mantenimiento")
class Incidencia {
    enum Estado { ABIERTA, EN_PROGRESO, RESUELTA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "apartamento_id", nullable = false, updatable = false)
    Long apartamentoId;

    @Column(nullable = false, length = 120, updatable = false)
    String titulo;

    @Column(nullable = false, length = 2000, updatable = false)
    String descripcion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    Estado estado;

    @Column(name = "creada_en", nullable = false, updatable = false)
    Instant creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    Instant actualizadaEn;

    @Column(name = "resuelta_en")
    Instant resueltaEn;

    protected Incidencia() {}

    Incidencia(long apartamentoId, String titulo, String descripcion) {
        this.apartamentoId = apartamentoId;
        this.titulo = titulo;
        this.descripcion = descripcion;
        estado = Estado.ABIERTA;
        creadaEn = ahora();
        actualizadaEn = creadaEn;
    }

    boolean avanzarA(Estado destino) {
        if (!((estado == Estado.ABIERTA && destino == Estado.EN_PROGRESO)
                || (estado == Estado.EN_PROGRESO && destino == Estado.RESUELTA))) return false;

        estado = destino;
        Instant ahora = ahora();
        actualizadaEn = ahora.isAfter(actualizadaEn) ? ahora : actualizadaEn.plus(1, ChronoUnit.MICROS);
        if (destino == Estado.RESUELTA) resueltaEn = actualizadaEn;
        return true;
    }

    private static Instant ahora() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }
}
