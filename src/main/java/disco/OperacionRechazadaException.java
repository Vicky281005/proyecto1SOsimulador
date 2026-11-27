package disco;

/**
 * Se lanza cuando el sistema de archivos rechaza una operación de E/S (permisos
 * insuficientes, ruta inexistente, falta de espacio...). La simulación no se
 * detiene: el proceso de E/S termina y el motivo queda registrado en el log.
 */
public class OperacionRechazadaException extends RuntimeException {

    public OperacionRechazadaException(String mensaje) {
        super(mensaje);
    }
}
