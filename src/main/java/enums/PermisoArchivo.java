package enums;

/**
 * Nivel de visibilidad y acceso de un archivo o directorio en el sistema de archivos
 * de ÁvilaOS.
 */
public enum PermisoArchivo {
    PUBLICO("Público", "public"),
    PRIVADO("Privado", "private");

    private final String etiqueta;
    private final String codigoJson;

    PermisoArchivo(String etiqueta, String codigoJson) {
        this.etiqueta = etiqueta;
        this.codigoJson = codigoJson;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getCodigoJson() {
        return codigoJson;
    }

    public boolean esPublico() {
        return this == PUBLICO;
    }

    /**
     * Convierte una cadena de configuración ("public", "private", "público", "privado")
     * a su constante correspondiente.
     *
     * @param texto cadena a interpretar.
     * @return el permiso correspondiente, o {@link #PUBLICO} por defecto.
     */
    public static PermisoArchivo desdeTexto(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return PUBLICO;
        }
        String limpio = texto.trim().toLowerCase();
        if (limpio.startsWith("priv")) {
            return PRIVADO;
        }
        return PUBLICO;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
