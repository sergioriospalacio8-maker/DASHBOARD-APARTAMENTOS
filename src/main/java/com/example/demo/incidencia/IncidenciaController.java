package com.example.demo.incidencia;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;

import java.util.List;

import com.example.demo.incidencia.Incidencia.Estado;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/incidencias")
public class IncidenciaController {
    private final IncidenciaService servicio;

    public IncidenciaController(IncidenciaService servicio) { this.servicio = servicio; }

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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Void> jsonInvalido() { return ResponseEntity.badRequest().build(); }

    @PostMapping
    @ResponseStatus(CREATED)
    public IncidenciaService.Vista crear(@RequestBody JsonNode alta) {
        if (!alta.isObject() || alta.size() != 3) throw new ResponseStatusException(BAD_REQUEST);
        JsonNode id = alta.path("apartamentoId");
        if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() < 1) {
            throw new ResponseStatusException(BAD_REQUEST);
        }
        return servicio.crear(id.longValue(), texto(alta.path("titulo"), 120), texto(alta.path("descripcion"), 2000));
    }

    @GetMapping
    public List<IncidenciaService.Vista> listar(@RequestParam(required = false) String apartamentoId,
                                               @RequestParam(required = false) String estado) {
        return servicio.listar(apartamentoId == null ? null : idValido(apartamentoId),
                estado == null ? null : estadoValido(estado));
    }

    @GetMapping("/{id}")
    public IncidenciaService.Vista consultar(@PathVariable String id) { return servicio.consultar(idValido(id)); }

    @PatchMapping("/{id}/estado")
    public IncidenciaService.Vista cambiarEstado(@PathVariable String id, @RequestBody JsonNode cambio) {
        if (!cambio.isObject() || cambio.size() != 1 || !cambio.path("estado").isString()) {
            throw new ResponseStatusException(BAD_REQUEST);
        }
        return servicio.cambiarEstado(idValido(id), estadoValido(cambio.path("estado").stringValue()));
    }

    private static long idValido(String valor) {
        try {
            long id = Long.parseLong(valor);
            if (id > 0) return id;
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(BAD_REQUEST);
        }
        throw new ResponseStatusException(BAD_REQUEST);
    }

    private static String texto(JsonNode nodo, int maximo) {
        if (!nodo.isString()) throw new ResponseStatusException(BAD_REQUEST);
        String valor = nodo.stringValue().strip();
        if (valor.isEmpty() || valor.length() > maximo) throw new ResponseStatusException(BAD_REQUEST);
        return valor;
    }

    private static Estado estadoValido(String valor) {
        try {
            return Estado.valueOf(valor);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST);
        }
    }
}
