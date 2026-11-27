package com.example.demo;

import java.util.List;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
@Tag(name = "Apartamentos", description = "API para la gestión de apartamentos")
@RestController
@RequestMapping("/api/apartamentos")
public class ApartamentoController {
    private final ApartamentoService apartamentoService;
    private final TransactionTemplate transaccionBorrado;

    @PersistenceContext
    private EntityManager entityManager;

    public ApartamentoController(ApartamentoService apartamentoService, PlatformTransactionManager transactionManager) {
        this.apartamentoService = apartamentoService;
        this.transaccionBorrado = new TransactionTemplate(transactionManager);
    }

    @Operation(summary = "Listar todos los apartamentos", description = "Devuelve la lista completa de apartamentos. Requiere rol de ADMIN.")
    @ApiResponse(responseCode = "200", description = "Lista obtenida con éxito")
    @ApiResponse(responseCode = "403", description = "Acceso denegado si no es ADMIN")
    @GetMapping
    public ResponseEntity<List<Apartamento>> listar(@RequestHeader(value = "X-User-Role", required = false) @Parameter(description = "Rol de usuario para  validar permisos") String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Apartamento> apartamentos = entityManager.createQuery("SELECT a FROM Apartamento a", Apartamento.class).getResultList();
        return ResponseEntity.ok(apartamentos);

    }

    @Operation(summary = "Consultar apartamento propio", description = "Busca y devuelve el apartamento asociado al ID del propietario enviado en la cabecera.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Apartamento encontrado"),
            @ApiResponse(responseCode = "404", description = "Apartamento no encontrado"),
            @ApiResponse(responseCode = "401", description = "No autorizado si falta el ID")
    })
    @GetMapping("/mio")
    public ResponseEntity<Apartamento> consultarPropio(
            @RequestHeader(value = "X-User-Id", required = false) @Parameter(description = "ID único del propietario") String propietarioId) {
        if (propietarioId == null || propietarioId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<Apartamento> resultados = entityManager.createQuery("SELECT a FROM Apartamento a WHERE a.propietarioId = :propietarioId", Apartamento.class)
                .setParameter("propietarioId", propietarioId)
                .setMaxResults(1)
                .getResultList();
        Apartamento apartamento = resultados.isEmpty() ? null : resultados.get(0);
        return apartamento == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(apartamento);
    }

    @Operation(summary = "Registrar un apartamento", description = "Crea un nuevo registro de apartamento. Requiere rol de ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Apartamento creado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado si no es ADMIN")
    })
    @PostMapping
    @Transactional
    public ResponseEntity<Apartamento> registrar(@RequestHeader(value = "X-User-Role", required = false) @Parameter(description = "Rol del usuario")String role,
                                                 @RequestBody @Parameter(description = "Datos del apartamento a registrar") Apartamento apartamento) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        entityManager.persist(apartamento);
        return ResponseEntity.status(HttpStatus.CREATED).body(apartamento);
    }

    @Operation(summary = "Actualizar un apartamento", description = "Modifica los datos de un apartamento existente. Requiere rol de ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Apartamento actualizado con éxito"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado si no es ADMIN"),
            @ApiResponse(responseCode = "404", description = "Apartamento no encontrado")
    })

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Apartamento> actualizar(
            @RequestHeader(value = "X-User-Role", required = false) @Parameter(description = "Rol del usuario") String role,
            @PathVariable @Parameter(description = "ID del apartamento a actualizar") Long id,
            @RequestBody @Parameter(description = "Nuevos datos del apartamento") Apartamento apartamentoDetalles) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Apartamento existente = entityManager.find(Apartamento.class, id);
        Apartamento actualizado = null;
        if (existente != null) {
            existente.setNumero(apartamentoDetalles.getNumero());
            existente.setTorre(apartamentoDetalles.getTorre());
            existente.setArea(apartamentoDetalles.getArea());
            existente.setPropietarioId(apartamentoDetalles.getPropietarioId());
            actualizado = entityManager.merge(existente);
        }
        return actualizado != null ? ResponseEntity.ok(actualizado) : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Eliminar un apartamento", description = "Elimina un apartamento del sistema por su ID. Requiere rol de ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Apartamento eliminado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado si no es ADMIN"),
            @ApiResponse(responseCode = "404", description = "Apartamento no encontrado"),
            @ApiResponse(responseCode = "409", description = "El apartamento tiene historial de ocupación, cuotas o incidencias de mantenimiento; no se elimina",
                    content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @RequestHeader(value = "X-User-Role", required = false) @Parameter(description = "Rol del usuario") String role,
            @PathVariable @Parameter(description = "ID del apartamento a eliminar") Long id) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            // El DELETE puede fallar al confirmar, no al llamar a remove().
            boolean eliminado = Boolean.TRUE.equals(transaccionBorrado.execute(estado -> {
                Apartamento existente = entityManager.find(Apartamento.class, id);
                if (existente == null) return false;
                entityManager.remove(existente);
                return true;
            }));
            return eliminado ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
        } catch (RuntimeException ex) {
            if (conflictoConHistorial(ex)) return ResponseEntity.status(HttpStatus.CONFLICT).build();
            throw ex;
        }
    }

    private static boolean conflictoConHistorial(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException violacion
                    && ("fk_ocupaciones_apartamento".equalsIgnoreCase(violacion.getConstraintName())
                    || "fk_cuotas_apartamento".equalsIgnoreCase(violacion.getConstraintName())
                    || "fk_incidencias_apartamento".equalsIgnoreCase(violacion.getConstraintName()))) {
                String sqlState = violacion.getSQLState();
                int codigo = violacion.getErrorCode();
                // MySQL: fila padre referenciada (1451); H2: hay filas hijas (23503).
                if ((codigo == 1451 && "23000".equals(sqlState))
                        || (codigo == 23503 && "23503".equals(sqlState))) return true;
            }
        }
        return false;
    }

    }
