package enums;

/**
 * Modo de ejecución del procesador.
 * USUARIO corresponde a los programas de usuario y KERNEL a las operaciones
 * del sistema operativo (semWait, semSignal, acceso a buffers, etc.).
 */
public enum ModoCPU {
    USUARIO("Usuario"),
    KERNEL("Kernel");

    private final String etiqueta;

    ModoCPU(String etiqueta) {
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