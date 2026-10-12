package enums;

/**
 * Políticas de planificación que puede usar un computador de ÁvilaOS.
 * Cada computador tiene su propia política y puede cambiarla en tiempo de
 * ejecución de forma independiente de los demás.
 */
public enum PoliticaPlanificacion {
    FCFS("First Come First Served (FCFS)"),
    ROUND_ROBIN("Round Robin (RR)"),
    EDF("Earliest Deadline First (EDF)"),
    PRIORIDAD("Prioridades Apropiativas");

    private final String etiqueta;

    PoliticaPlanificacion(String etiqueta) {
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