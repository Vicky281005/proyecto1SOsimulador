package config;

/**
 * Formatos de archivo soportados para la persistencia de la configuración del simulador.
 */
public enum FormatoPersistencia {
    JSON("Archivos JSON (*.json)", "json"),
    CSV("Archivos CSV (*.csv)", "csv");

    private final String descripcion;
    private final String extension;

    FormatoPersistencia(String descripcion, String extension) {
        this.descripcion = descripcion;
        this.extension = extension;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getExtension() {
        return extension;
    }

    @Override
    public String toString() {
        return descripcion;
    }
}
