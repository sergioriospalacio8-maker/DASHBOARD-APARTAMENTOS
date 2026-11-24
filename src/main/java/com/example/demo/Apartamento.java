package com.example.demo;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "apartamentos")
public class Apartamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "numero")
    private String numero;

    @Column(name = "torre")
    private String torre;

    @Column(name = "area")
    private Double area;

    @Column(name = "propietario_id")
    private String propietarioId;

    public Apartamento() {

    }

    public Apartamento(Long id, String numero, String torre, Double area, String propietarioId) {
        this.id = id;
        this.numero = numero;
        this.torre = torre;
        this.area = area;
        this.propietarioId = propietarioId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getTorre() { return torre; }
    public void setTorre(String torre) { this.torre = torre; }
    public Double getArea() { return area; }
    public void setArea(Double area) { this.area = area; }
    public String getPropietarioId() { return propietarioId; }
    public void setPropietarioId(String propietarioId) { this.propietarioId = propietarioId; }
}



