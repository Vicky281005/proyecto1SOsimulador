package enums;

public enum TipoInstruccion {
    CPU("Instrucción CPU"),
    READ_IO("Lectura E/S"),
    WRITE_IO("Escritura E/S"),
    SEM_WAIT("Espera Semáforo (Wait)"),
    SEM_SIGNAL("Señal Semáforo (Signal)"),
    PRODUCIR("Producir Elemento"),
    CONSUMIR("Consumir Elemento");

    private final String etiqueta;

    TipoInstruccion(String etiqueta) {
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
