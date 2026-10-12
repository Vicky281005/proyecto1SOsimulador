package modelo;

import enums.SemaforoBuffer;
import enums.TipoInstruccion;

public class Instruccion {
    private final TipoInstruccion tipo;
    private final SemaforoBuffer semaforo; // Opcional para operaciones de semáforo
    private final String parametro;        // Ej: ID de buffer "B1", milisegundos, etc.

    // Constructor 1: Para instrucciones simples de CPU
    public Instruccion(TipoInstruccion tipo) {
        this(tipo, SemaforoBuffer.NINGUNO, "");
    }

    // Constructor 2: Para instrucciones con parámetro genérico (E/S, Producir, Consumir)
    public Instruccion(TipoInstruccion tipo, String parametro) {
        this(tipo, SemaforoBuffer.NINGUNO, parametro);
    }

    // Constructor 3: Completo para Semáforos y Buffers
    public Instruccion(TipoInstruccion tipo, SemaforoBuffer semaforo, String parametro) {
        this.tipo = tipo;
        this.semaforo = (semaforo != null) ? semaforo : SemaforoBuffer.NINGUNO;
        this.parametro = (parametro != null) ? parametro : "";
    }

    public TipoInstruccion getTipo() { return tipo; }
    public SemaforoBuffer getSemaforo() { return semaforo; }
    public String getParametro() { return parametro; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(tipo.getEtiqueta());
        if (semaforo != SemaforoBuffer.NINGUNO) {
            sb.append(" [").append(semaforo.getEtiqueta()).append("]");
        }
        if (!parametro.isEmpty()) {
            sb.append(" ").append(parametro);
        }
        return sb.toString();
    }
}