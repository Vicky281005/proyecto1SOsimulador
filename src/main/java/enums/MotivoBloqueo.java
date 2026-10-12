package enums;

/** Causa por la que un proceso se encuentra en estado BLOQUEADO. */
public enum MotivoBloqueo {
    NINGUNO("Ninguno"),
    SEMAFORO_VACIO("Esperando semáforo (Buffer vacío)"),
    SEMAFORO_LLENO("Esperando semáforo (Buffer lleno)"),
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