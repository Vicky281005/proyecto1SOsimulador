package planificacion;

import enums.PoliticaPlanificacion;
import estructuras.ListaEnlazada;
import modelo.PCB;

/**
 * Política Round Robin (RR).
 *
 * <p>Es una política FIFO con una rebanada de tiempo (quantum): cada proceso
 * ejecuta como máximo {@code quantum} ciclos y, si no ha terminado, vuelve al
 * final de la cola. Es apropiativa, pero su desalojo no depende de que llegue
 * un proceso mejor, sino de que se agote el quantum; el kernel detecta ese
 * caso comparando los ciclos consumidos con {@link #getQuantum()}.</p>
 */
public class RoundRobin implements AlgoritmoPlanificacion {

    /** Quantum usado cuando no se especifica uno. */
    public static final int QUANTUM_POR_DEFECTO = 4;

    private int quantum;

    public RoundRobin() {
        this(QUANTUM_POR_DEFECTO);
    }

    public RoundRobin(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException("El quantum debe ser mayor que cero.");
        }
        this.quantum = quantum;
    }

    @Override
    public void encolar(ListaEnlazada<PCB> listos, PCB proceso) {
        // FIFO: al final, tanto al llegar como al reencolar tras agotar el quantum.
        listos.agregar(proceso);
    }

    @Override
    public PCB seleccionar(ListaEnlazada<PCB> listos, long cicloActual) {
        return listos.eliminarPrimero();
    }

    @Override
    public boolean esApropiativa() {
        return true;
    }

    @Override
    public int getQuantum() {
        return quantum;
    }

    /**
     * Cambia la longitud de la rebanada de tiempo.
     *
     * @param quantum ciclos que puede ejecutar cada proceso (mayor que cero).
     */
    public void setQuantum(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException("El quantum debe ser mayor que cero.");
        }
        this.quantum = quantum;
    }

    @Override
    public String getNombre() {
        return PoliticaPlanificacion.ROUND_ROBIN.getEtiqueta();
    }
}
