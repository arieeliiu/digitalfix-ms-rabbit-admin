package cl.digitalfix.rabbitadmin.service;

import java.util.function.Supplier;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ShutdownSignalException;
import jakarta.validation.Validator;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;
import cl.digitalfix.rabbitadmin.dto.Solicitudes.*;
import cl.digitalfix.rabbitadmin.dto.Solicitudes;
import cl.digitalfix.rabbitadmin.exception.ErrorApi;

@Service
public class RabbitAdminService {
    private final RabbitAdmin admin;
    private final Validator validator;
    public RabbitAdminService(RabbitAdmin admin, Validator validator) {
        this.admin = admin; this.validator = validator;
    }

    public void crearCola(Cola datos) {
        validar(datos); nombrePermitido(datos.name());
        ejecutar(() -> {
            String nombre = admin.declareQueue(QueueBuilder.durable(datos.name())
                    .withArgument("x-queue-type","classic").build());
            if (nombre == null) throw new ErrorApi(503,"BROKER_NO_DISPONIBLE","Declaracion no confirmada");
            return null;
        });
    }

    public EstadoCola consultarCola(String nombre) {
        nombrePermitido(nombre);
        // getQueueInfo absorbe algunos errores: usar consulta pasiva que los propague.
        return ejecutar(() -> admin.getRabbitTemplate().execute(c -> {
            var estado = c.queueDeclarePassive(nombre);
            return new EstadoCola(estado.getQueue(),estado.getMessageCount(),estado.getConsumerCount());
        }));
    }

    public void eliminarCola(String nombre) {
        consultarCola(nombre);
        ejecutar(() -> { admin.deleteQueue(nombre,true,true); return null; });
    }

    public void crearExchange(Intercambio datos) {
        validar(datos); nombrePermitido(datos.name());
        ejecutar(() -> {
            Exchange exchange = datos.type().equals("direct") ? new DirectExchange(datos.name(),true,false)
                    : new TopicExchange(datos.name(),true,false);
            admin.declareExchange(exchange); return null;
        });
    }

    public void consultarExchange(String nombre) {
        nombrePermitido(nombre);
        ejecutar(() -> admin.getRabbitTemplate().execute(c -> { c.exchangeDeclarePassive(nombre); return null; }));
    }

    public void eliminarExchange(String nombre) {
        consultarExchange(nombre);
        // RabbitAdmin.deleteExchange no ofrece ifUnused y puede ocultar IOException.
        // El canal de su RabbitTemplate permite exigir eliminacion sin bindings.
        ejecutar(() -> admin.getRabbitTemplate().execute(c -> { c.exchangeDelete(nombre,true); return null; }));
    }

    public void crearBinding(Enlace datos) {
        comprobarExtremos(datos);
        ejecutar(() -> { admin.declareBinding(binding(datos)); return null; });
    }

    public void eliminarBinding(Enlace datos) {
        comprobarExtremos(datos);
        ejecutar(() -> { admin.removeBinding(binding(datos)); return null; });
    }

    private void comprobarExtremos(Enlace datos) {
        validar(datos);
        // Comprobar ambos nombres ANTES de realizar cualquier llamada al broker.
        nombrePermitido(datos.queue()); nombrePermitido(datos.exchange());
        consultarCola(datos.queue()); consultarExchange(datos.exchange());
    }
    private Binding binding(Enlace datos) {
        return new Binding(datos.queue(),Binding.DestinationType.QUEUE,datos.exchange(),datos.routingKey(),null);
    }
    private void validar(Object datos) {
        if (datos == null || !validator.validate(datos).isEmpty())
            throw new ErrorApi(400,"DATOS_INVALIDOS","Parametros invalidos o incompletos");
    }
    private void nombrePermitido(String nombre) {
        if (nombre == null || nombre.length()>255 || !nombre.matches(Solicitudes.NOMBRE))
            throw new ErrorApi(400,"NOMBRE_INVALIDO","Nombre invalido: utilizar 1 a 255 caracteres ASCII permitidos");
        if (nombre.startsWith("amq.") || nombre.startsWith("rabbitmq.")
                || nombre.startsWith("cmd.") || nombre.startsWith("q.cmd."))
            throw new ErrorApi(403,"RECURSO_PROTEGIDO","El recurso pertenece al sistema o a la topologia funcional");
    }
    private <T> T ejecutar(Supplier<T> operacion) {
        try { return operacion.get(); }
        catch (ErrorApi e) { throw e; }
        catch (RuntimeException error) {
            for (Throwable e = error; e != null; e = e.getCause()) {
                if (e instanceof ShutdownSignalException cierre) {
                    int codigo = cierre.getReason() instanceof AMQP.Channel.Close canal ? canal.getReplyCode()
                            : cierre.getReason() instanceof AMQP.Connection.Close conexion ? conexion.getReplyCode() : 0;
                    if (codigo == 404) throw new ErrorApi(404,"NO_ENCONTRADO","El recurso no existe");
                    if (codigo == 403) throw new ErrorApi(403,"PERMISO_BROKER","El usuario RabbitMQ no tiene permiso");
                    if (codigo == 405 || codigo == 406)
                        throw new ErrorApi(409,"CONFLICTO_RECURSO","Propiedades incompatibles o recurso en uso");
                }
            }
            throw new ErrorApi(503,"BROKER_NO_DISPONIBLE","No se pudo confirmar la operacion con RabbitMQ");
        }
    }
}
