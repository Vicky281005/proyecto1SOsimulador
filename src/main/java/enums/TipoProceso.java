package enums;

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