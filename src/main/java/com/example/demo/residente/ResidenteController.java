package com.example.demo.residente;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ResidenteController {
    private final ResidenteService servicio;

    public ResidenteController(ResidenteService servicio) {
        this.servicio = servicio;
    }

    record Ficha(String nombre) {}
    record Inicio(BigDecimal apartamentoId, BigDecimal residenteId, String fechaInicio) {}
    record Cierre(String fechaFin) {}
    record VistaResidente(Long id, String nombre) {}
    record VistaOcupacion(Long id, Long apartamentoId, Long residenteId, LocalDate fechaInicio, LocalDate fechaFin) {}

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

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Void> entradaInvalida() {
        return ResponseEntity.status(BAD_REQUEST).build();
    }

    @GetMapping("/residentes")
    public List<VistaResidente> residentes() {
        return servicio.listarResidentes().stream().map(ResidenteController::vista).toList();
    }

    @GetMapping("/residentes/{id}")
    public VistaResidente residente(@PathVariable Long id) {
        return vista(servicio.residente(id));
    }

    @PostMapping("/residentes")
    @ResponseStatus(CREATED)
    public VistaResidente crearResidente(@RequestBody Ficha datos) {
        return vista(servicio.crearResidente(datos.nombre()));
    }

    @PutMapping("/residentes/{id}")
    public VistaResidente editarResidente(@PathVariable Long id, @RequestBody Ficha datos) {
        return vista(servicio.editarResidente(id, datos.nombre()));
    }

    @GetMapping("/ocupaciones")
    public List<VistaOcupacion> ocupaciones(@RequestParam(required = false) Long apartamentoId,
                                           @RequestParam(required = false) Long residenteId) {
        return servicio.listarOcupaciones(apartamentoId, residenteId).stream().map(ResidenteController::vista).toList();
    }

    @PostMapping("/ocupaciones")
    @ResponseStatus(CREATED)
    public VistaOcupacion iniciarOcupacion(@RequestBody Inicio datos) {
        return vista(servicio.iniciarOcupacion(idExacto(datos.apartamentoId()), idExacto(datos.residenteId()),
                datos.fechaInicio()));
    }

    @PatchMapping("/ocupaciones/{id}/cierre")
    public VistaOcupacion cerrarOcupacion(@PathVariable Long id, @RequestBody Cierre datos) {
        return vista(servicio.cerrarOcupacion(id, datos.fechaFin()));
    }

    private static Long idExacto(BigDecimal id) {
        if (id == null) throw new ResponseStatusException(BAD_REQUEST);
        try {
            return id.longValueExact();
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(BAD_REQUEST);
        }
    }

    private static VistaResidente vista(Residente residente) {
        return new VistaResidente(residente.id, residente.nombre);
    }

    private static VistaOcupacion vista(Ocupacion ocupacion) {
        return new VistaOcupacion(ocupacion.id, ocupacion.apartamentoId, ocupacion.residenteId,
                ocupacion.fechaInicio, ocupacion.fechaFin);
    }
}
