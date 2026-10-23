package planificacion;

import estructuras.ListaEnlazada;
import modelo.PCB;

/**
 * Contrato del comportamiento de una política de planificación de ÁvilaOS.
 *
 * <p>El planificador de cada computador conserva una referencia a esta
 * interfaz y la utiliza para ordenar su cola de listos y elegir al siguiente
 * proceso. Gracias a esto, agregar una política nueva (por ejemplo SJF)
 * consiste en crear una clase que implemente esta interfaz, sin tener que
 * modificar el código del planificador.</p>
 *
 * <p>La cola de listos se recibe como parámetro en lugar de guardarse dentro
 * de la política, para que la estructura siga siendo propiedad del
 * computador.</p>
 */
public interface AlgoritmoPlanificacion {

    /**
     * Inserta un proceso en la cola de listos respetando el criterio de la
     * política: FCFS y Round Robin lo envían al final, mientras que EDF y
     * Prioridades lo ubican según su deadline o su prioridad.
     *
     * @param listos  cola de listos del computador.
     * @param proceso proceso que pasa (o vuelve) a estar listo.
     */
    void encolar(ListaEnlazada<PCB> listos, PCB proceso);

    /**
     * Selecciona y extrae de la cola el proceso que debe ejecutarse a
     * continuación.
     *
     * @param listos      cola de listos del computador.
     * @param cicloActual ciclo de reloj en el que se realiza la selección.
     * @return el proceso elegido, o {@code null} si la cola está vacía.
     */
    PCB seleccionar(ListaEnlazada<PCB> listos, long cicloActual);

    /**
     * Reordena la cola de listos después de una selección, para las políticas
     * que lo necesiten (envejecimiento de prioridades, recálculo de deadlines
     * en EDF, etc.). Por defecto no hace nada.
     *
     * @param listos      cola de listos del computador.
     * @param cicloActual ciclo de reloj actual.
     */
    default void reordenar(ListaEnlazada<PCB> listos, long cicloActual) {
        // La mayoría de las políticas mantiene el orden con encolar().
    }

    /**
     * Indica si la política puede desalojar al proceso en ejecución cuando
     * llega uno con mejor derecho (deadline más próximo o mayor prioridad).
     *
     * @return {@code true} si la política es apropiativa.
     */
    boolean esApropiativa();

    /**
     * Cantidad de ciclos de reloj que un proceso puede ejecutar antes de ser
     * desalojado. Solo aplica a Round Robin.
     *
     * @return el quantum en ciclos, o {@code -1} si la política no usa quantum.
     */
    int getQuantum();

    /**
     * Nombre legible de la política, para mostrarlo en la GUI y en los logs.
     *
     * @return el nombre de la política.
     */
    String getNombre();
}
