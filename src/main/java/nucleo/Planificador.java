package nucleo;

import estructuras.Iterador;
import estructuras.ListaEnlazada;
import modelo.PCB;
import planificacion.AlgoritmoPlanificacion;

/**
 * Planificador de un computador de ÁvilaOS.
 *
 * <p>Es dueño de la cola de listos y de la política de planificación. La
 * política se inyecta por constructor (y puede cambiarse en tiempo de
 * ejecución), de modo que agregar un algoritmo nuevo no obliga a modificar
 * esta clase.</p>
 */
public class Planificador {

    private final ListaEnlazada<PCB> listos;
    private AlgoritmoPlanificacion politica;
    private Computador computador;

    public Planificador(AlgoritmoPlanificacion politica) {
        if (politica == null) {
            throw new IllegalArgumentException("La política de planificación no puede ser nula.");
        }
        this.listos = new ListaEnlazada<>();
        this.politica = politica;
    }

    /**
     * Encola un proceso en la cola de listos respetando el orden que exige la
     * política actual.
     *
     * @param proceso proceso que pasa a estar listo.
     */
    public void encolarListo(PCB proceso) {
        politica.encolar(listos, proceso);
    }

    /**
     * Selecciona y extrae de la cola el siguiente proceso a ejecutar.
     *
     * @param cicloActual ciclo de reloj en el que se realiza la selección.
     * @return el proceso elegido, o {@code null} si no hay listos.
     */
    public PCB seleccionarSiguiente(long cicloActual) {
        PCB siguiente = politica.seleccionar(listos, cicloActual);
        politica.reordenar(listos, cicloActual);
        return siguiente;
    }

    /**
     * Obtiene el mejor candidato sin extraerlo de la cola.
     *
     * @return el candidato a ejecutar, o {@code null} si la cola está vacía.
     */
    public PCB peekSiguiente() {
        if (listos.estaVacia()) {
            return null;
        }
        return listos.obtenerPrimero();
    }

    /**
     * Indica si la política es apropiativa.
     *
     * @return true si la política puede desalojar.
     */
    public boolean esApropiativa() {
        return politica.esApropiativa();
    }

    /**
     * Obtiene el quantum de la política actual.
     *
     * @return quantum en ciclos, o -1 si no aplica.
     */
    public int getQuantum() {
        return politica.getQuantum();
    }

    /** Indica si no hay ningún proceso listo. */
    public boolean estaVacio() {
        return listos.estaVacia();
    }

    /** Cantidad de procesos en la cola de listos. */
    public int getCantidadListos() {
        return listos.getTamano();
    }

    /** Política de planificación activa. */
    public AlgoritmoPlanificacion getPolitica() {
        return politica;
    }

    /**
     * Elimina una instancia específica de la cola de listos.
     *
     * @param proceso proceso a eliminar.
     * @return true si se eliminó.
     */
    public boolean eliminarListo(PCB proceso) {
        if (proceso == null) {
            return false;
        }
        return listos.eliminar(proceso);
    }

    public void setComputador(Computador computador) {
        this.computador = computador;
    }

    /**
     * Cambia la política de planificación en tiempo de ejecución.
     *
     * @param politica nueva política.
     */
    public void setPolitica(AlgoritmoPlanificacion politica) {
        if (politica == null) {
            throw new IllegalArgumentException("La política de planificación no puede ser nula.");
        }
        simulacion.LogEventos log = null;
        if (computador != null && computador.getSimulador() != null) {
            log = computador.getSimulador().getLogEventos();
        }
        if (log != null) {
            String ant = this.politica != null ? this.politica.getNombre() : "?";
            String nue = politica.getNombre();
            log.registrar(simulacion.LogEventos.TipoEvento.CAMBIO_POLITICA, "Cambio de política " + ant + " -> " + nue + " en " + (computador != null ? computador.getNombre() : ""));
        }
        this.politica = politica;
    }

    /** Iterador sobre la cola de listos, para observarla sin exponer la lista interna. */
    public Iterador<PCB> iteradorListos() {
        return listos.iterador();
    }
}
