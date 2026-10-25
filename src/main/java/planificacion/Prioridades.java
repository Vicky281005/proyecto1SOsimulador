package planificacion;

import enums.PoliticaPlanificacion;
import estructuras.ListaEnlazada;
import modelo.PCB;

/**
 * Política de Prioridades Apropiativas.
 *
 * <p>Ordena la cola de listos por prioridad, donde un número MENOR significa
 * MAYOR prioridad (estilo UNIX). Es apropiativa: si llega a listos un proceso
 * con mayor prioridad que el que está en ejecución, el kernel puede
 * desalojarlo.</p>
 *
 * <p>El orden se mantiene al insertar. Ante prioridades iguales se respeta el
 * orden de llegada (FIFO).</p>
 */
public class Prioridades implements AlgoritmoPlanificacion {

    @Override
    public void encolar(ListaEnlazada<PCB> listos, PCB proceso) {
        int posicion = 0;
        while (posicion < listos.getTamano()
                && listos.obtener(posicion).getPrioridad() <= proceso.getPrioridad()) {
            posicion++;
        }
        listos.insertar(posicion, proceso);
    }

    @Override
    public PCB seleccionar(ListaEnlazada<PCB> listos, long cicloActual) {
        return listos.eliminarPrimero(); // la mayor prioridad (menor número)
    }

    @Override
    public void reordenar(ListaEnlazada<PCB> listos, long cicloActual) {
        if (listos == null || listos.getTamano() <= 1) {
            return;
        }
        ListaEnlazada<PCB> temporal = new ListaEnlazada<>();
        while (!listos.estaVacia()) {
            temporal.agregar(listos.eliminarPrimero());
        }
        while (!temporal.estaVacia()) {
            encolar(listos, temporal.eliminarPrimero());
        }
    }

    @Override
    public boolean esApropiativa() {
        return true;
    }

    @Override
    public int getQuantum() {
        return -1; // no usa quantum
    }

    @Override
    public String getNombre() {
        return PoliticaPlanificacion.PRIORIDAD.getEtiqueta();
    }
}
