package cl.digitalfix.rabbitadmin.controller;

import java.net.URI;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import cl.digitalfix.rabbitadmin.dto.Solicitudes.*;
import cl.digitalfix.rabbitadmin.service.RabbitAdminService;

@RestController
@RequestMapping("/api/rabbit")
public class RabbitAdminController {
    private final RabbitAdminService servicio;
    public RabbitAdminController(RabbitAdminService servicio) { this.servicio=servicio; }
    @PostMapping("/queues")
    ResponseEntity<Void> crearCola(@Valid @RequestBody Cola datos) {
        servicio.crearCola(datos); return ResponseEntity.created(URI.create("/api/rabbit/queues/"+datos.name())).build();
    }
    @GetMapping("/queues/{name}")
    EstadoCola cola(@PathVariable String name) { return servicio.consultarCola(name); }
    @DeleteMapping("/queues/{name}")
    ResponseEntity<Void> eliminarCola(@PathVariable String name) {
        servicio.eliminarCola(name); return ResponseEntity.noContent().build();
    }
    @PostMapping("/exchanges")
    ResponseEntity<Void> crearExchange(@Valid @RequestBody Intercambio datos) {
        servicio.crearExchange(datos); return ResponseEntity.created(URI.create("/api/rabbit/exchanges/"+datos.name())).build();
    }
    @GetMapping("/exchanges/{name}")
    Map<String,Object> exchange(@PathVariable String name) {
        servicio.consultarExchange(name); return Map.of("name",name,"existe",true);
    }
    @DeleteMapping("/exchanges/{name}")
    ResponseEntity<Void> eliminarExchange(@PathVariable String name) {
        servicio.eliminarExchange(name); return ResponseEntity.noContent().build();
    }
    @PostMapping("/bindings")
    ResponseEntity<Void> crearBinding(@Valid @RequestBody Enlace datos) {
        servicio.crearBinding(datos); return ResponseEntity.status(201).build();
    }
    @DeleteMapping("/bindings")
    ResponseEntity<Void> eliminarBinding(@Valid @RequestBody Enlace datos) {
        servicio.eliminarBinding(datos); return ResponseEntity.noContent().build();
    }
}
