package com.example.demo.condominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.json.JsonCompareMode.STRICT;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Principal;

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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.JsonAssert;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = CondominioControllerTests.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        args = "--spring.config.location=optional:classpath:/condominio-test-only.properties",
        properties = {"server.address=127.0.0.1", "spring.datasource.url=jdbc:h2:mem:condominio;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
                "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=none",
                "spring.data.jpa.repositories.enabled=false", "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:condominio-schema.sql"})
class CondominioControllerTests {
    private static final String C = "/api/condominios";
    private static final String T = "/api/torres";
    private static final String A = "/api/apartamentos";
    private static final String CONDOMINIO = "{\"id\":1,\"nombre\":\"Central\"}";
    private static final String TORRE = "{\"id\":1,\"nombre\":\"Norte\",\"condominioId\":1}";
    private static final String ADMIN_PRUEBA = "Bearer condominio-test-admin";
    private static final String RESIDENTE_PRUEBA = "Bearer condominio-test-residente";
    private final HttpClient cliente = HttpClient.newHttpClient();
    @Value("${local.server.port}") private int puerto;

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "com.example.demo")
    @Import({ApartamentoController.class, ApartamentoService.class, ApartamentoRepository.class, AutenticacionPrueba.class})
    @ComponentScan(useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = RestController.class))
    static class App {}

    @TestConfiguration(proxyBeanMethods = false)
    static class AutenticacionPrueba {
        @Bean
        Filter principalDePrueba() {
            return (request, response, chain) -> {
                var http = (HttpServletRequest) request;
                String credencial = http.getHeader("Authorization");
                boolean admin = ADMIN_PRUEBA.equals(credencial);
                if (!admin && !RESIDENTE_PRUEBA.equals(credencial)) {
                    chain.doFilter(request, response);
                    return;
                }
                chain.doFilter(new HttpServletRequestWrapper(http) {
                    @Override public Principal getUserPrincipal() { return () -> admin ? "admin-prueba" : "residente-prueba"; }
                    @Override public boolean isUserInRole(String rol) { return admin && "ADMIN".equals(rol); }
                }, response);
            };
        }
    }

    @Test
    void gestionaCondominiosTorresYAsociacionSinBorrarApartamentos() throws Exception {
        http("POST", C, nombre("Sin credenciales"), "ADMIN", null, 403, "");
        admin("GET", C, null, 200, "[]");
        admin("GET", T, null, 200, "[]");
        admin("POST", C, nombre(" "), 400, "");
        admin("POST", C, nombre("x".repeat(121)), 400, "");
        admin("GET", C, null, 200, "[]");
        admin("POST", C, nombre(" Central "), 201, CONDOMINIO);
        admin("GET", C + "/1", null, 200, CONDOMINIO);
        admin("GET", C, null, 200, "[" + CONDOMINIO + "]");
        String renombrado = "{\"id\":1,\"nombre\":\"Principal\"}";
        admin("PUT", C + "/1", nombre("Principal"), 200, renombrado);
        admin("PUT", C + "/1", "{}", 400, "");
        admin("GET", C + "/1", null, 200, renombrado);
        admin("PUT", C + "/99", nombre("Principal"), 404, "");
        admin("POST", C, nombre("Anexo"), 201, "{\"id\":2,\"nombre\":\"Anexo\"}");

        admin("POST", T, nombre("Norte"), 400, "");
        admin("POST", T, torre("Norte", 99), 404, "");
        http("POST", T, torre("Sin credenciales", 1), "ADMIN", null, 403, "");
        admin("GET", T, null, 200, "[]");
        admin("POST", T, torre("Norte", 1), 201, TORRE);
        admin("GET", T, null, 200, "[" + TORRE + "]");
        admin("DELETE", C + "/1", null, 409, "");
        admin("PUT", T + "/1", torre(" ", 1), 400, "");
        admin("PUT", T + "/1", torre("Sur", 99), 404, "");
        admin("PUT", T + "/99", torre("Sur", 1), 404, "");
        admin("GET", T + "/1", null, 200, TORRE);
        admin("GET", C + "/1", null, 200, renombrado);
        admin("PUT", T + "/1", torre("Sur", 2), 200, "{\"id\":1,\"nombre\":\"Sur\",\"condominioId\":2}");
        admin("DELETE", C + "/1", null, 204, "");
        admin("GET", C + "/1", null, 404, "");
        admin("DELETE", C + "/1", null, 404, "");

        String datosApartamento = "\"numero\":\"101\",\"torre\":\"Texto legado\",\"area\":68.0,\"propietarioId\":\"p1\"";
        String creado = enviar("POST", A, "{" + datosApartamento + "}", "ADMIN", null, 201);
        Number apartamentoId = JsonPath.read(creado, "$.id");
        String apartamento = "{\"id\":" + apartamentoId + "," + datosApartamento + "}";
        JsonAssert.comparator(STRICT).assertIsMatch(apartamento, creado);
        String rutaApartamento = A + "/" + apartamentoId;
        String enlace = rutaApartamento + "/torre";
        http("PUT", enlace, "{\"torreId\":1}", "ADMIN", null, 403, "");
        admin("GET", enlace, null, 200, "{\"torreId\":null}");
        admin("PUT", A + "/99/torre", "{\"torreId\":1}", 404, "");
        admin("GET", A + "/99/torre", null, 404, "");
        admin("PUT", enlace, "{\"torreId\":1}", 200, "{\"torreId\":1}");
        admin("PUT", enlace, "{\"torreId\":99}", 404, "");
        admin("GET", enlace, null, 200, "{\"torreId\":1}");
        admin("PUT", enlace, "{\"torreId\":null}", 200, "{\"torreId\":null}");
        admin("GET", enlace, null, 200, "{\"torreId\":null}");
        admin("PUT", enlace, "{\"torreId\":1}", 200, "{\"torreId\":1}");
        admin("PUT", enlace, "{}", 200, "{\"torreId\":null}");
        admin("PUT", enlace, "{\"torreId\":1}", 200, "{\"torreId\":1}");
        String actualizado = apartamento.replace("101", "102");
        adminLegado("PUT", rutaApartamento, "{" + datosApartamento.replace("101", "102") + "}", 200, actualizado);
        admin("GET", enlace, null, 200, "{\"torreId\":1}");
        admin("DELETE", T + "/1", null, 204, "");
        admin("GET", T + "/1", null, 404, "");
        admin("DELETE", T + "/1", null, 404, "");
        adminLegado("GET", A, null, 200, "[" + actualizado + "]");
        admin("GET", enlace, null, 200, "{\"torreId\":null}");
        admin("DELETE", C + "/2", null, 204, "");
        admin("GET", C, null, 200, "[]");
        admin("GET", T, null, 200, "[]");
        adminLegado("DELETE", rutaApartamento, null, 204, "");
    }

    @Test
    void requiereAdminEnLasRutasNuevas() throws Exception {
        for (String recurso : new String[]{C, T}) {
            http("GET", recurso, null, null, null, 403, "");
            http("GET", recurso + "/1", null, "ADMIN", null, 403, "");
            http("POST", recurso, torre("Nombre", 1), "ADMIN", null, 403, "");
            http("PUT", recurso + "/1", torre("Nombre", 1), "ADMIN", null, 403, "");
            http("DELETE", recurso + "/1", null, "ADMIN", null, 403, "");
            assertEquals("", enviar("GET", recurso, null, "ADMIN", null, RESIDENTE_PRUEBA, 403));
        }
        http("GET", A + "/1/torre", null, "ADMIN", null, 403, "");
        http("PUT", A + "/1/torre", "{\"torreId\":1}", "ADMIN", null, 403, "");
    }

    @Test
    void preservaContratoHttp() throws Exception {
        String nuevo = "{\"numero\":\"202\",\"torre\":\"Legado\",\"area\":72.5,\"propietarioId\":\"p2\",\"torreId\":999}";
        http("GET", A, null, null, null, 403, "");
        http("GET", A + "/mio", null, null, null, 401, "");
        http("POST", A, nuevo, null, null, 403, "");
        String creado = enviar("POST", A, nuevo, "ADMIN", null, 201);
        Number id = JsonPath.read(creado, "$.id");
        String original = "{\"id\":" + id + ",\"numero\":\"202\",\"torre\":\"Legado\",\"area\":72.5,\"propietarioId\":\"p2\"}";
        JsonAssert.comparator(STRICT).assertIsMatch(original, creado);
        adminLegado("GET", A, null, 200, "[" + original + "]");
        http("GET", A + "/mio", null, null, "p2", 200, original);
        String actualizado = "{\"id\":" + id + ",\"numero\":\"203\",\"torre\":null,\"area\":null,\"propietarioId\":null}";
        adminLegado("PUT", A + "/" + id, "{\"numero\":\"203\",\"torreId\":999}", 200, actualizado);
        http("GET", A + "/mio", null, null, "p2", 404, "");
        adminLegado("DELETE", A + "/" + id, null, 204, "");
        adminLegado("DELETE", A + "/" + id, null, 404, "");
        adminLegado("GET", A, null, 200, "[]");
    }

    private static String nombre(String valor) { return "{\"nombre\":\"" + valor + "\"}"; }
    private static String torre(String valor, long condominio) {
        return "{\"nombre\":\"" + valor + "\",\"condominioId\":" + condominio + "}";
    }

    private void admin(String metodo, String ruta, String cuerpo, int estado, String esperado) throws Exception {
        verificar(metodo, ruta, esperado, enviar(metodo, ruta, cuerpo, null, null, ADMIN_PRUEBA, estado));
    }

    private void adminLegado(String metodo, String ruta, String cuerpo, int estado, String esperado) throws Exception {
        http(metodo, ruta, cuerpo, "admin", null, estado, esperado);
    }

    private void http(String metodo, String ruta, String cuerpo, String rol, String usuario, int estado, String esperado) throws Exception {
        verificar(metodo, ruta, esperado, enviar(metodo, ruta, cuerpo, rol, usuario, estado));
    }

    private static void verificar(String metodo, String ruta, String esperado, String respuesta) {
        if (esperado.isEmpty()) assertEquals("", respuesta, metodo + " " + ruta);
        else JsonAssert.comparator(STRICT).assertIsMatch(esperado, respuesta);
    }

    private String enviar(String metodo, String ruta, String cuerpo, String rol, String usuario, int estado) throws Exception {
        return enviar(metodo, ruta, cuerpo, rol, usuario, null, estado);
    }

    private String enviar(String metodo, String ruta, String cuerpo, String rol, String usuario, String credencial, int estado) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta));
        if (credencial != null) request.header("Authorization", credencial);
        if (rol != null) request.header("X-User-Role", rol);
        if (usuario != null) request.header("X-User-Id", usuario);
        if (cuerpo != null) request.header("Content-Type", "application/json");
        var respuesta = cliente.send(request.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(estado, respuesta.statusCode(), () -> metodo + " " + ruta + " → " + respuesta.body());
        return respuesta.body();
    }
}
