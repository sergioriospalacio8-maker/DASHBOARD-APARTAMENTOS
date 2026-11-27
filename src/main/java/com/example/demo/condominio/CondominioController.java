package com.example.demo.condominio;

import static jakarta.persistence.LockModeType.PESSIMISTIC_READ;
import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;

import com.example.demo.Apartamento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@Transactional
public class CondominioController {
    @PersistenceContext
    private EntityManager em;

    record Nombre(String nombre) {}
    record DatosTorre(String nombre, Long condominioId) {}
    record VistaCondominio(Long id, String nombre) {}
    record VistaTorre(Long id, String nombre, Long condominioId) {}
    record Enlace(Long torreId) {}

    @ModelAttribute
    public void exigirAdmin(HttpServletRequest solicitud) {
        if (solicitud.getUserPrincipal() == null || !solicitud.isUserInRole("ADMIN")) {
            throw new ResponseStatusException(FORBIDDEN);
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Void> error(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).build();
    }

    @GetMapping("/condominios")
    public List<VistaCondominio> condominios() {
        return em.createQuery("SELECT c FROM Condominio c ORDER BY c.id", Condominio.class)
                .getResultStream().map(CondominioController::vista).toList();
    }

    @GetMapping("/condominios/{id}")
    public VistaCondominio condominio(@PathVariable Long id) {
        return vista(exigirExistente(em.find(Condominio.class, id)));
    }

    @PostMapping("/condominios")
    @ResponseStatus(CREATED)
    public VistaCondominio crearCondominio(@RequestBody Nombre datos) {
        Condominio condominio = new Condominio();
        condominio.nombre = nombreValido(datos.nombre());
        em.persist(condominio);
        return vista(condominio);
    }

    @PutMapping("/condominios/{id}")
    public VistaCondominio actualizarCondominio(@PathVariable Long id, @RequestBody Nombre datos) {
        Condominio condominio = exigirExistente(em.find(Condominio.class, id));
        condominio.nombre = nombreValido(datos.nombre());
        return vista(condominio);
    }

    @DeleteMapping("/condominios/{id}")
    @ResponseStatus(NO_CONTENT)
    public void eliminarCondominio(@PathVariable Long id) {
        Condominio condominio = exigirExistente(em.find(Condominio.class, id, PESSIMISTIC_WRITE));
        long torres = em.createQuery("SELECT COUNT(t) FROM Torre t WHERE t.condominioId = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        if (torres != 0) throw new ResponseStatusException(CONFLICT);
        em.remove(condominio);
    }

    @GetMapping("/torres")
    public List<VistaTorre> torres() {
        return em.createQuery("SELECT t FROM Torre t ORDER BY t.id", Torre.class)
                .getResultStream().map(CondominioController::vista).toList();
    }

    @GetMapping("/torres/{id}")
    public VistaTorre torre(@PathVariable Long id) {
        return vista(exigirExistente(em.find(Torre.class, id)));
    }

    @PostMapping("/torres")
    @ResponseStatus(CREATED)
    public VistaTorre crearTorre(@RequestBody DatosTorre datos) {
        Torre torre = new Torre();
        cargarTorre(torre, datos);
        em.persist(torre);
        return vista(torre);
    }

    @PutMapping("/torres/{id}")
    public VistaTorre actualizarTorre(@PathVariable Long id, @RequestBody DatosTorre datos) {
        Torre torre = exigirExistente(em.find(Torre.class, id));
        cargarTorre(torre, datos);
        return vista(torre);
    }

    @DeleteMapping("/torres/{id}")
    @ResponseStatus(NO_CONTENT)
    public void eliminarTorre(@PathVariable Long id) {
        em.remove(exigirExistente(em.find(Torre.class, id, PESSIMISTIC_WRITE)));
    }

    @GetMapping("/apartamentos/{id}/torre")
    public Enlace enlace(@PathVariable Long id) {
        return new Enlace(exigirExistente(em.find(Apartamento.class, id)).getTorreId());
    }

    @PutMapping("/apartamentos/{id}/torre")
    public Enlace asignarTorre(@PathVariable Long id, @RequestBody Enlace enlace) {
        Apartamento apartamento = exigirExistente(em.find(Apartamento.class, id));
        if (enlace.torreId() != null) exigirExistente(em.find(Torre.class, enlace.torreId(), PESSIMISTIC_READ));
        apartamento.setTorreId(enlace.torreId());
        return enlace;
    }

    private void cargarTorre(Torre torre, DatosTorre datos) {
        String nombre = nombreValido(datos.nombre());
        if (datos.condominioId() == null) throw new ResponseStatusException(BAD_REQUEST);
        exigirExistente(em.find(Condominio.class, datos.condominioId(), PESSIMISTIC_READ));
        torre.nombre = nombre;
        torre.condominioId = datos.condominioId();
    }

    private static String nombreValido(String nombre) {
        if (nombre == null) throw new ResponseStatusException(BAD_REQUEST);
        String limpio = nombre.strip();
        if (limpio.isEmpty() || limpio.codePointCount(0, limpio.length()) > 120) {
            throw new ResponseStatusException(BAD_REQUEST);
        }
        return limpio;
    }

    private static <T> T exigirExistente(T entidad) {
        if (entidad == null) throw new ResponseStatusException(NOT_FOUND);
        return entidad;
    }

    private static VistaCondominio vista(Condominio condominio) {
        return new VistaCondominio(condominio.id, condominio.nombre);
    }

    private static VistaTorre vista(Torre torre) {
        return new VistaTorre(torre.id, torre.nombre, torre.condominioId);
    }
}
