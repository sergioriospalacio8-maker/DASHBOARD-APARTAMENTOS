package com.example.demo.incidencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.json.JsonCompareMode.STRICT;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.example.demo.ApartamentoController;
import com.example.demo.ApartamentoRepository;
import com.example.demo.ApartamentoService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.test.json.JsonAssert;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = IncidenciaControllerTests.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        args = "--spring.config.location=optional:classpath:/incidencia-test-only.properties",
        properties = {"server.address=127.0.0.1", "spring.datasource.url=jdbc:h2:mem:incidencia;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
                "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=none", "spring.jpa.open-in-view=false",
                "spring.data.jpa.repositories.enabled=false", "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:incidencia-schema.sql"})
class IncidenciaControllerTests {
    private static final String INC = "/api/incidencias";
    private static final String APT = "/api/apartamentos";
    private static final String TITULO = "Fuga";
    private static final String DESCRIPCION = "Goteo en la cocina";
    private static final long INEXISTENTE = Long.MAX_VALUE;
    private final HttpClient cliente = HttpClient.newHttpClient();
    @Value("${local.server.port}") private int puerto;

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "com.example.demo")
    @Import({ApartamentoController.class, ApartamentoService.class, ApartamentoRepository.class})
    @ComponentScan(useDefaultFilters = false, includeFilters = @ComponentScan.Filter(
            type = FilterType.ANNOTATION, classes = {RestController.class, Service.class}))
    static class App {
        @Bean
        Filter autenticacionSoloDePrueba() {
            return (request, response, chain) -> {
                var http = (HttpServletRequest) request;
                String sesion = http.getHeader("X-Test-Session");
                chain.doFilter(new HttpServletRequestWrapper(http) {
                    @Override public Principal getUserPrincipal() {
                        return "admin".equals(sesion) || "sinRol".equals(sesion) ? () -> "prueba" : null;
                    }
                    @Override public boolean isUserInRole(String rol) {
                        return "ADMIN".equals(rol) && ("admin".equals(sesion) || "sinPrincipal".equals(sesion));
                    }
                }, response);
            };
        }
    }

    @Test
    void exigePrincipalYRolDelServidorAntesDeProcesarDatos() throws Exception {
        long apt = apartamento();
        String original = crear(apt, TITULO, DESCRIPCION);
        long id = id(original);
        for (String sesion : new String[]{null, "sinRol", "sinPrincipal"}) {
            cuerpo("", enviar("GET", INC, null, sesion, "ADMIN", 403));
            cuerpo("", enviar("GET", INC + "/" + id, null, sesion, "ADMIN", 403));
            cuerpo("", enviar("POST", INC, alta(apt, TITULO, DESCRIPCION), sesion, "ADMIN", 403));
            cuerpo("", enviar("POST", INC, "{", sesion, "ADMIN", 403));
            cuerpo("", enviar("PATCH", estadoDe(id), estado("EN_PROGRESO"), sesion, "ADMIN", 403));
            cuerpo("", enviar("PATCH", estadoDe(id), "{", sesion, "ADMIN", 403));
        }
        admin("GET", INC + "?apartamentoId=" + apt, null, 200, "[" + original + "]");
        admin("GET", INC + "/" + id, null, 200, original);
        assertEquals(id + 1, id(crear(apt, TITULO, DESCRIPCION)));
    }

    @Test
    void creaConsultaYCombinaFiltrosConOrdenEstable() throws Exception {
        long apt = apartamento(), otro = apartamento();
        admin("GET", INC + "?apartamentoId=" + apt, null, 200, "[]");
        String primera = crear(apt, "  " + TITULO + " ", " " + DESCRIPCION + "  ");
        String segunda = crear(apt, "x".repeat(120), "a".repeat(2000));
        String tercera = crear(otro, TITULO, DESCRIPCION);
        String progreso = avanzar(primera, "EN_PROGRESO");
        admin("GET", INC + "/" + id(progreso), null, 200, progreso);
        admin("GET", INC + "?apartamentoId=" + apt, null, 200, lista(progreso, segunda));
        admin("GET", INC + "?estado=ABIERTA&apartamentoId=" + apt, null, 200, lista(segunda));
        admin("GET", INC + "?estado=EN_PROGRESO&apartamentoId=" + apt, null, 200, lista(progreso));
        admin("GET", INC + "?estado=EN_PROGRESO&apartamentoId=" + otro, null, 200, "[]");
        admin("GET", INC + "?estado=RESUELTA&apartamentoId=" + apt, null, 200, "[]");
        compararDesde(id(primera), lista(segunda, tercera), INC + "?estado=ABIERTA");
        compararDesde(id(primera), lista(progreso, segunda, tercera), INC);
    }

    @Test
    void rechazaEntradasInvalidasYReferenciasAusentesSinCambiosNiConsumoDeIds() throws Exception {
        long apt = apartamento();
        String original = crear(apt, TITULO, DESCRIPCION);
        long id = id(original);
        for (String invalido : List.of("{}", "{", "[]", "null", "", alta(apt, " ", DESCRIPCION),
                alta(apt, TITULO, ""), alta(apt, "x".repeat(121), DESCRIPCION),
                alta(apt, TITULO, "a".repeat(2001)),
                alta(Long.toString(apt), "1", "true"), alta(Long.toString(apt), "null", "\"texto\""),
                alta(apt, TITULO, DESCRIPCION).replace("}", ",\"estado\":\"RESUELTA\"}"))) {
            admin("POST", INC, invalido, 400, "");
        }
        for (String invalido : List.of("0", "-1", "1.2", "9223372036854775808", "null", "\"1\"")) {
            admin("POST", INC, alta(invalido, "\"Fuga\"", "\"Goteo\""), 400, "");
        }
        for (String invalido : List.of("0", "-1", "1.2", "9223372036854775808", "abc")) {
            admin("GET", INC + "/" + invalido, null, 400, "");
            admin("GET", INC + "?apartamentoId=" + invalido, null, 400, "");
            admin("PATCH", INC + "/" + invalido + "/estado", estado("EN_PROGRESO"), 400, "");
        }
        admin("GET", INC + "?apartamentoId=", null, 400, "");
        for (String invalido : List.of("", "abierta", "NUEVA")) {
            admin("GET", INC + "?estado=" + invalido, null, 400, "");
        }
        for (String invalido : List.of("{", "{}", "{\"estado\":null}", "{\"estado\":0}", estado(""),
                estado("en_progreso"), estado("NUEVA"), "{\"estado\":\"EN_PROGRESO\",\"titulo\":\"Nuevo\"}")) {
            admin("PATCH", estadoDe(id), invalido, 400, "");
        }
        admin("POST", INC, alta(INEXISTENTE, TITULO, DESCRIPCION), 404, "");
        admin("GET", INC + "?apartamentoId=" + INEXISTENTE, null, 404, "");
        admin("GET", INC + "/" + INEXISTENTE, null, 404, "");
        admin("PATCH", estadoDe(INEXISTENTE), estado("EN_PROGRESO"), 404, "");
        admin("GET", INC + "?apartamentoId=" + apt, null, 200, lista(original));
        admin("GET", INC + "/" + id, null, 200, original);
        assertEquals(id + 1, id(crear(apt, TITULO, DESCRIPCION)));
    }

    @Test
    void serializaTransicionesYConservaElRegistroResuelto() throws Exception {
        long apt = apartamento();
        String abierta = crear(apt, TITULO, DESCRIPCION);
        long id = id(abierta);
        String ruta = INC + "/" + id, cambio = estadoDe(id);
        for (String destino : List.of("ABIERTA", "RESUELTA")) admin("PATCH", cambio, estado(destino), 409, "");
        admin("GET", ruta, null, 200, abierta);

        var uno = cliente.sendAsync(solicitud("PATCH", cambio, estado("EN_PROGRESO"), "admin", null), HttpResponse.BodyHandlers.ofString());
        var dos = cliente.sendAsync(solicitud("PATCH", cambio, estado("EN_PROGRESO"), "admin", null), HttpResponse.BodyHandlers.ofString());
        var respuestas = List.of(uno.get(15, TimeUnit.SECONDS), dos.get(15, TimeUnit.SECONDS)).stream()
                .sorted(Comparator.comparingInt(HttpResponse::statusCode)).toList();
        assertEquals(List.of(200, 409), respuestas.stream().map(HttpResponse::statusCode).toList());
        cuerpo("", respuestas.get(1).body());
        String progreso = respuestas.get(0).body();
        comprobarCambio(abierta, progreso, "EN_PROGRESO");
        admin("PATCH", cambio, estado("ABIERTA"), 409, "");
        admin("GET", ruta, null, 200, progreso);

        String resuelta = avanzar(progreso, "RESUELTA");
        for (String destino : List.of("ABIERTA", "EN_PROGRESO", "RESUELTA")) admin("PATCH", cambio, estado(destino), 409, "");
        enviar("DELETE", ruta, null, "admin", null, 405);
        admin("GET", ruta, null, 200, resuelta);
        admin("GET", INC + "?apartamentoId=" + apt + "&estado=RESUELTA", null, 200, lista(resuelta));
        assertEquals(id + 1, id(crear(apt, TITULO, DESCRIPCION)));
    }

    @Test
    void conservaElApartamentoYLaIncidenciaResueltaAlRechazarElBorrado() throws Exception {
        String apartamento = enviar("POST", APT, "{\"numero\":\"103\",\"area\":52.5}", null, "ADMIN", 201);
        long apt = id(apartamento);
        String abierta = crear(apt, TITULO, DESCRIPCION);
        String resuelta = avanzar(avanzar(abierta, "EN_PROGRESO"), "RESUELTA");

        cuerpo("", enviar("DELETE", APT + "/" + apt, null, null, "ADMIN", 409));
        String listado = enviar("GET", APT, null, null, "ADMIN", 200);
        cuerpo(lista(apartamento), JsonPath.parse(listado).read("$[?(@.id == " + apt + ")]").toString());
        admin("GET", INC + "/" + id(resuelta), null, 200, resuelta);
        admin("GET", INC + "?apartamentoId=" + apt, null, 200, lista(resuelta));
    }

    @Test
    void preservaContratoHttp() throws Exception {
        String nuevo = enviar("POST", APT, "{\"numero\":\"101\",\"propietarioId\":\"legacy\"}", null, "ADMIN", 201);
        long apt = id(nuevo);
        String original = "{\"id\":%d,\"numero\":\"101\",\"torre\":null,\"area\":null,\"propietarioId\":\"legacy\"}".formatted(apt);
        cuerpo(original, nuevo);
        var propia = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + APT + "/mio"))
                .timeout(Duration.ofSeconds(10)).header("X-User-Id", "legacy").GET().build();
        var respuesta = cliente.send(propia, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, respuesta.statusCode());
        cuerpo(original, respuesta.body());
        cuerpo("", enviar("DELETE", APT + "/" + apt, null, "admin", "RESIDENTE", 403));
        cuerpo("", enviar("DELETE", APT + "/" + apt, null, null, "ADMIN", 204));
        cuerpo("", enviar("DELETE", APT + "/" + apt, null, null, "ADMIN", 404));
    }

    private long apartamento() throws Exception {
        return id(enviar("POST", APT, "{\"numero\":\"101\"}", null, "ADMIN", 201));
    }

    private String crear(long apt, String titulo, String descripcion) throws Exception {
        String actual = enviar("POST", INC, alta(apt, titulo, descripcion), "admin", null, 201);
        String creada = JsonPath.read(actual, "$.creadaEn");
        Instant.parse(creada);
        cuerpo(vista(id(actual), apt, titulo.strip(), descripcion.strip(), "ABIERTA", creada, creada, null), actual);
        return actual;
    }

    private String avanzar(String anterior, String estadoNuevo) throws Exception {
        String actual = enviar("PATCH", estadoDe(id(anterior)), estado(estadoNuevo), "admin", null, 200);
        comprobarCambio(anterior, actual, estadoNuevo);
        return actual;
    }

    private static void comprobarCambio(String anterior, String actual, String estado) {
        String actualizada = JsonPath.read(actual, "$.actualizadaEn");
        String precedente = JsonPath.read(anterior, "$.actualizadaEn");
        assertTrue(Instant.parse(actualizada).isAfter(Instant.parse(precedente)));
        cuerpo(vista(id(anterior), ((Number) JsonPath.read(anterior, "$.apartamentoId")).longValue(),
                JsonPath.read(anterior, "$.titulo"), JsonPath.read(anterior, "$.descripcion"), estado,
                JsonPath.read(anterior, "$.creadaEn"), actualizada, "RESUELTA".equals(estado) ? actualizada : null), actual);
    }

    private void compararDesde(long primero, String esperado, String ruta) throws Exception {
        String actual = enviar("GET", ruta, null, "admin", null, 200);
        cuerpo(esperado, JsonPath.parse(actual).read("$[?(@.id >= " + primero + ")]").toString());
    }

    private static String vista(long id, long apt, String titulo, String descripcion, String estado,
                                String creada, String actualizada, String resuelta) {
        return ("{\"id\":%d,\"apartamentoId\":%d,\"titulo\":\"%s\",\"descripcion\":\"%s\","
                + "\"estado\":\"%s\",\"creadaEn\":\"%s\",\"actualizadaEn\":\"%s\",\"resueltaEn\":%s}")
                .formatted(id, apt, titulo, descripcion, estado, creada, actualizada, resuelta == null ? "null" : "\"" + resuelta + "\"");
    }

    private static String lista(String... vistas) { return "[" + String.join(",", vistas) + "]"; }
    private static long id(String respuesta) { return ((Number) JsonPath.read(respuesta, "$.id")).longValue(); }
    private static String estadoDe(long id) { return INC + "/" + id + "/estado"; }
    private static String estado(String valor) { return "{\"estado\":\"" + valor + "\"}"; }
    private static String alta(long apt, String titulo, String descripcion) {
        return alta(Long.toString(apt), "\"" + titulo + "\"", "\"" + descripcion + "\"");
    }
    private static String alta(String apt, String tituloJson, String descripcionJson) {
        return "{\"apartamentoId\":%s,\"titulo\":%s,\"descripcion\":%s}".formatted(apt, tituloJson, descripcionJson);
    }

    private static void cuerpo(String esperado, String actual) {
        if (esperado.isEmpty()) assertEquals(esperado, actual);
        else JsonAssert.comparator(STRICT).assertIsMatch(esperado, actual);
    }

    private void admin(String metodo, String ruta, String datos, int estado, String esperado) throws Exception {
        cuerpo(esperado, enviar(metodo, ruta, datos, "admin", null, estado));
    }

    private String enviar(String metodo, String ruta, String datos, String sesion, String rol, int estado) throws Exception {
        var respuesta = cliente.send(solicitud(metodo, ruta, datos, sesion, rol), HttpResponse.BodyHandlers.ofString());
        assertEquals(estado, respuesta.statusCode(), () -> metodo + " " + ruta + " → " + respuesta.body());
        return respuesta.body();
    }

    private HttpRequest solicitud(String metodo, String ruta, String datos, String sesion, String rol) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta)).timeout(Duration.ofSeconds(10));
        if (rol != null) request.header("X-User-Role", rol);
        if (sesion != null) request.header("X-Test-Session", sesion);
        if (datos != null) request.header("Content-Type", "application/json");
        return request.method(metodo, datos == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(datos)).build();
    }
}
