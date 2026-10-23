package planificacion;

import enums.PoliticaPlanificacion;
import estructuras.ListaEnlazada;
import modelo.PCB;

/**
 * Política First Come First Served (FCFS).
 *
 * <p>El proceso que llega se coloca al final de la cola y se atiende al más
 * antiguo. No es apropiativa: el proceso en ejecución no se interrumpe hasta
 * que termina.</p>
 */
public class FCFS implements AlgoritmoPlanificacion {

    @Override
    public void encolar(ListaEnlazada<PCB> listos, PCB proceso) {
        listos.agregar(proceso); // al final
    }

    @Override
    public PCB seleccionar(ListaEnlazada<PCB> listos, long cicloActual) {
        return listos.eliminarPrimero(); // el más antiguo
    }

    @Override
    public boolean esApropiativa() {
        return false;
    }

    @Override
    public int getQuantum() {
        return -1; // no usa quantum
    }

    @Override
    public String getNombre() {
        return PoliticaPlanificacion.FCFS.getEtiqueta();
    }
}
