package cl.digitalfix.rabbitadmin.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class SaludController {
    @GetMapping("/healthz")
    Map<String,String> salud() { return Map.of("estado","activo","servicio","digitalfix-ms-rabbit-admin"); }
}
