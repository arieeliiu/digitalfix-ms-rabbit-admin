package cl.digitalfix.rabbitadmin;

import java.time.Instant;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import cl.digitalfix.rabbitadmin.service.RabbitAdminService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
class SeguridadHttpTests extends JwtPruebas {
    @Autowired MockMvc mvc;
    @MockitoBean RabbitAdminService servicio;
    static final String COLA = "{\"name\":\"admin.prueba\",\"durable\":true,\"exclusive\":false,\"autoDelete\":false,\"type\":\"classic\"}";

    @Test void saludPublica() throws Exception { mvc.perform(get("/healthz")).andExpect(status().isOk()); }
    @Test void sinToken() throws Exception {
        mvc.perform(post("/api/rabbit/queues").contentType("application/json").content(COLA)).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }
    @Test void tokenIlegible() throws Exception {
        mvc.perform(get("/api/rabbit/queues/admin.prueba").header("Authorization","Bearer invalido"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }
    @Test void clienteYOperadorRechazados() throws Exception {
        for (String rol : new String[]{"Cliente","Operador"})
            mvc.perform(post("/api/rabbit/queues").header("Authorization","Bearer "+token(rol,"access_as_user"))
                    .contentType("application/json").content(COLA)).andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }
    @Test void adminSinScopeRechazado() throws Exception {
        mvc.perform(post("/api/rabbit/queues").header("Authorization","Bearer "+token("Admin","otro"))
                .contentType("application/json").content(COLA)).andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }
    @Test void adminConScopeAceptado() throws Exception {
        mvc.perform(post("/api/rabbit/queues").header("Authorization","Bearer "+admin())
                .contentType("application/json").content(COLA)).andExpect(status().isCreated());
        verify(servicio).crearCola(any());
    }
    @Test void firmaIssuerAudienciaYExpiracionInvalidos() throws Exception {
        String[] invalidos = {
                token("Admin","access_as_user",ISSUER,AUDIENCE,Instant.now().minusSeconds(180),CLAVE),
                token("Admin","access_as_user","https://otro.invalid",AUDIENCE,Instant.now().plusSeconds(300),CLAVE),
                token("Admin","access_as_user",ISSUER,"otra-api",Instant.now().plusSeconds(300),CLAVE),
                token("Admin","access_as_user",ISSUER,AUDIENCE,Instant.now().plusSeconds(300),new RSAKeyGenerator(2048).generate())};
        for (String jwt : invalidos)
            mvc.perform(get("/api/rabbit/queues/admin.prueba").header("Authorization","Bearer "+jwt))
                    .andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }
    @Test void jsonInvalidoYPropiedadesDesconocidas() throws Exception {
        for (String cuerpo : new String[]{"{invalido",COLA.replace("classic","quorum"),COLA.replace("true","false"),
                COLA.replace("admin.prueba",""),COLA.replace("admin.prueba","x".repeat(256)),COLA.replace("}",",\"arguments\":{}}")})
            mvc.perform(post("/api/rabbit/queues").header("Authorization","Bearer "+admin())
                    .contentType("application/json").content(cuerpo)).andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }
    @Test void erroresDominioConFormatoConsistente() throws Exception {
        doThrow(new cl.digitalfix.rabbitadmin.exception.ErrorApi(503,"BROKER_NO_DISPONIBLE","No disponible"))
                .when(servicio).crearCola(any());
        mvc.perform(post("/api/rabbit/queues").header("Authorization","Bearer "+admin())
                .contentType("application/json").content(COLA)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("BROKER_NO_DISPONIBLE"));
    }
}
