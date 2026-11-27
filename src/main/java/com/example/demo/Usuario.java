package com.example.demo;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @Column(name = "id", length = 255)
    private String id;

    @Lob
    @Column(name = "nombre")
    private String nombre;

    @Lob
    @Column(name = "email")
    private String email;

    @Column(name = "edad", nullable = false)
    private int edad;

    public Usuario() {
        ensureId();
    }

    public Usuario(String nombre, String email, int edad) {
        this();
        this.nombre = nombre;
        this.email = email;
        this.edad = edad;
    }

    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    //Getters y Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getEdad() {
        return edad;
    }

    public void setEdad(int edad) {
        this.edad = edad;
    }
}
