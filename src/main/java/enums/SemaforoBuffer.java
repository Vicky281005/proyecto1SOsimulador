package enums;

/**
 * Identifica el semáforo asociado a un buffer de capacidad limitada
 * (espacios vacíos, elementos llenos o mutex).
 * Indica sobre cuál semáforo opera una instrucción o en cuál quedó esperando
 * un proceso. No confundirse con { MotivoBloqueo}, que describe la causa
 * del bloqueo.
 */
public enum SemaforoBuffer {
    NINGUNO("Ninguno"),
    VACIOS("Semáforo Espacios Vacíos"),
    LLENOS("Semáforo Elementos Llenos"),
    MUTEX("Semáforo Exclusión Mutua (Mutex)");

    private final String etiqueta;

    SemaforoBuffer(String etiqueta) {
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
