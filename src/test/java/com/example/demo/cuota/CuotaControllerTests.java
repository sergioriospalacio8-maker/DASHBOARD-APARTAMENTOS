package com.example.demo.cuota;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.json.JsonCompareMode.STRICT;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Principal;
import java.time.Duration;
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

@SpringBootTest(classes = CuotaControllerTests.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        args = "--spring.config.location=optional:classpath:/cuota-test-only.properties",
        properties = {"server.address=127.0.0.1", "spring.datasource.url=jdbc:h2:mem:cuota;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
                "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=none",
                "spring.data.jpa.repositories.enabled=false", "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:cuota-schema.sql"})
class CuotaControllerTests {
    private static final String IDS = "/api/cuotas";
    private static final String APT = "/api/apartamentos";
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
        Filter autenticacionDePrueba() {
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
    void protegeTodasLasRutasConPrincipalYRolDelServidor() throws Exception {
        long apartamento = apartamento();
        cuerpo("", enviar("POST", IDS, "{", null, 403));
        long id = crear(apartamento, "10.00", "COP");
        String ruta = IDS + "/" + id;
        long primero = crearPago(ruta + "/pagos", id, "1", "previo");
        for (String sesion : new String[]{null, "sinRol", "sinPrincipal"}) {
            cuerpo("", enviar("GET", IDS, null, sesion, 403));
            cuerpo("", enviar("GET", ruta, null, sesion, 403));
            cuerpo("", enviar("GET", ruta + "/pagos", null, sesion, 403));
            cuerpo("", enviar("POST", IDS, alta(apartamento, "10", "COP"), sesion, 403));
            cuerpo("", enviar("POST", IDS, "{", sesion, 403));
            cuerpo("", enviar("POST", ruta + "/pagos", abono("1", "a"), sesion, 403));
        }
        admin("GET", IDS + "?apartamentoId=" + apartamento, null, 200,
                "[" + cuota(id, apartamento, "10", "COP", "9") + "]");
        admin("GET", ruta + "/pagos", null, 200, "[" + pago(primero, id, "1", "previo") + "]");
        assertEquals(primero + 1, crearPago(ruta + "/pagos", id, "1", "a"));
        assertEquals(id + 1, crear(apartamento, "10.00", "COP"));
    }

    @Test
    void creaListaFiltraYRechazaCuotasInvalidasSinConsumirIds() throws Exception {
        long apt = apartamento(), otro = apartamento();
        long id = crear(apt, "0.01", "EUR");
        String ruta = IDS + "/" + id;
        String primera = cuota(id, apt, "0.01", "EUR", "0.01");
        admin("GET", ruta, null, 200, primera);
        admin("GET", IDS + "?apartamentoId=" + otro, null, 200, "[]");
        for (String importe : List.of("0", "-0.01", "1.001", "1.000", "10000000000", "null", "\"x\"")) {
            admin("POST", IDS, alta(apt, importe, "EUR"), 400, "");
        }
        for (String apartamentoId : List.of("0", "-1", "1.2", "9223372036854775808", "null")) {
            admin("POST", IDS, alta(apartamentoId, "1", "EUR"), 400, "");
        }
        for (String moneda : List.of("", "euR", "EURO", "€UR")) {
            admin("POST", IDS, alta(apt, "1", moneda), 400, "");
        }
        admin("POST", IDS, "{}", 400, "");
        admin("POST", IDS, alta(otro + 1, "1", "EUR"), 404, "");
        admin("GET", IDS + "/" + (id + 1), null, 404, "");
        admin("GET", IDS + "?apartamentoId=" + (otro + 1), null, 404, "");
        for (String invalido : List.of("0", "-1", "abc", "9223372036854775808")) {
            admin("GET", IDS + "/" + invalido, null, 400, "");
            admin("GET", IDS + "?apartamentoId=" + invalido, null, 400, "");
        }
        admin("GET", IDS + "?apartamentoId=", null, 400, "");
        assertEquals(id + 1, crear(otro, "9999999999.99", "COP"));
        String ultima = cuota(id + 1, otro, "9999999999.99", "COP", "9999999999.99");
        admin("GET", IDS + "?apartamentoId=" + apt, null, 200, "[" + primera + "]");
        admin("GET", IDS + "?apartamentoId=" + otro, null, 200, "[" + ultima + "]");
        String listado = enviar("GET", IDS, null, "admin", 200);
        cuerpo("[" + primera + "," + ultima + "]", JsonPath.parse(listado).read("$[?(@.id >= " + id + ")]").toString());
    }

    @Test
    void registraPagosExactosRepiteReferenciasYProtegeHistorial() throws Exception {
        long apt = apartamento(), id = crear(apt, "0.30", "USD");
        String ruta = IDS + "/" + id, pagos = ruta + "/pagos";
        admin("GET", pagos, null, 200, "[]");
        long primero = crearPago(pagos, id, "0.10", "R-1");
        String original = pago(primero, id, "0.10", "R-1");
        admin("GET", ruta, null, 200, cuota(id, apt, "0.30", "USD", "0.20"));
        for (String importe : List.of("0", "-0.01", "0.001", "10000000000", "null")) {
            admin("POST", pagos, abono(importe, "R-2"), 400, "");
        }
        for (String referencia : List.of("", "a b", "a".repeat(65))) {
            admin("POST", pagos, abono("0.01", referencia), 400, "");
        }
        admin("POST", pagos, "{\"importe\":0.01}", 400, "");
        admin("POST", pagos, "{\"importe\":0.01,\"referencia\":123}", 400, "");
        admin("POST", IDS + "/0/pagos", abono("0.01", "R-2"), 400, "");
        admin("POST", IDS + "/" + (id + 1) + "/pagos", abono("0.01", "R-2"), 404, "");
        admin("GET", IDS + "/" + (id + 1) + "/pagos", null, 404, "");
        admin("POST", pagos, abono("0.11", "R-1"), 409, "");
        admin("POST", pagos, abono("0.21", "r-1"), 409, "");
        admin("GET", pagos, null, 200, "[" + original + "]");
        admin("GET", ruta, null, 200, cuota(id, apt, "0.30", "USD", "0.20"));
        assertEquals(primero + 1, crearPago(pagos, id, "0.20", "r-1"));
        admin("POST", pagos, abono("0.1", "R-1"), 200, original);
        admin("POST", pagos, abono("0.01", "R-2"), 409, "");
        String fin = cuota(id, apt, "0.30", "USD", "0.00");
        String historial = "[" + original + "," + pago(primero + 1, id, "0.20", "r-1") + "]";
        String apartamento = apartamentoListado(apt);
        assertEquals(405, enviar("DELETE", ruta, null, "admin").statusCode());
        assertEquals(405, enviar("DELETE", pagos, null, "admin").statusCode());
        admin("GET", ruta, null, 200, fin);
        admin("GET", pagos, null, 200, historial);
        cuerpo("", enviar("DELETE", APT + "/" + apt, null, null, 409));
        cuerpo(apartamento, apartamentoListado(apt));
        admin("GET", IDS + "?apartamentoId=" + apt, null, 200, "[" + fin + "]");
        admin("GET", pagos, null, 200, historial);
        long otro = crear(apt, "0.01", "EUR");
        String otrosPagos = IDS + "/" + otro + "/pagos";
        assertEquals(primero + 2, crearPago(otrosPagos, otro, "0.01", "R-1"));
        long tercera = crear(apt, "9999999999.99", "USD");
        assertEquals(primero + 3, crearPago(IDS + "/" + tercera + "/pagos", tercera, "9999999999.99", "x".repeat(64)));
    }

    @Test
    void serializaPagosConcurrentesDeLaMismaCuota() throws Exception {
        long apt = apartamento(), id = crear(apt, "1.00", "COP");
        String ruta = IDS + "/" + id;
        String pagos = ruta + "/pagos";
        var distintos = simultaneos(pagos, abono("0.70", "a"), abono("0.70", "b"));
        assertEquals(List.of(201, 409), distintos.stream().map(HttpResponse::statusCode).toList());
        cuerpo("", distintos.get(1).body());
        String ganador = distintos.get(0).body();
        long primero = id(ganador);
        String ref = JsonPath.read(ganador, "$.referencia");
        assertTrue(List.of("a", "b").contains(ref));
        cuerpo(pago(primero, id, "0.70", ref), ganador);
        admin("GET", ruta, null, 200, cuota(id, apt, "1.00", "COP", "0.30"));
        var repetidos = simultaneos(pagos, abono("0.30", "c"), abono("0.3", "c"));
        assertEquals(List.of(200, 201), repetidos.stream().map(HttpResponse::statusCode).toList());
        String ultimo = pago(primero + 1, id, "0.30", "c");
        for (var respuesta : repetidos) cuerpo(ultimo, respuesta.body());
        admin("GET", pagos, null, 200, "[" + ganador + "," + ultimo + "]");
        admin("GET", ruta, null, 200, cuota(id, apt, "1.00", "COP", "0.00"));
    }

    @Test
    void preservaContratoHttp() throws Exception {
        long apt = apartamento();
        String ruta = APT + "/" + apt;
        String original = "[{\"id\":" + apt + ",\"numero\":\"101\",\"torre\":null,\"area\":null,\"propietarioId\":null}]";
        cuerpo(original, apartamentoListado(apt));
        cuerpo("", enviar("DELETE", ruta, null, "admin", "RESIDENTE", 403));
        cuerpo(original, apartamentoListado(apt));
        cuerpo("", enviar("DELETE", ruta, null, null, 204));
        cuerpo("", enviar("DELETE", ruta, null, null, 404));
        cuerpo("[]", apartamentoListado(apt));
    }

    private long apartamento() throws Exception {
        return id(enviar("POST", APT, "{\"numero\":\"101\"}", null, 201));
    }

    private String apartamentoListado(long id) throws Exception {
        return JsonPath.parse(enviar("GET", APT, null, null, 200)).read("$[?(@.id == " + id + ")]").toString();
    }

    private long crear(long apt, String importe, String moneda) throws Exception {
        String respuesta = enviar("POST", IDS, alta(apt, importe, moneda), "admin", 201);
        long id = id(respuesta);
        cuerpo(cuota(id, apt, importe, moneda, importe), respuesta);
        return id;
    }

    private long crearPago(String ruta, long cuota, String importe, String ref) throws Exception {
        String respuesta = enviar("POST", ruta, abono(importe, ref), "admin", 201);
        long id = id(respuesta);
        cuerpo(pago(id, cuota, importe, ref), respuesta);
        return id;
    }

    private static long id(String respuesta) { return ((Number) JsonPath.read(respuesta, "$.id")).longValue(); }
    private static String alta(long apt, String importe, String moneda) { return alta(Long.toString(apt), importe, moneda); }
    private static String alta(String apt, String importe, String moneda) {
        return "{\"apartamentoId\":%s,\"importe\":%s,\"moneda\":\"%s\"}".formatted(apt, importe, moneda);
    }
    private static String abono(String importe, String ref) {
        return "{\"importe\":%s,\"referencia\":\"%s\"}".formatted(importe, ref);
    }
    private static String cuota(long id, long apt, String importe, String moneda, String saldo) {
        return "{\"id\":%d,\"apartamentoId\":%d,\"importe\":%s,\"moneda\":\"%s\",\"saldo\":%s}"
                .formatted(id, apt, importe, moneda, saldo);
    }
    private static String pago(long id, long cuota, String importe, String ref) {
        return "{\"id\":%d,\"cuotaId\":%d,\"importe\":%s,\"referencia\":\"%s\"}".formatted(id, cuota, importe, ref);
    }

    private static void cuerpo(String esperado, String actual) {
        if (esperado.isEmpty()) assertEquals(esperado, actual);
        else JsonAssert.comparator(STRICT).assertIsMatch(esperado, actual);
    }

    private void admin(String metodo, String ruta, String datos, int estado, String esperado) throws Exception {
        cuerpo(esperado, enviar(metodo, ruta, datos, "admin", estado));
    }

    private String enviar(String metodo, String ruta, String datos, String sesion, int estado) throws Exception {
        var respuesta = enviar(metodo, ruta, datos, sesion);
        assertEquals(estado, respuesta.statusCode(), () -> metodo + " " + ruta + " → " + respuesta.body());
        return respuesta.body();
    }

    private String enviar(String metodo, String ruta, String datos, String sesion, String rol, int estado) throws Exception {
        var respuesta = cliente.send(solicitud(metodo, ruta, datos, sesion, rol), HttpResponse.BodyHandlers.ofString());
        assertEquals(estado, respuesta.statusCode(), () -> metodo + " " + ruta + " → " + respuesta.body());
        return respuesta.body();
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String datos, String sesion) throws Exception {
        return cliente.send(solicitud(metodo, ruta, datos, sesion), HttpResponse.BodyHandlers.ofString());
    }

    private List<HttpResponse<String>> simultaneos(String ruta, String a, String b) throws Exception {
        var uno = cliente.sendAsync(solicitud("POST", ruta, a, "admin"), HttpResponse.BodyHandlers.ofString());
        var dos = cliente.sendAsync(solicitud("POST", ruta, b, "admin"), HttpResponse.BodyHandlers.ofString());
        return List.of(uno.get(15, TimeUnit.SECONDS), dos.get(15, TimeUnit.SECONDS)).stream()
                .sorted(Comparator.comparingInt(HttpResponse::statusCode)).toList();
    }

    private HttpRequest solicitud(String metodo, String ruta, String datos, String sesion) {
        return solicitud(metodo, ruta, datos, sesion, "ADMIN");
    }

    private HttpRequest solicitud(String metodo, String ruta, String datos, String sesion, String rol) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta))
                .timeout(Duration.ofSeconds(10)).header("X-User-Role", rol);
        if (sesion != null) request.header("X-Test-Session", sesion);
        if (datos != null) request.header("Content-Type", "application/json");
        return request.method(metodo, datos == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(datos)).build();
    }
}
