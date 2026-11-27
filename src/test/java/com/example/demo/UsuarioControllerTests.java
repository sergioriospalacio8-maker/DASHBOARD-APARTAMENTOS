package com.example.demo;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

class UsuarioControllerTests {

    private static final String USUARIOS = "/api/usuarios";
    private static final String ID = "a3c9d6c5-bf6e-4637-87ce-ec3efc38588a";
    private static final String USUARIO = """
            {"id":"%s","nombre":"Prueba Única %%_","email":"prueba@example.com","edad":29}
            """.formatted(ID);
    private static final String ACTUALIZADO = """
            {"id":"%s","nombre":"Nuevo nombre","email":null,"edad":0}
            """.formatted(ID);
    private static final String PARCIAL = """
            {"id":"%s","nombre":"Nuevo nombre","email":"nuevo@example.com","edad":32}
            """.formatted(ID);

    @Test
    void iniciaSinUsuariosDeEjemplo() throws Exception {
        try (var app = abrirAplicacion(UUID.randomUUID())) {
            http(app).perform(get(USUARIOS))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]", JsonCompareMode.STRICT));
        }
    }

    @Test
    void comparteAltasCambiosYBajasEntreContextosIndependientes() throws Exception {
        UUID database = UUID.randomUUID();
        try (var primera = abrirAplicacion(database)) {
            http(primera).perform(post(USUARIOS).contentType(MediaType.APPLICATION_JSON).content(USUARIO))
                    .andExpect(status().isCreated())
                    .andExpect(content().json(USUARIO, JsonCompareMode.STRICT));

            try (var segunda = abrirAplicacion(database)) {
                MockMvc lector = http(segunda);
                lector.perform(get(USUARIOS + "/{id}", ID))
                        .andExpect(status().isOk())
                        .andExpect(content().json(USUARIO, JsonCompareMode.STRICT));
                lector.perform(get(USUARIOS).queryParam("ignorado", "true"))
                        .andExpect(status().isOk())
                        .andExpect(content().json("[" + USUARIO + "]", JsonCompareMode.STRICT));
                lector.perform(put(USUARIOS + "/{id}", ID).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nombre\":\"Nuevo nombre\",\"email\":null,\"edad\":0}"))
                        .andExpect(status().isOk())
                        .andExpect(content().json(ACTUALIZADO, JsonCompareMode.STRICT));
                http(primera).perform(get(USUARIOS + "/{id}", ID))
                        .andExpect(status().isOk())
                        .andExpect(content().json(ACTUALIZADO, JsonCompareMode.STRICT));
                http(primera).perform(patch(USUARIOS + "/{id}", ID).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"nuevo@example.com\",\"edad\":32}"))
                        .andExpect(status().isOk())
                        .andExpect(content().json(PARCIAL, JsonCompareMode.STRICT));
                lector.perform(get(USUARIOS + "/{id}", ID))
                        .andExpect(status().isOk())
                        .andExpect(content().json(PARCIAL, JsonCompareMode.STRICT));
                lector.perform(delete(USUARIOS + "/{id}", ID))
                        .andExpect(status().isNoContent())
                        .andExpect(content().string(""));
                http(primera).perform(get(USUARIOS + "/{id}", ID))
                        .andExpect(status().isNotFound())
                        .andExpect(content().string(""));
                http(primera).perform(get(USUARIOS))
                        .andExpect(status().isOk())
                        .andExpect(content().json("[]", JsonCompareMode.STRICT));
            }
        }
    }

    @Test
    void preservaContratoHttp() throws Exception {
        try (var app = abrirAplicacion(UUID.randomUUID())) {
            MockMvc cliente = http(app);
            cliente.perform(get(USUARIOS + "/cabecera").header("User-Agent", "usuario-test"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Informacion del cliente (User-Agent): usuario-test"));
            cliente.perform(get(USUARIOS + "/cabecera"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(""));
            cliente.perform(get(USUARIOS + "/buscar"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(""));

            cliente.perform(post(USUARIOS).contentType(MediaType.APPLICATION_JSON).content(USUARIO))
                    .andExpect(status().isCreated())
                    .andExpect(content().json(USUARIO, JsonCompareMode.STRICT));
            cliente.perform(get(USUARIOS + "/buscar").queryParam("nombre", "ÚNICA %_"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[" + USUARIO + "]", JsonCompareMode.STRICT));
            cliente.perform(get(USUARIOS + "/buscar").queryParam("nombre", "ÚNICA _%"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));

            String creado = cliente.perform(post(USUARIOS).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Generado\",\"email\":\"nuevo@example.com\",\"edad\":18}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", matchesPattern("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")))
                    .andReturn().getResponse().getContentAsString();
            String generado = JsonPath.read(creado, "$.id");
            cliente.perform(get(USUARIOS + "/{id}", generado))
                    .andExpect(status().isOk())
                    .andExpect(content().json(creado, JsonCompareMode.STRICT));

            cliente.perform(put(USUARIOS + "/{id}", ID).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"ignorado\",\"nombre\":\"Nuevo nombre\"}"))
                    .andExpect(status().isBadRequest()).andExpect(content().string(""));
            cliente.perform(get(USUARIOS + "/{id}", ID))
                    .andExpect(status().isOk())
                    .andExpect(content().json(USUARIO, JsonCompareMode.STRICT));
            cliente.perform(post(USUARIOS).contentType(MediaType.APPLICATION_JSON).content(ACTUALIZADO))
                    .andExpect(status().isCreated())
                    .andExpect(content().json(ACTUALIZADO, JsonCompareMode.STRICT));
            cliente.perform(put(USUARIOS + "/{id}", ID).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"ignorado\",\"nombre\":\"Nuevo nombre\",\"edad\":0}"))
                    .andExpect(status().isOk())
                    .andExpect(content().json(ACTUALIZADO, JsonCompareMode.STRICT));
            cliente.perform(patch(USUARIOS + "/{id}", ID).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"ignorado\",\"email\":\"nuevo@example.com\",\"edad\":32}"))
                    .andExpect(status().isOk())
                    .andExpect(content().json(PARCIAL, JsonCompareMode.STRICT));

            String ausente = "inexistente";
            cliente.perform(get(USUARIOS + "/{id}", ausente))
                    .andExpect(status().isNotFound()).andExpect(content().string(""));
            cliente.perform(put(USUARIOS + "/{id}", ausente).contentType(MediaType.APPLICATION_JSON).content(USUARIO))
                    .andExpect(status().isNotFound()).andExpect(content().string(""));
            cliente.perform(patch(USUARIOS + "/{id}", ausente).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotFound()).andExpect(content().string(""));
            cliente.perform(delete(USUARIOS + "/{id}", ausente))
                    .andExpect(status().isNotFound()).andExpect(content().string(""));
            cliente.perform(get(USUARIOS + "/{id}", ausente))
                    .andExpect(status().isNotFound()).andExpect(content().string(""));
            cliente.perform(get(USUARIOS + "/{id}", ID))
                    .andExpect(status().isOk())
                    .andExpect(content().json(PARCIAL, JsonCompareMode.STRICT));
            cliente.perform(delete(USUARIOS + "/{id}", ID))
                    .andExpect(status().isNoContent()).andExpect(content().string(""));
            cliente.perform(get(USUARIOS + "/{id}", ID))
                    .andExpect(status().isNotFound()).andExpect(content().string(""));
        }
    }

    private static ConfigurableApplicationContext abrirAplicacion(UUID database) {
        return new SpringApplicationBuilder(DemoApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.config.location=optional:classpath:/usuario-test-only.properties",
                        "--spring.datasource.url=jdbc:h2:mem:usuario-" + database,
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.datasource.username=sa", "--spring.datasource.password=",
                        "--spring.jpa.hibernate.ddl-auto=none", "--spring.sql.init.mode=always",
                        "--spring.sql.init.schema-locations=classpath:usuario-schema.sql");
    }

    private static MockMvc http(ConfigurableApplicationContext app) {
        return standaloneSetup(app.getBean(UsuarioController.class)).build();
    }
}
