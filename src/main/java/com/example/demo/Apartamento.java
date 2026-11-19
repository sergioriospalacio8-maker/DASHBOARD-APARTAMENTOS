package com.example.demo;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "apartamentos")
public class Apartamento {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "numero")
    private String numero;

    @Column(name = "torre")
    private String torre;

    @Column(name = "area")
    private double area;

    @Column(name = "propietario_id")
    private String propietarioId;

    public Apartamento() {
        this.id = UUID.randomUUID().toString();
    }

    public Apartamento(String id, String numero, String torre, double area, String propietarioId) {
        this.id = id;
        this.numero = numero;
        this.torre = torre;
        this.area = area;
        this.propietarioId = propietarioId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getTorre() { return torre; }
    public void setTorre(String torre) { this.torre = torre; }
    public double getArea() { return area; }
    public void setArea(double area) { this.area = area; }
    public String getPropietarioId() { return propietarioId; }
    public void setPropietarioId(String propietarioId) { this.propietarioId = propietarioId; }
}



