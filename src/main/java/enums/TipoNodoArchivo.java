package enums;

/**
 * Tipo de entrada dentro del sistema de archivos jerárquico de ÁvilaOS
 * (archivo regular o directorio).
 */
public enum TipoNodoArchivo {
    ARCHIVO("Archivo", "FILE"),
    DIRECTORIO("Directorio", "DIRECTORY");

    private final String etiqueta;
    private final String codigoJson;

    TipoNodoArchivo(String etiqueta, String codigoJson) {
        this.etiqueta = etiqueta;
        this.codigoJson = codigoJson;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getCodigoJson() {
        return codigoJson;
    }

    public boolean esDirectorio() {
        return this == DIRECTORIO;
    }

    public boolean esArchivo() {
        return this == ARCHIVO;
    }

    public static TipoNodoArchivo desdeTexto(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return ARCHIVO;
        }
        String limpio = texto.trim().toUpperCase();
        if (limpio.startsWith("DIR") || limpio.startsWith("CARP")) {
            return DIRECTORIO;
        }
        return ARCHIVO;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
