package com.example.demo.cuota;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;

import java.math.BigDecimal;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/cuotas")
public class CuotaController {
    private final CuotaService servicio;

    public CuotaController(CuotaService servicio) { this.servicio = servicio; }

    record Alta(BigDecimal apartamentoId, BigDecimal importe, String moneda) {}
    record Abono(BigDecimal importe, JsonNode referencia) {}

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
    public ResponseEntity<Void> entradaInvalida() { return ResponseEntity.badRequest().build(); }

    @PostMapping
    @ResponseStatus(CREATED)
    public CuotaService.VistaCuota crear(@RequestBody Alta alta) {
        BigDecimal id = alta.apartamentoId();
        if (id == null || id.compareTo(BigDecimal.ONE) < 0 || id.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            throw new ResponseStatusException(BAD_REQUEST);
        }
        long apartamentoId;
        try {
            apartamentoId = id.longValueExact();
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(BAD_REQUEST);
        }
        return servicio.crear(apartamentoId, alta.importe(), alta.moneda());
    }

    @GetMapping
    public List<CuotaService.VistaCuota> listar(@RequestParam(required = false) String apartamentoId) {
        if (apartamentoId == null) return servicio.listar(null);
        try {
            return servicio.listar(Long.parseLong(apartamentoId));
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(BAD_REQUEST);
        }
    }

    @GetMapping("/{id}")
    public CuotaService.VistaCuota consultar(@PathVariable long id) { return servicio.consultar(id); }

    @PostMapping("/{id}/pagos")
    public ResponseEntity<CuotaService.VistaPago> registrar(@PathVariable long id, @RequestBody Abono abono) {
        if (abono.referencia() == null || !abono.referencia().isString()) throw new ResponseStatusException(BAD_REQUEST);
        var resultado = servicio.registrar(id, abono.importe(), abono.referencia().stringValue());
        return resultado.nuevo() ? ResponseEntity.status(CREATED).body(resultado.pago()) : ResponseEntity.ok(resultado.pago());
    }

    @GetMapping("/{id}/pagos")
    public List<CuotaService.VistaPago> pagos(@PathVariable long id) { return servicio.pagos(id); }
}
