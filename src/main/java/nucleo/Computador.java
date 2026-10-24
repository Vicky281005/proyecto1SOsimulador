package nucleo;

import enums.EstadoProceso;
import enums.MotivoBloqueo;
import enums.TipoInstruccion;
import enums.TipoProceso;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import hardware.CPU;
import hardware.ComponenteReloj;
import modelo.Instruccion;
import modelo.PCB;
import planificacion.AlgoritmoPlanificacion;
import planificacion.RoundRobin;
import simulacion.Simulador;
import sincronizacion.Buffer;
import sincronizacion.Semaforo;
import sincronizacion.SemaforoBinario;

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

        // Revisar deadlines y posibles desalos en todo el computador.
        revisarDeadlines(cicloActual);
        evaluarDesalojo(cicloActual);

        despachar(cicloActual);

        PCB enEjecucion = cpu.getProcesoActual();
        if (enEjecucion != null) {
            // Revisar deadline antes de ejecutar la instrucción.
            if (enEjecucion.verificarDeadline(cicloActual)) {
                terminarPorDeadline(enEjecucion, cicloActual);
                despachar(cicloActual);
                enEjecucion = cpu.getProcesoActual();
            }
        }

        if (enEjecucion != null) {
            Instruccion instr = enEjecucion.getInstruccionActual();
            if (instr != null && esInstruccionKernel(instr.getTipo())) {
                boolean bloqueado = procesarInstruccionKernel(enEjecucion, instr, cicloActual);
                if (bloqueado) {
                    enEjecucion = cpu.getProcesoActual();
                } else {
                    // La instrucción se procesó, avanzar PC
                    enEjecucion.avanzarPasoCpu(cicloActual);
                    enEjecucion = cpu.getProcesoActual();
                }
            } else {
                cpu.tick(cicloActual);
            }
            // Tras ejecutar, volver a evaluar por si venció el deadline o agotó quantum.
            enEjecucion = cpu.getProcesoActual();
            if (enEjecucion != null) {
                if (enEjecucion.verificarDeadline(cicloActual)) {
                    terminarPorDeadline(enEjecucion, cicloActual);
                    despachar(cicloActual);
                } else if (enEjecucion.getTiempoRestante() == 0) {
                    terminar(enEjecucion, cicloActual);
                }
            }
        }

        // Evaluar desalojo al final por quantum.
        evaluarDesalojo(cicloActual);
        if (cpu.estaOciosa()) {
            despachar(cicloActual);
        }
    }

    private boolean esInstruccionKernel(TipoInstruccion tipo) {
        return tipo == TipoInstruccion.SEM_WAIT
                || tipo == TipoInstruccion.SEM_SIGNAL
                || tipo == TipoInstruccion.PRODUCIR
                || tipo == TipoInstruccion.CONSUMIR
                || tipo == TipoInstruccion.READ_IO
                || tipo == TipoInstruccion.WRITE_IO;
    }

    private boolean procesarInstruccionKernel(PCB proceso, Instruccion instr, long cicloActual) {
        try {
            switch (instr.getTipo()) {
                case SEM_WAIT:
                case SEM_SIGNAL:
                case PRODUCIR:
                case CONSUMIR: {
                    return procesarSincronizacion(proceso, instr, cicloActual);
                }
                case READ_IO:
                case WRITE_IO: {
                    // Por ahora se trata como instrucción de 1 ciclo (modo SO). 
                    // Se puede bloquear si se requiere.
                    return false;
                }
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean procesarSincronizacion(PCB proceso, Instruccion instr, long cicloActual) {
        String bufferId = proceso.getIdBufferTarget();
        Buffer buffer = null;
        if (simulador != null && bufferId != null && !bufferId.isEmpty()) {
            buffer = simulador.buscarBuffer(bufferId);
        }
        if (buffer == null) {
            // Sin buffer, ejecutar como instrucción normal
            return false;
        }

        switch (instr.getTipo()) {
            case PRODUCIR: {
                // wait(vacios) -> wait(mutex) -> insertar -> signal(mutex) -> signal(llenos)
                if (!buffer.getVacios().esperar(proceso, MotivoBloqueo.SEMAFORO_VACIO)) {
                    bloquearEnSemaforo(proceso, buffer.getVacios(), MotivoBloqueo.SEMAFORO_VACIO, buffer.getId());
                    return true;
                }
                if (!buffer.getMutex().esperar(proceso)) {
                    // devolver vacios
                    buffer.getVacios().senalizar();
                    bloquearEnSemaforo(proceso, buffer.getMutex().getSemaforo(), MotivoBloqueo.MUTEX, buffer.getId());
                    return true;
                }
                buffer.insertar(new Object());
                proceso.incrementarElementosProducidos();
                // signal(mutex)
                PCB despertadoMutex = buffer.getMutex().senalizar();
                if (despertadoMutex != null) {
                    despertarProceso(despertadoMutex);
                }
                // signal(llenos)
                PCB despertadoLlenos = buffer.getLlenos().senalizar();
                if (despertadoLlenos != null) {
                    despertarProceso(despertadoLlenos);
                }
                return false;
            }
            case CONSUMIR: {
                if (!buffer.getLlenos().esperar(proceso, MotivoBloqueo.SEMAFORO_LLENO)) {
                    bloquearEnSemaforo(proceso, buffer.getLlenos(), MotivoBloqueo.SEMAFORO_LLENO, buffer.getId());
                    return true;
                }
                if (!buffer.getMutex().esperar(proceso)) {
                    buffer.getLlenos().senalizar();
                    bloquearEnSemaforo(proceso, buffer.getMutex().getSemaforo(), MotivoBloqueo.MUTEX, buffer.getId());
                    return true;
                }
                buffer.extraer();
                proceso.incrementarElementosConsumidos();
                PCB despertadoMutex2 = buffer.getMutex().senalizar();
                if (despertadoMutex2 != null) {
                    despertarProceso(despertadoMutex2);
                }
                PCB despertadoVacios = buffer.getVacios().senalizar();
                if (despertadoVacios != null) {
                    despertarProceso(despertadoVacios);
                }
                return false;
            }
            case SEM_WAIT: {
                Semaforo s = buffer.getVacios();
                if (!s.esperar(proceso, MotivoBloqueo.SEMAFORO_VACIO)) {
                    bloquearEnSemaforo(proceso, s, MotivoBloqueo.SEMAFORO_VACIO, buffer.getId());
                    return true;
                }
                return false;
            }
            case SEM_SIGNAL: {
                Semaforo s2 = buffer.getVacios();
                PCB despertado = s2.senalizar();
                if (despertado != null) {
                    despertarProceso(despertado);
                }
                return false;
            }
            default:
                return false;
        }
    }

    /** Mueve de nuevos a listos los procesos que quepan en memoria, en orden de llegada. */
    private void admitir() {
        ListaEnlazada<PCB> admitidos = new ListaEnlazada<>();
        Iterador<PCB> iterador = nuevos.iterador();
        while (iterador.haySiguiente()) {
            PCB proceso = iterador.siguiente();
            if (proceso.verificarDeadline(0) && proceso.getCicloInicio() == -1) {
                // Ya vencido al momento de admitir (caso extremo).
                admitidos.agregar(proceso);
                continue;
            }
            if (proceso.getMemoriaAsignada() <= memoriaDisponible) {
                memoriaDisponible -= proceso.getMemoriaAsignada();
                admitidos.agregar(proceso);
            }
        }
        Iterador<PCB> iteradorAdmitidos = admitidos.iterador();
        while (iteradorAdmitidos.haySiguiente()) {
            PCB proceso = iteradorAdmitidos.siguiente();
            nuevos.eliminar(proceso);
            if (proceso.isDeadlineCumplido() == false && proceso.getTiempoRestante() > 0) {
                // Ya marcado como vencido.
                terminarPorDeadline(proceso, 0);
                continue;
            }
            proceso.setEstado(EstadoProceso.LISTO);
            planificador.encolarListo(proceso);
            evaluarDesalojo(0);
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
        if (siguiente.verificarDeadline(cicloActual)) {
            terminarPorDeadline(siguiente, cicloActual);
            despachar(cicloActual);
            return;
        }
        siguiente.setEstado(EstadoProceso.EJECUCION);
        cpu.cargarProceso(siguiente);
    }

    /** Cierra un proceso que agotó su programa: libera RAM y CPU. */
    private void terminar(PCB proceso, long cicloActual) {
        if (proceso == null) {
            return;
        }
        proceso.setEstado(EstadoProceso.TERMINADO);
        if (proceso.getCicloFin() <= 0) {
            proceso.setCicloFin(cicloActual);
        }
        memoriaDisponible += proceso.getMemoriaAsignada();
        cpu.liberar();
        terminados.agregar(proceso);
    }

    private void terminarPorDeadline(PCB proceso, long cicloActual) {
        if (proceso == null) {
            return;
        }
        proceso.setDeadlineCumplido(false);
        proceso.setEstado(EstadoProceso.TERMINADO);
        if (proceso.getCicloFin() <= 0) {
            proceso.setCicloFin(cicloActual);
        }
        memoriaDisponible += proceso.getMemoriaAsignada();
        if (cpu.getProcesoActual() == proceso) {
            cpu.liberar();
        }
        terminados.agregar(proceso);
    }

    /**
     * Bloquea un proceso en el semáforo indicado.
     *
     * <p>El proceso pasa a estado {@code BLOQUEADO}, se registra el motivo y el
     * semáforo, se libera la CPU y se añade a la cola de bloqueados del
     * computador.</p>
     *
     * @param proceso  proceso a bloquear.
     * @param semaforo semáforo sobre el que espera.
     * @param motivo   motivo del bloqueo.
     * @param bufferId identificador del buffer (opcional).
     * @return true si se bloqueó correctamente.
     */
    public boolean bloquearEnSemaforo(PCB proceso, Semaforo semaforo, MotivoBloqueo motivo, String bufferId) {
        if (proceso == null) {
            return false;
        }
        if (cpu.getProcesoActual() == proceso) {
            cpu.liberar();
        }
        proceso.bloquear(motivo != null ? motivo : MotivoBloqueo.NINGUNO, bufferId);
        // Eliminar de listos si estuviera
        planificador.eliminarListo(proceso);
        bloqueados.agregar(proceso);
        return true;
    }

    /**
     * Despierta al proceso indicado y lo pasa a la cola de listos.
     *
     * @param proceso proceso a despertar.
     */
    public void despertarProceso(PCB proceso) {
        if (proceso == null) {
            return;
        }
        bloqueados.eliminar(proceso);
        proceso.setEstado(EstadoProceso.LISTO);
        proceso.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
        proceso.setCiclosBloqueoRestantes(0);
        planificador.encolarListo(proceso);
    }

    /**
     * Intenta despertar procesos del semáforo indicado.
     *
     * @param semaforo semáforo sobre el que se hizo señal.
     * @return true si se despertó algún proceso.
     */
    public boolean despertarDesdeSemaforo(Semaforo semaforo) {
        if (semaforo == null) {
            return false;
        }
        PCB p = semaforo.senalizar();
        if (p == null) {
            return false;
        }
        // Verificar que esté bloqueado en este computador
        despertarProceso(p);
        return true;
    }

    private void revisarDeadlines(long cicloActual) {
        Iterador<PCB> it = planificador.iteradorListos();
        while (it.haySiguiente()) {
            PCB p = it.siguiente();
            if (p.verificarDeadline(cicloActual) && p.getTiempoRestante() > 0) {
                planificador.eliminarListo(p);
                terminarPorDeadline(p, cicloActual);
                break;
            }
        }
    }

    private void evaluarDesalojo(long cicloActual) {
        PCB actual = cpu.getProcesoActual();
        if (actual == null || planificador.estaVacio()) {
            return;
        }
        if (!planificador.esApropiativa()) {
            return;
        }

        if (planificador.getPolitica() instanceof RoundRobin rr) {
            if (cpu.getCiclosProcesoActual() >= rr.getQuantum()) {
                // Desalojar por quantum
                cpu.liberar();
                actual.setEstado(EstadoProceso.LISTO);
                planificador.encolarListo(actual);
                return;
            }
            return;
        }

        PCB candidato = planificador.peekSiguiente();
        if (candidato == null) {
            return;
        }
        // Comparar
        String nombrePol = planificador.getPolitica().getNombre();
        boolean desalojar = false;
        if (nombrePol != null && nombrePol.toLowerCase().contains("prioridad")) {
            // menor número = mayor prioridad
            if (candidato.getPrioridad() < actual.getPrioridad()) {
                desalojar = true;
            }
        } else if (nombrePol != null && nombrePol.toLowerCase().contains("earliest") || (nombrePol != null && nombrePol.toLowerCase().contains("edf"))) {
            if (candidato.getDeadlineAbsoluto() < actual.getDeadlineAbsoluto()) {
                desalojar = true;
            }
        }
        if (!desalojar) {
            return;
        }
        PCB siguiente = planificador.seleccionarSiguiente(cicloActual);
        if (siguiente == null) {
            return;
        }
        cpu.liberar();
        actual.setEstado(EstadoProceso.LISTO);
        planificador.encolarListo(actual);
        if (siguiente.verificarDeadline(cicloActual)) {
            terminarPorDeadline(siguiente, cicloActual);
            return;
        }
        siguiente.setEstado(EstadoProceso.EJECUCION);
        cpu.cargarProceso(siguiente);
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

    /**
     * Reserva memoria para un componente del sistema (buffers, etc.).
     *
     * @param cantidad bytes/KB a reservar.
     * @return true si hay espacio suficiente.
     */
    public boolean reservarMemoria(long cantidad) {
        if (cantidad <= 0) {
            return true;
        }
        if (cantidad > memoriaDisponible) {
            return false;
        }
        memoriaDisponible -= cantidad;
        return true;
    }

    /**
     * Libera memoria reservada.
     *
     * @param cantidad cantidad a liberar.
     */
    public void liberarMemoria(long cantidad) {
        if (cantidad <= 0) {
            return;
        }
        memoriaDisponible += cantidad;
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

    private final ListaEnlazada<Buffer> buffers = new ListaEnlazada<>();
    private Simulador simulador;

    public void setSimulador(Simulador simulador) {
        this.simulador = simulador;
    }

    public Simulador getSimulador() {
        return simulador;
    }

    public void registrarBuffer(Buffer buffer) {
        if (buffer == null) {
            return;
        }
        buffers.agregar(buffer);
    }

    public ListaEnlazada<Buffer> getBuffers() {
        return buffers;
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
