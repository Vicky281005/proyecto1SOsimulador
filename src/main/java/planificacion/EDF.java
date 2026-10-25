package planificacion;

import enums.PoliticaPlanificacion;
import estructuras.ListaEnlazada;
import modelo.PCB;

/**
 * Política Earliest Deadline First (EDF).
 *
 * <p>Ordena la cola de listos por deadline absoluto ascendente, de modo que
 * siempre se atiende al proceso cuyo plazo vence antes. Es apropiativa: si
 * llega un proceso con un deadline más próximo que el del proceso en
 * ejecución, el kernel puede desalojarlo.</p>
 *
 * <p>El orden se mantiene al insertar, por lo que no requiere reordenamiento
 * posterior. Ante deadlines iguales se respeta el orden de llegada (FIFO).</p>
 */
public class EDF implements AlgoritmoPlanificacion {

    @Override
    public void encolar(ListaEnlazada<PCB> listos, PCB proceso) {
        int posicion = 0;
        while (posicion < listos.getTamano()
                && listos.obtener(posicion).getDeadlineAbsoluto() <= proceso.getDeadlineAbsoluto()) {
            posicion++;
        }
        listos.insertar(posicion, proceso);
    }

    @Override
    public PCB seleccionar(ListaEnlazada<PCB> listos, long cicloActual) {
        return listos.eliminarPrimero(); // el de deadline más próximo
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
        return PoliticaPlanificacion.EDF.getEtiqueta();
    }
}
