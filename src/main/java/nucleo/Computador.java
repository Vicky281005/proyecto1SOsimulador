package nucleo;

import enums.EstadoProceso;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import hardware.CPU;
import hardware.ComponenteReloj;
import modelo.PCB;
import planificacion.AlgoritmoPlanificacion;

/**
 * Computador simulado de ÁvilaOS: el hardware (CPU) más su núcleo del sistema
 * operativo (planificador y colas).
 *
 * <p>Reacciona al reloj global: en cada ciclo realiza las tareas del sistema
 * operativo y luego permite que la CPU ejecute una instrucción. La CPU es
 * conducida por su propio computador (como la placa madre), de manera que el
 * orden sea determinista: primero el SO decide, después la CPU ejecuta.</p>
 */
public class Computador implements ComponenteReloj {

    private final int id;
    private final String nombre;

    private final CPU cpu;
    private final Planificador planificador;

    private final ListaEnlazada<PCB> nuevos;
    private final ListaEnlazada<PCB> bloqueados;
    private final ListaEnlazada<PCB> terminados;

    private final long memoriaTotal;
    private long memoriaDisponible;

    /**
     * @param id             identificador del computador.
     * @param nombre         nombre para logs y GUI.
     * @param memoriaTotal   RAM total disponible en este computador.
     * @param politica       política de planificación con la que arranca.
     */
    public Computador(int id, String nombre, long memoriaTotal, AlgoritmoPlanificacion politica) {
        if (memoriaTotal <= 0) {
            throw new IllegalArgumentException("La memoria total debe ser mayor que cero.");
        }
        this.id = id;
        this.nombre = (nombre != null && !nombre.isEmpty()) ? nombre : ("Computador-" + id);
        this.cpu = new CPU(this.nombre + " CPU");
        this.planificador = new Planificador(politica);
        this.nuevos = new ListaEnlazada<>();
        this.bloqueados = new ListaEnlazada<>();
        this.terminados = new ListaEnlazada<>();
        this.memoriaTotal = memoriaTotal;
        this.memoriaDisponible = memoriaTotal;
    }

    /**
     * Recibe un proceso recién creado y lo coloca en la cola de nuevos.
     * Si el proceso aún no tenía computador asignado, se le asigna este.
     *
     * @param proceso proceso en estado NUEVO.
     */
    public void recibirProceso(PCB proceso) {
        if (proceso == null) {
            throw new IllegalArgumentException("No se puede recibir un proceso nulo.");
        }
        if (proceso.getEstado() != EstadoProceso.NUEVO) {
            throw new IllegalStateException("Solo se pueden recibir procesos en estado NUEVO.");
        }
        if (proceso.getComputadorAsignado() == PCB.SIN_ASIGNAR) {
            proceso.asignarComputador(id);
        }
        nuevos.agregar(proceso);
    }

    @Override
    public void tick(long cicloActual) {
        admitir();
        despachar(cicloActual);

        PCB enEjecucion = cpu.getProcesoActual();
        if (enEjecucion != null) {
            cpu.tick(cicloActual);
            if (enEjecucion.getTiempoRestante() == 0) {
                terminar(enEjecucion, cicloActual);
            }
        }
    }

    /** Mueve de nuevos a listos los procesos que quepan en memoria, en orden de llegada. */
    private void admitir() {
        ListaEnlazada<PCB> admitidos = new ListaEnlazada<>();
        Iterador<PCB> iterador = nuevos.iterador();
        while (iterador.haySiguiente()) {
            PCB proceso = iterador.siguiente();
            if (proceso.getMemoriaAsignada() <= memoriaDisponible) {
                memoriaDisponible -= proceso.getMemoriaAsignada();
                admitidos.agregar(proceso);
            }
        }
        Iterador<PCB> iteradorAdmitidos = admitidos.iterador();
        while (iteradorAdmitidos.haySiguiente()) {
            PCB proceso = iteradorAdmitidos.siguiente();
            nuevos.eliminar(proceso);
            proceso.setEstado(EstadoProceso.LISTO);
            planificador.encolarListo(proceso);
        }
    }

    /** Si la CPU está ociosa y hay listos, carga el siguiente según la política. */
    private void despachar(long cicloActual) {
        if (!cpu.estaOciosa() || planificador.estaVacio()) {
            return;
        }
        PCB siguiente = planificador.seleccionarSiguiente(cicloActual);
        if (siguiente == null) {
            return;
        }
        siguiente.setEstado(EstadoProceso.EJECUCION);
        cpu.cargarProceso(siguiente);
    }

    /** Cierra un proceso que agotó su programa: libera RAM y CPU. */
    private void terminar(PCB proceso, long cicloActual) {
        proceso.setEstado(EstadoProceso.TERMINADO);
        proceso.setCicloFin(cicloActual);
        memoriaDisponible += proceso.getMemoriaAsignada();
        cpu.liberar();
        terminados.agregar(proceso);
    }

    /** Indica si no queda nada por hacer (CPU ociosa y todas las colas vacías). */
    public boolean sinTrabajoPendiente() {
        return cpu.estaOciosa()
                && planificador.estaVacio()
                && nuevos.estaVacia()
                && bloqueados.estaVacia();
    }

    @Override
    public String getNombre() {
        return nombre;
    }

    public int getId() {
        return id;
    }

    public CPU getCpu() {
        return cpu;
    }

    public Planificador getPlanificador() {
        return planificador;
    }

    public long getMemoriaTotal() {
        return memoriaTotal;
    }

    public long getMemoriaDisponible() {
        return memoriaDisponible;
    }

    public int getCantidadNuevos() {
        return nuevos.getTamano();
    }

    public int getCantidadBloqueados() {
        return bloqueados.getTamano();
    }

    public int getCantidadTerminados() {
        return terminados.getTamano();
    }

    public Iterador<PCB> iteradorNuevos() {
        return nuevos.iterador();
    }

    public Iterador<PCB> iteradorBloqueados() {
        return bloqueados.iterador();
    }

    public Iterador<PCB> iteradorTerminados() {
        return terminados.iterador();
    }
}
