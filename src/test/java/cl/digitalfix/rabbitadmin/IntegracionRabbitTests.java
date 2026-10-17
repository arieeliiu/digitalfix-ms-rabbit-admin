package cl.digitalfix.rabbitadmin;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import jakarta.validation.Validator;
import cl.digitalfix.rabbitadmin.service.RabbitAdminService;
import cl.digitalfix.rabbitadmin.dto.Solicitudes.Cola;
import cl.digitalfix.rabbitadmin.exception.ErrorApi;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="RABBITMQ_TEST_ADDRESSES",matches=".+")
class IntegracionRabbitTests extends JwtPruebas {
    @Autowired MockMvc mvc;
    @Autowired RabbitAdmin admin;
    @Autowired Validator validator;

    @Test void cicloRealDirectYTopicConProteccionesYConflictos() throws Exception {
        String prefijo="admin.it."+UUID.randomUUID();
        String queue=prefijo+".q", direct=prefijo+".direct", topic=prefijo+".topic";
        String auth="Bearer "+admin();
        String cola="{\"name\":\""+queue+"\",\"durable\":true,\"exclusive\":false,\"autoDelete\":false,\"type\":\"classic\"}";
        try {
            mvc.perform(post("/api/rabbit/queues").header("Authorization",auth).contentType("application/json").content(cola))
                    .andExpect(status().isCreated());
            mvc.perform(get("/api/rabbit/queues/"+queue).header("Authorization",auth)).andExpect(status().isOk());
            for (String tipo : new String[]{"direct","topic"}) {
                String exchange=tipo.equals("direct")?direct:topic;
                String cuerpo="{\"name\":\""+exchange+"\",\"type\":\""+tipo+"\",\"durable\":true,\"autoDelete\":false}";
                mvc.perform(post("/api/rabbit/exchanges").header("Authorization",auth).contentType("application/json").content(cuerpo))
                        .andExpect(status().isCreated());
                String key=tipo.equals("direct")?"prueba.uno":"prueba.*";
                String binding="{\"queue\":\""+queue+"\",\"exchange\":\""+exchange+"\",\"routingKey\":\""+key+"\"}";
                mvc.perform(post("/api/rabbit/bindings").header("Authorization",auth).contentType("application/json").content(binding))
                        .andExpect(status().isCreated());
                mvc.perform(delete("/api/rabbit/exchanges/"+exchange).header("Authorization",auth)).andExpect(status().isConflict());
                publicar(exchange,"prueba.uno");
                mvc.perform(delete("/api/rabbit/queues/"+queue).header("Authorization",auth)).andExpect(status().isConflict());
                assertNotNull(admin.getRabbitTemplate().receive(queue,1000));
                mvc.perform(delete("/api/rabbit/bindings").header("Authorization",auth).contentType("application/json").content(binding))
                        .andExpect(status().isNoContent());
                publicar(exchange,"prueba.uno");
                assertNull(admin.getRabbitTemplate().receive(queue,300));
                // AMQP unbind es idempotente cuando ambos extremos existen.
                mvc.perform(delete("/api/rabbit/bindings").header("Authorization",auth).contentType("application/json").content(binding))
                        .andExpect(status().isNoContent());
            }
            String conflicto="{\"name\":\""+direct+"\",\"type\":\"topic\",\"durable\":true,\"autoDelete\":false}";
            mvc.perform(post("/api/rabbit/exchanges").header("Authorization",auth).contentType("application/json").content(conflicto))
                    .andExpect(status().isConflict());
            for (String protegido : new String[]{"q.cmd.email","q.cmd.dispatch","q.cmd.report","amq.direct"})
                mvc.perform(delete("/api/rabbit/queues/"+protegido).header("Authorization",auth)).andExpect(status().isForbidden());
            mvc.perform(delete("/api/rabbit/queues/"+queue).header("Authorization",auth)).andExpect(status().isNoContent());
            mvc.perform(get("/api/rabbit/queues/"+queue).header("Authorization",auth)).andExpect(status().isNotFound());
            for (String ex : new String[]{direct,topic}) {
                mvc.perform(delete("/api/rabbit/exchanges/"+ex).header("Authorization",auth)).andExpect(status().isNoContent());
                mvc.perform(get("/api/rabbit/exchanges/"+ex).header("Authorization",auth)).andExpect(status().isNotFound());
            }
        } finally {
            // Unicamente recursos con UUID propio; jamas se purga una cola funcional.
            admin.deleteQueue(queue); admin.deleteExchange(direct); admin.deleteExchange(topic);
        }
    }

    private void publicar(String exchange,String key) {
        admin.getRabbitTemplate().execute(c -> {
            c.confirmSelect(); c.basicPublish(exchange,key,null,"prueba".getBytes());
            c.waitForConfirmsOrDie(5000); return null;
        });
    }

    @Test void conexionRealRechazadaSeTraduceA503() {
        var cf=new CachingConnectionFactory("127.0.0.1",1);
        cf.setConnectionTimeout(1000);
        try {
            var servicio=new RabbitAdminService(new RabbitAdmin(cf),validator);
            assertEquals(503,assertThrows(ErrorApi.class,() -> servicio.crearCola(
                    new Cola("admin.inaccesible",true,false,false,"classic"))).status());
        } finally { cf.destroy(); }
    }
}
