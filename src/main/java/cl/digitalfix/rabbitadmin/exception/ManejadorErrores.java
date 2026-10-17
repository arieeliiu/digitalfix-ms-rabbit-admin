package cl.digitalfix.rabbitadmin.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ManejadorErrores {
    public record RespuestaError(int status, String codigo, String mensaje) {}
    @ExceptionHandler(ErrorApi.class)
    ResponseEntity<RespuestaError> dominio(ErrorApi error) {
        return ResponseEntity.status(error.status()).body(new RespuestaError(error.status(),error.codigo(),error.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class})
    ResponseEntity<RespuestaError> datos(Exception error) {
        return ResponseEntity.badRequest().body(new RespuestaError(400,"DATOS_INVALIDOS","JSON o parametros invalidos"));
    }
}
