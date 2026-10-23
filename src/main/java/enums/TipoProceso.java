package enums;

/**
 * Naturaleza de un proceso según el recurso que más consume.
 * CPU_BOUND e IO_BOUND se satisfacen por ciclos de ejecución, mientras que
 * PRODUCTOR y CONSUMIDOR trabajan sobre un buffer de capacidad limitada.
 */
public enum TipoProceso {
    CPU_BOUND("CPU Bound"),
    IO_BOUND("I/O Bound"),
    PRODUCTOR("Productor"),
    CONSUMIDOR("Consumidor");

    private final String etiqueta;

    TipoProceso(String etiqueta) {
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