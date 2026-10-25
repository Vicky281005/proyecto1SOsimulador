package hardware;

import enums.ModoCPU;
import enums.TipoInstruccion;
import modelo.Instruccion;
import modelo.PCB;

/**
 * Procesador simulado de un computador de ÁvilaOS.
 *
 * <p>Es un componente reactivo al reloj: en cada ciclo ejecuta exactamente una
 * instrucción del proceso que tenga cargado. Por ahora toda instrucción
 * consume un ciclo; la semántica de las operaciones de sistema (bloqueo por
 * semáforo, acceso a buffers, E/S, etc.) la aplicará el kernel en una etapa
 * posterior.</p>
 */
public class CPU implements ComponenteReloj {

    private final String nombre;

    /** Proceso en ejecución, o null si la CPU está ociosa. */
    private PCB procesoActual;

    /** Modo en el que se ejecutó la última instrucción. */
    private ModoCPU modo;

    /** Total de instrucciones ejecutadas por esta CPU durante la simulación. */
    private long ciclosEjecutados;

    /** Instrucciones ejecutadas por el proceso cargado actualmente (para el quantum). */
    private long ciclosProcesoActual;

    public CPU(String nombre) {
        this.nombre = (nombre != null && !nombre.isEmpty()) ? nombre : "CPU";
        this.procesoActual = null;
        this.modo = ModoCPU.USUARIO;
        this.ciclosEjecutados = 0;
        this.ciclosProcesoActual = 0;
    }

    /**
     * Carga un proceso para que comience a ejecutarse en el siguiente ciclo.
     * Reinicia el contador de ciclos del proceso (base del quantum).
     *
     * @param proceso proceso a ejecutar.
     */
    public void cargarProceso(PCB proceso) {
        this.procesoActual = proceso;
        this.modo = ModoCPU.USUARIO;
        this.ciclosProcesoActual = 0;
    }

    /**
     * Libera la CPU al terminar o al desalojar el proceso en ejecución.
     * El kernel debe invocarlo cuando el proceso deja de ser ejecutable
     * (terminó o se bloqueó).
     */
    public void liberar() {
        this.procesoActual = null;
        this.modo = ModoCPU.USUARIO;
        this.ciclosProcesoActual = 0;
    }

    /** Indica si la CPU no tiene ningún proceso cargado. */
    public boolean estaOciosa() {
        return procesoActual == null;
    }

    @Override
    public void tick(long cicloActual) {
        if (procesoActual == null) {
            return;
        }

        Instruccion instruccion = procesoActual.getInstruccionActual();
        if (instruccion == null) {
            // El programa se agotó: la CPU queda libre hasta que el kernel decida.
            liberar();
            return;
        }

        this.modo = esDeModoKernel(instruccion.getTipo()) ? ModoCPU.KERNEL : ModoCPU.USUARIO;

        // Una instrucción ocupa exactamente un ciclo de reloj.
        procesoActual.avanzarPasoCpu(cicloActual);
        this.ciclosEjecutados++;
        this.ciclosProcesoActual++;
    }

    /** Instrucciones que, según el enunciado, se ejecutan en modo sistema operativo. */
    private boolean esDeModoKernel(TipoInstruccion tipo) {
        return tipo == TipoInstruccion.SEM_WAIT
                || tipo == TipoInstruccion.SEM_SIGNAL
                || tipo == TipoInstruccion.PRODUCIR
                || tipo == TipoInstruccion.CONSUMIR
                || tipo == TipoInstruccion.READ_IO
                || tipo == TipoInstruccion.WRITE_IO;
    }

    /**
     * Sincroniza el modo de CPU al especificado.
     *
     * @param modo nuevo modo de operación (USUARIO o KERNEL).
     */
    public void setModo(ModoCPU modo) {
        this.modo = (modo != null) ? modo : ModoCPU.USUARIO;
    }

    /**
     * Registra la ejecución de una instrucción del sistema operativo en modo kernel.
     * Sincroniza el modo a {@link ModoCPU#KERNEL} e incrementa los ciclos ejecutados
     * de la CPU y del proceso actual (para el control del quantum).
     */
    public void registrarPasoEjecutado() {
        this.modo = ModoCPU.KERNEL;
        this.ciclosEjecutados++;
        this.ciclosProcesoActual++;
    }

    @Override
    public String getNombre() {
        return nombre;
    }

    /** Proceso en ejecución, o {@code null} si está ociosa. */
    public PCB getProcesoActual() {
        return procesoActual;
    }

    /** Modo en el que se ejecutó la última instrucción. */
    public ModoCPU getModo() {
        return modo;
    }

    /** Total de instrucciones ejecutadas por esta CPU. */
    public long getCiclosEjecutados() {
        return ciclosEjecutados;
    }

    /** Instrucciones que lleva ejecutadas el proceso cargado (control del quantum). */
    public long getCiclosProcesoActual() {
        return ciclosProcesoActual;
    }
}
