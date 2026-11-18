package enums;

/**
 * Modelo de siete estados de un proceso.
 * Los estados suspendidos indican que el proceso fue sacado de la RAM
 * y su imagen está en el área de intercambio (swap) del disco.
 * La causa de un bloqueo se distingue con {@link MotivoBloqueo}.
 */
public enum EstadoProceso {
    NUEVO("Nuevo"),
    LISTO("Listo"),
    EJECUCION("Ejecución"),
    BLOQUEADO("Bloqueado"),
    LISTO_SUSPENDIDO("Listo/Suspendido"),
    BLOQUEADO_SUSPENDIDO("Bloqueado/Suspendido"),
    TERMINADO("Terminado");

    private final String etiqueta;

    EstadoProceso(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Indica si el proceso está fuera de la RAM (en swap). */
    public boolean estaSuspendido() {
        return this == LISTO_SUSPENDIDO || this == BLOQUEADO_SUSPENDIDO;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
