package enums;

/**
 * Políticas de planificación de solicitudes de Entrada/Salida para el disco simulado (SD)
 * de ÁvilaOS.
 */
public enum PoliticaDisco {
    FIFO("FIFO (First In, First Out)", "FIFO"),
    SSTF("SSTF (Shortest Seek Time First)", "SSTF"),
    SCAN("SCAN (Algoritmo del Ascensor)", "SCAN"),
    C_SCAN("C-SCAN (Circular SCAN)", "C-SCAN");

    private final String etiqueta;
    private final String codigoJson;

    PoliticaDisco(String etiqueta, String codigoJson) {
        this.etiqueta = etiqueta;
        this.codigoJson = codigoJson;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getCodigoJson() {
        return codigoJson;
    }

    /**
     * Convierte una cadena de configuración (ej. "FIFO", "SSTF", "SCAN", "C-SCAN")
     * a su constante correspondiente.
     *
     * @param texto nombre o código de la política.
     * @return la política correspondiente, o {@link #FIFO} por defecto.
     */
    public static PoliticaDisco desdeTexto(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return FIFO;
        }
        String limpio = texto.trim().toUpperCase();
        return switch (limpio) {
            case "FIFO", "FCFS" -> FIFO;
            case "SSTF" -> SSTF;
            case "SCAN" -> SCAN;
            case "C-SCAN", "CSCAN", "C_SCAN" -> C_SCAN;
            default -> FIFO;
        };
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
