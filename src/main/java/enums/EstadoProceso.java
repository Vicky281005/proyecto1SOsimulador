package enums;

/**
 * Modelo de estados de un proceso 
 *
 * Para los procesos bloqueados se decidió mantener un único estado BLOQUEADO
 * y distinguir la causa con el enum {@link MotivoBloqueo}. Esto evita multiplicar
 * los estados del diagrama y permite mostrar en la GUI el motivo concreto
 * (semáforo lleno, semáforo vacío, mutex, latencia de red o espera de E/S).
 */
public enum EstadoProceso {
    NUEVO("Nuevo"),
    LISTO("Listo"),
    EJECUCION("Ejecución"),
    BLOQUEADO("Bloqueado"),
    TERMINADO("Terminado");

    private final String etiqueta;

    EstadoProceso(String etiqueta) {
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




