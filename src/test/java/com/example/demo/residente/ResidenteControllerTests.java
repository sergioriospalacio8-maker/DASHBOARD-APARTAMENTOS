package com.example.demo.residente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.json.JsonCompareMode.STRICT;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Principal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.example.demo.ApartamentoController;
import com.example.demo.ApartamentoRepository;
import com.example.demo.ApartamentoService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.h2.api.Trigger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.test.json.JsonAssert;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = ResidenteControllerTests.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        args = "--spring.config.location=optional:classpath:/residente-test-only.properties",
        properties = {"server.address=127.0.0.1", "spring.datasource.url=jdbc:h2:mem:residente;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
                "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=none",
                "spring.data.jpa.repositories.enabled=false", "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:residente-schema.sql"})
class ResidenteControllerTests {
    private static final String RESIDENTES = "/api/residentes";
    private static final String OCUPACIONES = "/api/ocupaciones";
    private static final String APARTAMENTOS = "/api/apartamentos";
    private static final String ADMIN = "Bearer residente-test-admin";
    private static final String SIN_ROL = "Bearer residente-test-sin-rol";
    private static final String SIN_PRINCIPAL = "Bearer residente-test-sin-principal";
    private final HttpClient cliente = HttpClient.newHttpClient();
    @Value("${local.server.port}") private int puerto;
    @Autowired private JdbcTemplate jdbc;

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "com.example.demo")
    @Import({ApartamentoController.class, ApartamentoService.class, ApartamentoRepository.class, AutenticacionPrueba.class})
    @ComponentScan(useDefaultFilters = false, includeFilters = @ComponentScan.Filter(
            type = FilterType.ANNOTATION, classes = {RestController.class, Service.class}))
    static class App {}

    @TestConfiguration(proxyBeanMethods = false)
    static class AutenticacionPrueba {
        @Bean
        Filter principalDePrueba() {
            return (request, response, chain) -> {
                var http = (HttpServletRequest) request;
                String credencial = http.getHeader("Authorization");
                if (!ADMIN.equals(credencial) && !SIN_ROL.equals(credencial) && !SIN_PRINCIPAL.equals(credencial)) {
                    chain.doFilter(request, response);
                    return;
                }
                chain.doFilter(new HttpServletRequestWrapper(http) {
                    @Override public Principal getUserPrincipal() {
                        return SIN_PRINCIPAL.equals(credencial) ? null : () -> "principal-prueba";
                    }
                    @Override public boolean isUserInRole(String rol) {
                        return !SIN_ROL.equals(credencial) && "ADMIN".equals(rol);
                    }
                }, response);
            };
        }
    }

    @Test
    void exigePrincipalYRolAdminAntesDeProcesarLaSolicitud() throws Exception {
        long residente = crearResidente("Protegido");
        long apartamento = crearApartamento("Protegido");
        String filtro = OCUPACIONES + "?apartamentoId=" + apartamento;
        long ocupacion = iniciarOcupacion(apartamento, residente, "2026-07-01");
        verificar("", enviar("GET", RESIDENTES, null, null, null, 403));
        for (String credencial : new String[]{null, SIN_ROL, SIN_PRINCIPAL}) {
            prohibido("GET", RESIDENTES, null, credencial);
            prohibido("GET", RESIDENTES + "/" + residente, null, credencial);
            prohibido("POST", RESIDENTES, nombre("No autorizado"), credencial);
            prohibido("POST", RESIDENTES, "{", credencial);
            prohibido("PUT", RESIDENTES + "/" + residente, nombre("No autorizado"), credencial);
            prohibido("GET", filtro, null, credencial);
            prohibido("POST", OCUPACIONES, inicio(apartamento, residente + 1, "2026-07-01"), credencial);
            prohibido("PATCH", cierre(ocupacion), fin("2026-07-02"), credencial);
        }
        admin("GET", RESIDENTES + "/" + residente, null, 200, residente(residente, "Protegido"));
        admin("GET", filtro, null, 200, "[" + ocupacion(ocupacion, apartamento, residente, "2026-07-01", null) + "]");
        assertEquals(residente + 1, crearResidente("Después del rechazo"));
        assertEquals(ocupacion + 1, iniciarOcupacion(apartamento, residente + 1, "2026-07-01"));
    }

    @Test
    void creaConsultaListaYEditaLaFichaSinBorrarla() throws Exception {
        long primero = crearResidente("Primero");
        for (String cuerpo : List.of("{}", "{", nombre(" "), nombre("x".repeat(121)))) {
            admin("POST", RESIDENTES, cuerpo, 400, "");
            admin("PUT", RESIDENTES + "/" + primero, cuerpo, 400, "");
        }
        admin("GET", RESIDENTES + "/" + primero, null, 200, residente(primero, "Primero"));
        admin("GET", RESIDENTES + "/0", null, 400, "");
        admin("GET", RESIDENTES + "/abc", null, 400, "");
        long siguiente = primero + 1;
        admin("GET", RESIDENTES + "/" + siguiente, null, 404, "");
        admin("PUT", RESIDENTES + "/" + siguiente, nombre("Ausente"), 404, "");
        admin("POST", RESIDENTES, nombre("  " + "x".repeat(120) + "  "), 201, residente(siguiente, "x".repeat(120)));
        admin("PUT", RESIDENTES + "/" + siguiente, nombre("  Editado  "), 200, residente(siguiente, "Editado"));
        admin("GET", RESIDENTES + "/" + siguiente, null, 200, residente(siguiente, "Editado"));
        String lista = enviar("GET", RESIDENTES, null, ADMIN, null, 200);
        List<Number> ids = JsonPath.read(lista, "$[*].id");
        assertEquals(ids.stream().map(Number::longValue).sorted().toList(), ids.stream().map(Number::longValue).toList());
        String fichas = JsonPath.parse(lista).read("$[?(@.id >= " + primero + " && @.id <= " + siguiente + ")]").toString();
        verificar("[" + residente(primero, "Primero") + "," + residente(siguiente, "Editado") + "]", fichas);
        enviar("DELETE", RESIDENTES + "/" + siguiente, null, ADMIN, null, 405);
        admin("GET", RESIDENTES + "/" + siguiente, null, 200, residente(siguiente, "Editado"));
    }

    @Test
    void conservaHistorialPermiteCoResidentesYCombinaFiltros() throws Exception {
        long persona = crearResidente("Titular");
        long conviviente = crearResidente("Conviviente");
        long apartamento = crearApartamento("Ocupado");
        long otro = crearApartamento("Otro");
        String porApartamento = OCUPACIONES + "?apartamentoId=" + apartamento;
        String porResidente = OCUPACIONES + "?residenteId=" + persona;
        String ambos = porApartamento + "&residenteId=" + persona;
        long id = iniciarOcupacion(apartamento, persona, "2026-01-02");
        String activa = ocupacion(id, apartamento, persona, "2026-01-02", null);

        admin("POST", OCUPACIONES, inicio(apartamento, persona, "2026-01-03"), 409, "");
        admin("POST", OCUPACIONES, inicio(otro + 1, persona, "2026-01-02"), 404, "");
        admin("POST", OCUPACIONES, inicio(apartamento, conviviente + 1, "2026-01-02"), 404, "");
        for (String cuerpo : List.of("{}", inicio(apartamento, 0, "2026-01-02"), inicio(0, persona, "2026-01-02"),
                inicio(apartamento, persona, "2026-02-30"), inicio(apartamento, persona, "0999-12-31"),
                inicio(apartamento, persona, "+10000-01-01"))) {
            admin("POST", OCUPACIONES, cuerpo, 400, "");
        }
        admin("POST", OCUPACIONES, "{\"apartamentoId\":" + apartamento + ",\"residenteId\":" + persona
                + ".5,\"fechaInicio\":\"2026-01-02\"}", 400, "");
        admin("PATCH", cierre(id), fin("2026-01-01"), 400, "");
        admin("PATCH", cierre(id), fin("0999-12-31"), 400, "");
        admin("PATCH", cierre(id), "{}", 400, "");
        admin("PATCH", cierre(id + 1), fin("2026-01-02"), 404, "");
        admin("GET", porApartamento, null, 200, "[" + activa + "]");
        admin("GET", OCUPACIONES + "?residenteId=-1", null, 400, "");
        admin("GET", OCUPACIONES + "?apartamentoId=abc", null, 400, "");
        admin("GET", OCUPACIONES + "?apartamentoId=" + (otro + 1), null, 200, "[]");

        assertEquals(id + 1, iniciarOcupacion(apartamento, conviviente, "2026-01-02"));
        assertEquals(id + 2, iniciarOcupacion(otro, persona, "2026-02-01"));
        String cerrada = ocupacion(id, apartamento, persona, "2026-01-02", "2026-01-02");
        admin("PATCH", cierre(id), fin("2026-01-02"), 200, cerrada);
        admin("PATCH", cierre(id), fin("2026-01-03"), 409, "");
        admin("GET", ambos, null, 200, "[" + cerrada + "]");

        // Incluso sin ocupaciones activas, la FK protege el historial del apartamento.
        String coResidente = ocupacion(id + 1, apartamento, conviviente, "2026-01-02", "2026-01-03");
        admin("PATCH", cierre(id + 1), fin("2026-01-03"), 200, coResidente);
        String original = apartamentoListado(apartamento);
        verificar("", enviar("DELETE", APARTAMENTOS + "/" + apartamento, null, null, "ADMIN", 409));
        verificar(original, apartamentoListado(apartamento));
        admin("GET", porApartamento, null, 200, lista(cerrada, coResidente));
        assertEquals(id + 3, iniciarOcupacion(apartamento, persona, "2026-02-01"));
        String nueva = ocupacion(id + 3, apartamento, persona, "2026-02-01", null);
        String segundoApartamento = ocupacion(id + 2, otro, persona, "2026-02-01", null);
        admin("GET", ambos, null, 200, lista(cerrada, nueva));
        admin("GET", porApartamento, null, 200, lista(cerrada, coResidente, nueva));
        admin("GET", porResidente, null, 200, lista(cerrada, segundoApartamento, nueva));
        String todas = enviar("GET", OCUPACIONES, null, ADMIN, null, 200);
        String rango = JsonPath.parse(todas).read("$[?(@.id >= " + id + " && @.id <= " + (id + 3) + ")]").toString();
        verificar(lista(cerrada, coResidente, segundoApartamento, nueva), rango);
        enviar("DELETE", OCUPACIONES + "/" + id, null, ADMIN, null, 404);
        admin("GET", ambos, null, 200, lista(cerrada, nueva));
    }

    @Test
    void soloAceptaUnaAltaConcurrentePorPar() throws Exception {
        long residente = crearResidente("Concurrente");
        long apartamento = crearApartamento("Concurrente");
        var solicitud = solicitud("POST", OCUPACIONES, inicio(apartamento, residente, "2026-03-01"), ADMIN, null);
        var primera = cliente.sendAsync(solicitud, HttpResponse.BodyHandlers.ofString());
        var segunda = cliente.sendAsync(solicitud, HttpResponse.BodyHandlers.ofString());
        var respuestas = Stream.of(primera.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS))
                .sorted(java.util.Comparator.comparingInt(HttpResponse::statusCode)).toList();
        assertEquals(List.of(201, 409), respuestas.stream().map(HttpResponse::statusCode).toList());
        assertEquals("", respuestas.get(1).body());
        Number id = JsonPath.read(respuestas.get(0).body(), "$.id");
        String esperada = ocupacion(id.longValue(), apartamento, residente, "2026-03-01", null);
        verificar(esperada, respuestas.get(0).body());
        admin("GET", OCUPACIONES + "?apartamentoId=" + apartamento, null, 200, lista(esperada));
        long otraPersona = crearResidente("Otro concurrente");
        assertEquals(id.longValue() + 1, iniciarOcupacion(apartamento, otraPersona, "2026-03-01"));
    }

    @Test
    void noTraduceUnErrorSqlAjenoALaClaveForanea() throws Exception {
        long id = crearApartamento("Fallo SQL de prueba");
        jdbc.execute("CREATE TRIGGER fallo_delete_apartamento BEFORE DELETE ON apartamentos FOR EACH ROW CALL '"
                + FalloDeleteApartamento.class.getName() + "'");
        String original = apartamentoListado(id);
        String ruta = APARTAMENTOS + "/" + id;
        String error = enviar("DELETE", ruta, null, null, "ADMIN", 500);
        assertEquals("Internal Server Error", JsonPath.read(error, "$.error"));
        assertEquals(ruta, JsonPath.read(error, "$.path"));
        verificar(original, apartamentoListado(id));
    }

    public static class FalloDeleteApartamento implements Trigger {
        @Override
        public void fire(Connection conexion, Object[] anterior, Object[] nueva) throws SQLException {
            if ("Fallo SQL de prueba".equals(anterior[1])) {
                throw new SQLException("Fallo de integridad de prueba distinto de una FK", "23513", 23513);
            }
        }
    }

    @Test
    void preservaContratoHttp() throws Exception {
        String nuevo = "{\"numero\":\"202\",\"torre\":\"Legado\",\"area\":72.5,\"propietarioId\":\"p2\"}";
        verificar("", enviar("GET", APARTAMENTOS, null, ADMIN, null, 403));
        verificar("", enviar("POST", APARTAMENTOS, nuevo, ADMIN, null, 403));
        String creado = enviar("POST", APARTAMENTOS, nuevo, null, "admin", 201);
        Number id = JsonPath.read(creado, "$.id");
        verificar("{\"id\":" + id + "," + nuevo.substring(1), creado);
        String listado = enviar("GET", APARTAMENTOS, null, null, "admin", 200);
        verificar(lista(creado), JsonPath.parse(listado).read("$[?(@.id == " + id + ")]").toString());
        String cambiado = "{\"id\":" + id + ",\"numero\":\"203\",\"torre\":null,\"area\":null,\"propietarioId\":null}";
        verificar(cambiado, enviar("PUT", APARTAMENTOS + "/" + id, "{\"numero\":\"203\"}", null, "admin", 200));
        verificar("", enviar("DELETE", APARTAMENTOS + "/" + id, null, ADMIN, null, 403));
        verificar(lista(cambiado), apartamentoListado(id.longValue()));
        verificar("", enviar("DELETE", APARTAMENTOS + "/" + id, null, null, "admin", 204));
        verificar("", enviar("DELETE", APARTAMENTOS + "/" + id, null, null, "admin", 404));
    }

    private String apartamentoListado(long id) throws Exception {
        String listado = enviar("GET", APARTAMENTOS, null, null, "ADMIN", 200);
        return JsonPath.parse(listado).read("$[?(@.id == " + id + ")]").toString();
    }

    private long crearResidente(String nombre) throws Exception {
        String cuerpo = enviar("POST", RESIDENTES, nombre(nombre), ADMIN, null, 201);
        Number id = JsonPath.read(cuerpo, "$.id");
        verificar(residente(id.longValue(), nombre), cuerpo);
        return id.longValue();
    }

    private long iniciarOcupacion(long apartamento, long residente, String fecha) throws Exception {
        String cuerpo = enviar("POST", OCUPACIONES, inicio(apartamento, residente, fecha), ADMIN, null, 201);
        Number id = JsonPath.read(cuerpo, "$.id");
        verificar(ocupacion(id.longValue(), apartamento, residente, fecha, null), cuerpo);
        return id.longValue();
    }

    private long crearApartamento(String numero) throws Exception {
        String cuerpo = enviar("POST", APARTAMENTOS, "{\"numero\":\"" + numero + "\"}", null, "ADMIN", 201);
        Number id = JsonPath.read(cuerpo, "$.id");
        return id.longValue();
    }

    private void admin(String metodo, String ruta, String cuerpo, int estado, String esperado) throws Exception {
        verificar(esperado, enviar(metodo, ruta, cuerpo, ADMIN, null, estado));
    }

    private void prohibido(String metodo, String ruta, String cuerpo, String credencial) throws Exception {
        verificar("", enviar(metodo, ruta, cuerpo, credencial, "ADMIN", 403));
    }

    private String enviar(String metodo, String ruta, String cuerpo, String credencial, String rol, int estado) throws Exception {
        var respuesta = cliente.send(solicitud(metodo, ruta, cuerpo, credencial, rol), HttpResponse.BodyHandlers.ofString());
        assertEquals(estado, respuesta.statusCode(), () -> metodo + " " + ruta + " → " + respuesta.body());
        return respuesta.body();
    }

    private HttpRequest solicitud(String metodo, String ruta, String cuerpo, String credencial, String rol) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta));
        if (credencial != null) request.header("Authorization", credencial);
        if (rol != null) request.header("X-User-Role", rol);
        if (cuerpo != null) request.header("Content-Type", "application/json");
        return request.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo)).build();
    }

    private static void verificar(String esperado, String respuesta) {
        if (esperado.isEmpty()) assertEquals("", respuesta);
        else JsonAssert.comparator(STRICT).assertIsMatch(esperado, respuesta);
    }

    private static String nombre(String valor) { return "{\"nombre\":\"" + valor + "\"}"; }
    private static String residente(long id, String nombre) { return "{\"id\":" + id + ",\"nombre\":\"" + nombre + "\"}"; }
    private static String inicio(long apartamento, long residente, String fecha) {
        return "{\"apartamentoId\":" + apartamento + ",\"residenteId\":" + residente + ",\"fechaInicio\":\"" + fecha + "\"}";
    }
    private static String cierre(long id) { return OCUPACIONES + "/" + id + "/cierre"; }
    private static String fin(String fecha) { return "{\"fechaFin\":\"" + fecha + "\"}"; }
    private static String ocupacion(long id, long apartamento, long residente, String inicio, String fin) {
        return "{\"id\":" + id + ",\"apartamentoId\":" + apartamento + ",\"residenteId\":" + residente
                + ",\"fechaInicio\":\"" + inicio + "\",\"fechaFin\":" + (fin == null ? "null" : "\"" + fin + "\"") + "}";
    }
    private static String lista(String... elementos) { return "[" + String.join(",", elementos) + "]"; }
}
