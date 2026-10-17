package cl.digitalfix.rabbitadmin;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import cl.digitalfix.rabbitadmin.dto.Solicitudes.*;
import cl.digitalfix.rabbitadmin.exception.ErrorApi;
import cl.digitalfix.rabbitadmin.service.RabbitAdminService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReglasServiceTests {
    @Test void recursosProtegidosNoLleganAlBroker() {
        var admin = mock(RabbitAdmin.class);
        try(var validacion = Validation.buildDefaultValidatorFactory()) {
            var servicio = new RabbitAdminService(admin,validacion.getValidator());
            for (String nombre : new String[]{"cmd.direct","cmd.topic","cmd.dead.dlx","q.cmd.email","q.cmd.dispatch",
                    "q.cmd.report","q.cmd.email.dlq","q.cmd.dispatch.dlq","q.cmd.report.dlq","amq.direct","rabbitmq.interno"}) {
                assertEquals(403,assertThrows(ErrorApi.class,() -> servicio.eliminarCola(nombre)).status());
                assertEquals(403,assertThrows(ErrorApi.class,() -> servicio.eliminarExchange(nombre)).status());
                assertEquals(403,assertThrows(ErrorApi.class,() -> servicio.crearCola(new Cola(nombre,true,false,false,"classic"))).status());
                assertEquals(403,assertThrows(ErrorApi.class,() -> servicio.crearExchange(new Intercambio(nombre,"direct",true,false))).status());
                assertEquals(403,assertThrows(ErrorApi.class,() -> servicio.crearBinding(new Enlace("admin.q",nombre,"a.*"))).status());
                assertEquals(403,assertThrows(ErrorApi.class,() -> servicio.eliminarBinding(new Enlace(nombre,"admin.x","a.*"))).status());
            }
            verifyNoInteractions(admin);
        }
    }
    @Test void errorConexionSeTraduceA503() {
        var admin = mock(RabbitAdmin.class);
        when(admin.declareQueue(any())).thenThrow(new AmqpConnectException(new java.net.ConnectException("no disponible")));
        try(var validacion = Validation.buildDefaultValidatorFactory()) {
            var servicio = new RabbitAdminService(admin,validacion.getValidator());
            assertEquals(503,assertThrows(ErrorApi.class,() -> servicio.crearCola(new Cola("admin.q",true,false,false,"classic"))).status());
        }
    }
}
