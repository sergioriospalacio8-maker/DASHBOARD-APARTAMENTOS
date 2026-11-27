package com.example.demo.residente;

import static jakarta.persistence.LockModeType.PESSIMISTIC_READ;
import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResidenteService {
    private static final Pattern FECHA_ISO = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public List<Residente> listarResidentes() {
        return em.createQuery("SELECT r FROM Residente r ORDER BY r.id", Residente.class).getResultList();
    }

    @Transactional(readOnly = true)
    public Residente residente(Long id) {
        return existente(em.find(Residente.class, idValido(id)));
    }

    @Transactional
    public Residente crearResidente(String nombre) {
        Residente residente = new Residente(nombreValido(nombre));
        em.persist(residente);
        return residente;
    }

    @Transactional
    public Residente editarResidente(Long id, String nombre) {
        idValido(id);
        String limpio = nombreValido(nombre);
        Residente residente = existente(em.find(Residente.class, id));
        residente.nombre = limpio;
        return residente;
    }

    @Transactional(readOnly = true)
    public List<Ocupacion> listarOcupaciones(Long apartamentoId, Long residenteId) {
        String jpql = "SELECT o FROM Ocupacion o WHERE 1 = 1";
        if (apartamentoId != null) jpql += " AND o.apartamentoId = :apartamento";
        if (residenteId != null) jpql += " AND o.residenteId = :residente";
        var consulta = em.createQuery(jpql + " ORDER BY o.id", Ocupacion.class);
        if (apartamentoId != null) consulta.setParameter("apartamento", idValido(apartamentoId));
        if (residenteId != null) consulta.setParameter("residente", idValido(residenteId));
        return consulta.getResultList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Ocupacion iniciarOcupacion(Long apartamentoId, Long residenteId, String fecha) {
        idValido(apartamentoId);
        idValido(residenteId);
        LocalDate inicio = fechaValida(fecha);

        // Serializa las altas del mismo residente; la consulta posterior ve el último commit.
        existente(em.find(Residente.class, residenteId, PESSIMISTIC_WRITE));
        // Comprueba y protege sólo la PK: no carga columnas de Apartamento ni requiere la relación de T2.
        if (em.createQuery("SELECT a.id FROM Apartamento a WHERE a.id = :id", Long.class)
                .setParameter("id", apartamentoId).setLockMode(PESSIMISTIC_READ).getResultList().isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND);
        }
        if (!em.createQuery("""
                SELECT o.id FROM Ocupacion o WHERE o.apartamentoId = :apartamento
                    AND o.residenteId = :residente AND o.fechaFin IS NULL
                """, Long.class).setParameter("apartamento", apartamentoId)
                .setParameter("residente", residenteId).setMaxResults(1).getResultList().isEmpty()) {
            throw new ResponseStatusException(CONFLICT);
        }
        Ocupacion ocupacion = new Ocupacion(apartamentoId, residenteId, inicio);
        em.persist(ocupacion);
        return ocupacion;
    }

    @Transactional
    public Ocupacion cerrarOcupacion(Long id, String fecha) {
        idValido(id);
        LocalDate fin = fechaValida(fecha);
        Ocupacion ocupacion = existente(em.find(Ocupacion.class, id, PESSIMISTIC_WRITE));
        if (ocupacion.fechaFin != null) throw new ResponseStatusException(CONFLICT);
        if (fin.isBefore(ocupacion.fechaInicio)) throw new ResponseStatusException(BAD_REQUEST);
        ocupacion.fechaFin = fin;
        return ocupacion;
    }

    private static String nombreValido(String nombre) {
        if (nombre == null) throw new ResponseStatusException(BAD_REQUEST);
        String limpio = nombre.strip();
        if (limpio.isEmpty() || limpio.codePointCount(0, limpio.length()) > 120) {
            throw new ResponseStatusException(BAD_REQUEST);
        }
        return limpio;
    }

    private static LocalDate fechaValida(String fecha) {
        if (fecha == null || !FECHA_ISO.matcher(fecha).matches() || fecha.compareTo("1000-01-01") < 0) {
            throw new ResponseStatusException(BAD_REQUEST);
        }
        try {
            return LocalDate.parse(fecha);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(BAD_REQUEST);
        }
    }

    private static Long idValido(Long id) {
        if (id == null || id < 1) throw new ResponseStatusException(BAD_REQUEST);
        return id;
    }

    private static <T> T existente(T entidad) {
        if (entidad == null) throw new ResponseStatusException(NOT_FOUND);
        return entidad;
    }
}
