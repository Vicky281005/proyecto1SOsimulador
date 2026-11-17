package enums;

/**
 * Tipos de operación de Entrada/Salida sobre el disco simulado y el sistema de archivos.
 *
 * <p>Incluye las cuatro operaciones CRUD solicitadas por procesos de usuario/administrador
 * ({@link #CREAR}, {@link #LEER}, {@link #ACTUALIZAR}, {@link #ELIMINAR}) y las dos
 * operaciones del planificador a mediano plazo sobre el área de intercambio o swap
 * ({@link #SUSPENDER}, {@link #REACTIVAR}).</p>
 */
public enum TipoOperacionES {
    CREAR("Crear", "CREATE"),
    LEER("Leer", "READ"),
    ACTUALIZAR("Actualizar", "UPDATE"),
    ELIMINAR("Eliminar", "DELETE"),
    SUSPENDER("Suspender a Swap", "SUSPEND"),
    REACTIVAR("Reactivar de Swap", "REACTIVATE");

    private final String etiqueta;
    private final String codigoJson;

    TipoOperacionES(String etiqueta, String codigoJson) {
        this.etiqueta = etiqueta;
        this.codigoJson = codigoJson;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getCodigoJson() {
        return codigoJson;
    }

    /** Indica si la operación corresponde a una acción CRUD sobre el sistema de archivos. */
    public boolean esOperacionArchivo() {
        return this == CREAR || this == LEER || this == ACTUALIZAR || this == ELIMINAR;
    }

    /** Indica si la operación corresponde a suspensión o reactivación en el área de Swap. */
    public boolean esOperacionSwap() {
        return this == SUSPENDER || this == REACTIVAR;
    }

    /**
     * Convierte un texto (de archivo JSON o de la interfaz) al enum correspondiente.
     * Soporta tanto los códigos en inglés del formato JSON ("CREATE", "READ", "UPDATE", "DELETE")
     * como los nombres en español.
     *
     * @param texto cadena a interpretar.
     * @return el tipo de operación correspondiente, o {@link #LEER} por defecto si es nulo/desconocido.
     */
    public static TipoOperacionES desdeTexto(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return LEER;
        }
        String limpio = texto.trim().toUpperCase();
        return switch (limpio) {
            case "CREATE", "CREAR" -> CREAR;
            case "READ", "LEER" -> LEER;
            case "UPDATE", "ACTUALIZAR", "MODIFICAR", "RENOMBRAR" -> ACTUALIZAR;
            case "DELETE", "ELIMINAR", "BORRAR" -> ELIMINAR;
            case "SUSPEND", "SUSPENDER" -> SUSPENDER;
            case "REACTIVATE", "REACTIVAR" -> REACTIVAR;
            default -> LEER;
        };
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
