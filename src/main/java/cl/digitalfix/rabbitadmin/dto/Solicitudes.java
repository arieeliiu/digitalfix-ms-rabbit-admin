package cl.digitalfix.rabbitadmin.dto;

import jakarta.validation.constraints.*;

public final class Solicitudes {
    private Solicitudes() {}
    public static final String NOMBRE = "[A-Za-z0-9][A-Za-z0-9._-]*";

    public record Cola(@NotBlank @Size(max=255) @Pattern(regexp=NOMBRE) String name,
                       @NotNull @AssertTrue Boolean durable,
                       @NotNull @AssertFalse Boolean exclusive,
                       @NotNull @AssertFalse Boolean autoDelete,
                       @NotNull @Pattern(regexp="classic") String type) {}

    public record Intercambio(@NotBlank @Size(max=255) @Pattern(regexp=NOMBRE) String name,
                             @NotNull @Pattern(regexp="direct|topic") String type,
                             @NotNull @AssertTrue Boolean durable,
                             @NotNull @AssertFalse Boolean autoDelete) {}

    public record Enlace(@NotBlank @Size(max=255) @Pattern(regexp=NOMBRE) String queue,
                         @NotBlank @Size(max=255) @Pattern(regexp=NOMBRE) String exchange,
                         @NotBlank @Size(max=255)
                         @Pattern(regexp="([A-Za-z0-9_-]+|\\*|#)(\\.([A-Za-z0-9_-]+|\\*|#))*") String routingKey) {}
    public record EstadoCola(String name, int mensajesListos, int consumidores) {}
}
