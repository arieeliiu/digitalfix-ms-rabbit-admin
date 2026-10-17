package cl.digitalfix.rabbitadmin.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    @Bean
    RabbitAdmin administrador(ConnectionFactory connectionFactory) {
        var admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(false);
        admin.setIgnoreDeclarationExceptions(false);
        admin.setRedeclareManualDeclarations(false);
        return admin;
    }
}
