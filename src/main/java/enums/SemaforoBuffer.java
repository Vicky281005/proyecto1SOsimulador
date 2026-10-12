package enums;

public enum SemaforoBuffer {
    NINGUNO("Ninguno"),
    VACIOS("Semáforo Espacios Vacíos"),
    LLENOS("Semáforo Elementos Llenos"),
    MUTEX("Semáforo Exclusión Mutua (Mutex)");

    private final String etiqueta;

    SemaforoBuffer(String etiqueta) {
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
