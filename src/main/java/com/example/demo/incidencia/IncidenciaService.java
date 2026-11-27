package com.example.demo.incidencia;

import static jakarta.persistence.LockModeType.NONE;
import static jakarta.persistence.LockModeType.PESSIMISTIC_READ;
import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.Instant;
import java.util.List;

import com.example.demo.incidencia.Incidencia.Estado;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IncidenciaService {
    @PersistenceContext
    private EntityManager em;

    record Vista(Long id, Long apartamentoId, String titulo, String descripcion, Estado estado,
                 Instant creadaEn, Instant actualizadaEn, Instant resueltaEn) {}

    @Transactional
    public Vista crear(long apartamentoId, String titulo, String descripcion) {
        exigirApartamento(apartamentoId, PESSIMISTIC_READ);
        Incidencia incidencia = new Incidencia(apartamentoId, titulo, descripcion);
        em.persist(incidencia);
        return vista(incidencia);
    }

    @Transactional(readOnly = true)
    public Vista consultar(long id) { return vista(exigirIncidencia(id, NONE)); }

    @Transactional(readOnly = true)
    public List<Vista> listar(Long apartamentoId, Estado estado) {
        if (apartamentoId != null) exigirApartamento(apartamentoId, NONE);
        var consulta = em.createQuery("SELECT i FROM Incidencia i WHERE 1 = 1"
                + (apartamentoId == null ? "" : " AND i.apartamentoId = :apartamentoId")
                + (estado == null ? "" : " AND i.estado = :estado") + " ORDER BY i.id", Incidencia.class);
        if (apartamentoId != null) consulta.setParameter("apartamentoId", apartamentoId);
        if (estado != null) consulta.setParameter("estado", estado);
        return consulta.getResultList().stream().map(IncidenciaService::vista).toList();
    }

    @Transactional
    public Vista cambiarEstado(long id, Estado destino) {
        Incidencia incidencia = exigirIncidencia(id, PESSIMISTIC_WRITE);
        if (!incidencia.avanzarA(destino)) throw new ResponseStatusException(CONFLICT);
        return vista(incidencia);
    }

    private Incidencia exigirIncidencia(long id, LockModeType bloqueo) {
        Incidencia incidencia = em.find(Incidencia.class, id, bloqueo);
        if (incidencia == null) throw new ResponseStatusException(NOT_FOUND);
        return incidencia;
    }

    private void exigirApartamento(long id, LockModeType bloqueo) {
        // Sólo requiere la PK; con bloqueo protege el alta frente al borrado concurrente del apartamento.
        if (em.createQuery("SELECT a.id FROM Apartamento a WHERE a.id = :id", Long.class)
                .setParameter("id", id).setLockMode(bloqueo).getResultList().isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND);
        }
    }

    private static Vista vista(Incidencia i) {
        return new Vista(i.id, i.apartamentoId, i.titulo, i.descripcion, i.estado, i.creadaEn, i.actualizadaEn, i.resueltaEn);
    }
}
