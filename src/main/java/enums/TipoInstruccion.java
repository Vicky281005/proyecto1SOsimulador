package enums;

/**
 * Tipos de instrucción que puede contener el programa de un proceso.
 * Cada instrucción ocupa exactamente un ciclo de reloj.
 */
public enum TipoInstruccion {
    CPU("Instrucción CPU"),
    READ_IO("Lectura E/S"),
    WRITE_IO("Escritura E/S"),
    SEM_WAIT("Espera Semáforo (Wait)"),
    SEM_SIGNAL("Señal Semáforo (Signal)"),
    PRODUCIR("Producir Elemento"),
    CONSUMIR("Consumir Elemento"),
    OPERACION_ES("Operación de E/S sobre el sistema de archivos");

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
