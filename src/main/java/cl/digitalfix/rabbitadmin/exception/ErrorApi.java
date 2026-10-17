package cl.digitalfix.rabbitadmin.exception;

public class ErrorApi extends RuntimeException {
    private final int status;
    private final String codigo;
    public ErrorApi(int status, String codigo, String mensaje) {
        super(mensaje); this.status = status; this.codigo = codigo;
    }
    public int status() { return status; }
    public String codigo() { return codigo; }
}
