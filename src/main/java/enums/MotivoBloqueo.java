package enums;

/**
 * Causa por la que un proceso se encuentra en estado BLOQUEADO.
 * A diferencia de { SemaforoBuffer}, que identifica el semáforo, este
 * enum describe el motivo: esperar espacio, elementos, mutex, latencia de red
 * o una operación de E/S.
 */
public enum MotivoBloqueo {
    NINGUNO("Ninguno"),
    SEMAFORO_VACIO("Esperando espacio libre (buffer lleno)"),
    SEMAFORO_LLENO("Esperando elemento (buffer vacío)"),
    MUTEX("Esperando exclusión mutua (Mutex)"),
    LATENCIA_RED("Esperando latencia de red"),
    ESPERA_IO("Esperando operación de E/S");

    private final String etiqueta;

    MotivoBloqueo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}