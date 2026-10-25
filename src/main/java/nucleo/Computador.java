package nucleo;

import enums.EstadoProceso;
import enums.ModoCPU;
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
        this.planificador.setComputador(this);
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

        simulacion.LogEventos log = (simulador != null) ? simulador.getLogEventos() : null;
        if (log != null && !proceso.isCreacionRegistradaEnLog()) {
            proceso.setCreacionRegistradaEnLog(true);
            log.registrar(simulacion.LogEventos.TipoEvento.CREACION,
                    "Creado proceso " + proceso.getNombre() + " (" + proceso.getId() + ") en estado NUEVO (RAM requerida: " + proceso.getMemoriaAsignada() + " KB)");
        }
    }

    @Override
    public void tick(long cicloActual) {
        // Procesar bloqueos por latencia de red antes de admitir/despachar
        procesarBloqueosPorLatencia(cicloActual);

        admitir(cicloActual);

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
                cpu.setModo(ModoCPU.KERNEL);
                boolean bloqueado = procesarInstruccionKernel(enEjecucion, instr, cicloActual);
                if (bloqueado) {
                    enEjecucion = cpu.getProcesoActual();
                } else {
                    // La instrucción se procesó, avanzar PC y registrar paso en la CPU
                    enEjecucion.avanzarPasoCpu(cicloActual);
                    cpu.registrarPasoEjecutado();
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

        int idCompProc = proceso.getComputadorAsignado();
        int idCompBuf = buffer.getIdComputadorAnfitrion();
        boolean accesoRemoto = (idCompProc != -1 && idCompProc != idCompBuf);

        switch (instr.getTipo()) {
            case PRODUCIR: {
                // wait(vacios) -> wait(mutex) -> insertar -> signal(mutex) -> signal(llenos)
                if (!proceso.isRecursoSemaforoAdquirido()) {
                    if (!buffer.getVacios().esperar(proceso, MotivoBloqueo.SEMAFORO_VACIO)) {
                        bloquearEnSemaforo(proceso, buffer.getVacios(), MotivoBloqueo.SEMAFORO_VACIO, buffer.getId());
                        return true;
                    }
                    proceso.setRecursoSemaforoAdquirido(true);
                }
                if (!buffer.getMutex().esperar(proceso)) {
                    bloquearEnSemaforo(proceso, buffer.getMutex().getSemaforo(), MotivoBloqueo.MUTEX, buffer.getId());
                    return true;
                }
                if (accesoRemoto) {
                    // Bloquear por latencia de red antes de insertar
                    int latencia = 3;
                    if (simulador != null) {
                        latencia = simulador.getLatenciaRedCiclos();
                    }
                    proceso.setOperacionRemotaPendiente(TipoInstruccion.PRODUCIR);
                    proceso.setBufferRemotoPendienteId(buffer.getId());
                    proceso.setMutexRemotoRetenido(true);
                    proceso.setCiclosBloqueoRestantes(latencia);
                    if (cpu.getProcesoActual() == proceso) {
                        cpu.liberar();
                    }
                    proceso.bloquear(MotivoBloqueo.LATENCIA_RED, buffer.getId());
                    planificador.eliminarListo(proceso);
                    bloqueados.agregar(proceso);
                    simulacion.LogEventos logRem1 = (simulador != null) ? simulador.getLogEventos() : null;
                    if (logRem1 != null) {
                        logRem1.registrar(simulacion.LogEventos.TipoEvento.ACCESO_REMOTO,
                                "Acceso remoto LATENCIA_RED: " + proceso.getNombre() + " -> buffer " + buffer.getId()
                                        + " (" + nombre + " != PC anfitrión)");
                    }
                    return true;
                }
                buffer.insertar(new Object());
                proceso.incrementarElementosProducidos();
                proceso.setRecursoSemaforoAdquirido(false);
                // signal(mutex)
                PCB despertadoMutex = buffer.getMutex().senalizar();
                if (despertadoMutex != null) {
                    despertarProcesoCluster(despertadoMutex);
                }
                // signal(llenos)
                PCB despertadoLlenos = buffer.getLlenos().senalizar();
                if (despertadoLlenos != null) {
                    despertarProcesoCluster(despertadoLlenos);
                }
                simulacion.LogEventos logProd = (simulador != null) ? simulador.getLogEventos() : null;
                if (logProd != null) {
                    Computador anf = (simulador != null) ? simulador.buscarComputador(buffer.getIdComputadorAnfitrion()) : null;
                    String nomAnf = (anf != null) ? anf.getNombre() : ("PC-" + buffer.getIdComputadorAnfitrion());
                    logProd.registrar(simulacion.LogEventos.TipoEvento.BUFFER,
                            "Inserción en buffer " + buffer.getId() + " por proceso " + proceso.getNombre()
                                    + " (PID " + proceso.getId() + ") [anfitrión: " + nomAnf + "] -> "
                                    + "(vacíos=" + buffer.getValorVacios() + ", llenos=" + buffer.getValorLlenos()
                                    + ", mutex=" + buffer.getValorMutex() + ")");
                }
                return false;
            }
            case CONSUMIR: {
                if (!proceso.isRecursoSemaforoAdquirido()) {
                    if (!buffer.getLlenos().esperar(proceso, MotivoBloqueo.SEMAFORO_LLENO)) {
                        bloquearEnSemaforo(proceso, buffer.getLlenos(), MotivoBloqueo.SEMAFORO_LLENO, buffer.getId());
                        return true;
                    }
                    proceso.setRecursoSemaforoAdquirido(true);
                }
                if (!buffer.getMutex().esperar(proceso)) {
                    bloquearEnSemaforo(proceso, buffer.getMutex().getSemaforo(), MotivoBloqueo.MUTEX, buffer.getId());
                    return true;
                }
                if (accesoRemoto) {
                    int latencia = 3;
                    if (simulador != null) {
                        latencia = simulador.getLatenciaRedCiclos();
                    }
                    proceso.setOperacionRemotaPendiente(TipoInstruccion.CONSUMIR);
                    proceso.setBufferRemotoPendienteId(buffer.getId());
                    proceso.setMutexRemotoRetenido(true);
                    proceso.setCiclosBloqueoRestantes(latencia);
                    if (cpu.getProcesoActual() == proceso) {
                        cpu.liberar();
                    }
                    proceso.bloquear(MotivoBloqueo.LATENCIA_RED, buffer.getId());
                    planificador.eliminarListo(proceso);
                    bloqueados.agregar(proceso);
                    simulacion.LogEventos logRem2 = (simulador != null) ? simulador.getLogEventos() : null;
                    if (logRem2 != null) {
                        logRem2.registrar(simulacion.LogEventos.TipoEvento.ACCESO_REMOTO, "Acceso remoto LATENCIA_RED: " + proceso.getNombre() + " -> buffer " + buffer.getId() + " (" + nombre + " != PC anfitrión)");
                    }
                    return true;
                }
                buffer.extraer();
                proceso.incrementarElementosConsumidos();
                proceso.setRecursoSemaforoAdquirido(false);
                PCB despertadoMutex2 = buffer.getMutex().senalizar();
                if (despertadoMutex2 != null) {
                    despertarProcesoCluster(despertadoMutex2);
                }
                PCB despertadoVacios = buffer.getVacios().senalizar();
                if (despertadoVacios != null) {
                    despertarProcesoCluster(despertadoVacios);
                }
                simulacion.LogEventos logCons = (simulador != null) ? simulador.getLogEventos() : null;
                if (logCons != null) {
                    Computador anf = (simulador != null) ? simulador.buscarComputador(buffer.getIdComputadorAnfitrion()) : null;
                    String nomAnf = (anf != null) ? anf.getNombre() : ("PC-" + buffer.getIdComputadorAnfitrion());
                    logCons.registrar(simulacion.LogEventos.TipoEvento.BUFFER,
                            "Extracción de buffer " + buffer.getId() + " por proceso " + proceso.getNombre()
                                    + " (PID " + proceso.getId() + ") [anfitrión: " + nomAnf + "] -> "
                                    + "(vacíos=" + buffer.getValorVacios() + ", llenos=" + buffer.getValorLlenos()
                                    + ", mutex=" + buffer.getValorMutex() + ")");
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
                    despertarProcesoCluster(despertado);
                }
                return false;
            }
            default:
                return false;
        }
    }

    /** Mueve de nuevos a listos los procesos que quepan en memoria, en orden de llegada. */
    private void admitir(long cicloActual) {
        ListaEnlazada<PCB> admitidos = new ListaEnlazada<>();
        Iterador<PCB> iterador = nuevos.iterador();
        while (iterador.haySiguiente()) {
            PCB proceso = iterador.siguiente();
            if (proceso.verificarDeadline(cicloActual) && proceso.getCicloInicio() == -1) {
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
                terminarPorDeadline(proceso, cicloActual);
                continue;
            }
            proceso.setEstado(EstadoProceso.LISTO);
            planificador.encolarListo(proceso);
            evaluarDesalojo(cicloActual);
            simulacion.LogEventos logAdm = (simulador != null) ? simulador.getLogEventos() : null;
            if (logAdm != null) {
                logAdm.registrar(simulacion.LogEventos.TipoEvento.ADMISION, "Admitido " + proceso.getNombre() + " en " + nombre);
            }
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
        if (siguiente.getTiempoRestante() == 0) {
            terminar(siguiente, cicloActual);
            despachar(cicloActual);
            return;
        }
        siguiente.setEstado(EstadoProceso.EJECUCION);
        cpu.cargarProceso(siguiente);
        simulacion.LogEventos logDesp = (simulador != null) ? simulador.getLogEventos() : null;
        if (logDesp != null) {
            logDesp.registrar(simulacion.LogEventos.TipoEvento.DESPACHO, "Despachado " + siguiente.getNombre() + " en " + nombre + " (ciclo " + cicloActual + ")");
        }
    }

    /**
     * Libera de forma segura recursos retenidos por un proceso que termina o muere.
     */
    private void liberarRecursosProceso(PCB proceso) {
        if (proceso == null) {
            return;
        }
        // Si estaba en la cola de algún semáforo de buffer, desvincularlo para no dejarlo como proceso fantasma.
        // Si tenía un permiso reservado, transferir dicho permiso y despertar al siguiente proceso en espera.
        String bufferIdParaRemover = proceso.getBufferBloqueadoId();
        if (bufferIdParaRemover == null || bufferIdParaRemover.isEmpty()) {
            bufferIdParaRemover = proceso.getIdBufferTarget();
        }
        if (bufferIdParaRemover != null && !bufferIdParaRemover.isEmpty()) {
            Buffer buffer = (simulador != null) ? simulador.buscarBuffer(bufferIdParaRemover) : null;
            if (buffer != null) {
                PCB despV = buffer.getVacios().remover(proceso);
                if (despV != null) {
                    buffer.getVacios().signal();
                    despertarProcesoCluster(despV);
                }
                PCB despL = buffer.getLlenos().remover(proceso);
                if (despL != null) {
                    buffer.getLlenos().signal();
                    despertarProcesoCluster(despL);
                }
                PCB despM = buffer.getMutex().remover(proceso);
                if (despM != null) {
                    buffer.getMutex().signal();
                    despertarProcesoCluster(despM);
                }
            }
        }

        // Si tenía operación remota pendiente con mutex retenido, restituir recursos y despertar
        if (proceso.isMutexRemotoRetenido() && proceso.getOperacionRemotaPendiente() != null) {
            String bid = proceso.getBufferRemotoPendienteId();
            if (bid == null || bid.isEmpty()) {
                bid = proceso.getBufferBloqueadoId();
            }
            Buffer buffer = (simulador != null) ? simulador.buscarBuffer(bid) : null;
            if (buffer != null) {
                try {
                    PCB despRecurso = null;
                    if (proceso.getOperacionRemotaPendiente() == TipoInstruccion.PRODUCIR) {
                        despRecurso = buffer.getVacios().senalizar();
                    } else if (proceso.getOperacionRemotaPendiente() == TipoInstruccion.CONSUMIR) {
                        despRecurso = buffer.getLlenos().senalizar();
                    }
                    if (despRecurso != null) {
                        despertarProcesoCluster(despRecurso);
                    }
                    PCB despMutex = buffer.getMutex().senalizar();
                    if (despMutex != null) {
                        despertarProcesoCluster(despMutex);
                    }
                } catch (Exception ignored) {
                }
            }
            proceso.limpiarOperacionRemotaPendiente();
            proceso.setRecursoSemaforoAdquirido(false);
        }

        // Si tenía un recurso de semáforo adquirido pero aún no completó la operación (ej. murió esperando el mutex)
        if (proceso.isRecursoSemaforoAdquirido()) {
            String bid = proceso.getIdBufferTarget();
            if (bid == null || bid.isEmpty()) {
                bid = proceso.getBufferBloqueadoId();
            }
            Buffer buffer = (simulador != null && bid != null && !bid.isEmpty()) ? simulador.buscarBuffer(bid) : null;
            if (buffer != null) {
                try {
                    PCB despRecurso = null;
                    if (proceso.getTipo() == TipoProceso.PRODUCTOR) {
                        despRecurso = buffer.getVacios().senalizar();
                    } else if (proceso.getTipo() == TipoProceso.CONSUMIDOR) {
                        despRecurso = buffer.getLlenos().senalizar();
                    }
                    if (despRecurso != null) {
                        despertarProcesoCluster(despRecurso);
                    }
                } catch (Exception ignored) {
                }
            }
            proceso.setRecursoSemaforoAdquirido(false);
        }

        nuevos.eliminar(proceso);
        bloqueados.eliminar(proceso);
        planificador.eliminarListo(proceso);
    }

    /** Cierra un proceso que agotó su programa: libera RAM y CPU. */
    private void terminar(PCB proceso, long cicloActual) {
        if (proceso == null) {
            return;
        }
        liberarRecursosProceso(proceso);

        proceso.setEstado(EstadoProceso.TERMINADO);
        if (proceso.getCicloFin() <= 0) {
            proceso.setCicloFin(cicloActual);
        }
        memoriaDisponible += proceso.getMemoriaAsignada();
        if (memoriaDisponible > memoriaTotal) {
            memoriaDisponible = memoriaTotal;
        }
        if (cpu.getProcesoActual() == proceso) {
            cpu.liberar();
        }
        terminados.agregar(proceso);
        simulacion.LogEventos logTerm = (simulador != null) ? simulador.getLogEventos() : null;
        if (logTerm != null) {
            logTerm.registrar(simulacion.LogEventos.TipoEvento.TERMINADO, "Terminado " + proceso.getNombre() + " en " + nombre);
        }
    }

    private void terminarPorDeadline(PCB proceso, long cicloActual) {
        if (proceso == null) {
            return;
        }
        liberarRecursosProceso(proceso);

        boolean admitido = proceso.getEstado() != EstadoProceso.NUEVO;
        proceso.setDeadlineCumplido(false);
        proceso.setEstado(EstadoProceso.TERMINADO);
        if (proceso.getCicloFin() <= 0) {
            proceso.setCicloFin(cicloActual);
        }
        if (admitido) {
            memoriaDisponible += proceso.getMemoriaAsignada();
            if (memoriaDisponible > memoriaTotal) {
                memoriaDisponible = memoriaTotal;
            }
        }
        if (cpu.getProcesoActual() == proceso) {
            cpu.liberar();
        }
        terminados.agregar(proceso);
        simulacion.LogEventos logDl = (simulador != null) ? simulador.getLogEventos() : null;
        if (logDl != null) {
            logDl.registrar(simulacion.LogEventos.TipoEvento.DEADLINE_VENCIDO, "Deadline vencido: " + proceso.getNombre() + " en " + nombre);
        }
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
        simulacion.LogEventos logBlk = (simulador != null) ? simulador.getLogEventos() : null;
        if (logBlk != null) {
            logBlk.registrar(simulacion.LogEventos.TipoEvento.BLOQUEO, "Bloqueado " + proceso.getNombre() + " en " + nombre + " por " + (motivo != null ? motivo : MotivoBloqueo.NINGUNO));
        }
        return true;
    }

    /**
     * Despierta al proceso indicado y lo pasa a la cola de listos.
     *
     * @param proceso proceso a despertar.
     */
    /**
     * Despierta un proceso en su computador de origen. Evita que un computador
     * local despierte un proceso ajeno en sus propias colas (migración indebida).
     *
     * @param proceso proceso a despertar.
     */
    public void despertarProcesoCluster(PCB proceso) {
        if (proceso == null || proceso.getEstado() != EstadoProceso.BLOQUEADO) {
            return;
        }
        if (simulador != null) {
            Computador compDestino = simulador.buscarComputador(proceso.getIdComputador());
            if (compDestino != null) {
                compDestino.despertarProceso(proceso);
                return;
            }
        }
        despertarProceso(proceso);
    }

    /**
     * Despierta al proceso indicado y lo pasa a la cola de listos de este computador.
     *
     * @param proceso proceso a despertar.
     */
    public void despertarProceso(PCB proceso) {
        if (proceso == null || proceso.getEstado() != EstadoProceso.BLOQUEADO) {
            return;
        }
        bloqueados.eliminar(proceso);
        proceso.desbloquear();
        planificador.encolarListo(proceso);
        simulacion.LogEventos logDesb = (simulador != null) ? simulador.getLogEventos() : null;
        if (logDesb != null) {
            logDesb.registrar(simulacion.LogEventos.TipoEvento.DESBLOQUEO, "Desbloqueado " + proceso.getNombre() + " en " + nombre);
        }
    }

    /**
     * Intenta despertar procesos del semáforo indicado en su computador de origen.
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
        despertarProcesoCluster(p);
        return true;
    }

    private void revisarDeadlines(long cicloActual) {
        ListaEnlazada<PCB> listosVencidos = new ListaEnlazada<>();
        Iterador<PCB> it = planificador.iteradorListos();
        while (it.haySiguiente()) {
            PCB p = it.siguiente();
            if (p != null && p.verificarDeadline(cicloActual) && p.getTiempoRestante() > 0) {
                listosVencidos.agregar(p);
            }
        }
        Iterador<PCB> itLv = listosVencidos.iterador();
        while (itLv.haySiguiente()) {
            PCB p = itLv.siguiente();
            planificador.eliminarListo(p);
            terminarPorDeadline(p, cicloActual);
        }

        ListaEnlazada<PCB> blkVencidos = new ListaEnlazada<>();
        Iterador<PCB> itb = bloqueados.iterador();
        while (itb.haySiguiente()) {
            PCB p = itb.siguiente();
            if (p != null && p.verificarDeadline(cicloActual) && p.getTiempoRestante() > 0) {
                blkVencidos.agregar(p);
            }
        }
        Iterador<PCB> itBv = blkVencidos.iterador();
        while (itBv.haySiguiente()) {
            PCB p = itBv.siguiente();
            terminarPorDeadline(p, cicloActual);
        }
    }

    private void procesarBloqueosPorLatencia(long cicloActual) {
        if (bloqueados.estaVacia()) {
            return;
        }
        ListaEnlazada<PCB> listosParaDespertar = new ListaEnlazada<>();
        Iterador<PCB> it = bloqueados.iterador();
        while (it.haySiguiente()) {
            PCB p = it.siguiente();
            if (p == null) {
                continue;
            }
            if (p.getMotivoBloqueo() == MotivoBloqueo.LATENCIA_RED
                    && p.getOperacionRemotaPendiente() != null
                    && p.isMutexRemotoRetenido()) {
                long restantes = p.getCiclosBloqueoRestantes();
                if (restantes > 0) {
                    p.setCiclosBloqueoRestantes(restantes - 1);
                    continue;
                }
                // Latencia cumplida: completar la operación en el buffer remoto
                Buffer buffer = null;
                if (simulador != null) {
                    String bid = p.getBufferRemotoPendienteId();
                    if (bid == null || bid.isEmpty()) {
                        bid = p.getBufferBloqueadoId();
                    }
                    buffer = simulador.buscarBuffer(bid);
                }
                if (buffer != null) {
                    TipoInstruccion opRemota = p.getOperacionRemotaPendiente();
                    if (opRemota == TipoInstruccion.PRODUCIR) {
                        buffer.insertar(new Object());
                        p.incrementarElementosProducidos();
                    } else if (opRemota == TipoInstruccion.CONSUMIR) {
                        buffer.extraer();
                        p.incrementarElementosConsumidos();
                    }
                    p.setRecursoSemaforoAdquirido(false);
                    // Liberar mutex y despertar a quien espere por el mutex
                    PCB despertadoMutex = buffer.getMutex().senalizar();
                    if (despertadoMutex != null) {
                        despertarProcesoCluster(despertadoMutex);
                    }
                    // Señalizar el recurso correspondiente (llenos tras producir, vacios tras consumir)
                    if (opRemota == TipoInstruccion.PRODUCIR) {
                        PCB despertadoLlenos = buffer.getLlenos().senalizar();
                        if (despertadoLlenos != null) {
                            despertarProcesoCluster(despertadoLlenos);
                        }
                    } else if (opRemota == TipoInstruccion.CONSUMIR) {
                        PCB despertadoVacios = buffer.getVacios().senalizar();
                        if (despertadoVacios != null) {
                            despertarProcesoCluster(despertadoVacios);
                        }
                    }
                    simulacion.LogEventos logOp = (simulador != null) ? simulador.getLogEventos() : null;
                    if (logOp != null) {
                        Computador anf = (simulador != null) ? simulador.buscarComputador(buffer.getIdComputadorAnfitrion()) : null;
                        String nomAnf = (anf != null) ? anf.getNombre() : ("PC-" + buffer.getIdComputadorAnfitrion());
                        String tipoOp = (opRemota == TipoInstruccion.PRODUCIR) ? "Inserción remota en buffer " : "Extracción remota de buffer ";
                        logOp.registrar(simulacion.LogEventos.TipoEvento.BUFFER,
                                tipoOp + buffer.getId() + " por proceso " + p.getNombre()
                                        + " (PID " + p.getId() + ") [anfitrión: " + nomAnf + "] -> "
                                        + "(vacíos=" + buffer.getValorVacios() + ", llenos=" + buffer.getValorLlenos()
                                        + ", mutex=" + buffer.getValorMutex() + ")");
                    }
                }
                p.limpiarOperacionRemotaPendiente();
                listosParaDespertar.agregar(p);
            }
        }

        // Desbloquear procesos que completaron su latencia
        Iterador<PCB> ides = listosParaDespertar.iterador();
        while (ides.haySiguiente()) {
            PCB dp = ides.siguiente();
            bloqueados.eliminar(dp);
            dp.setEstado(EstadoProceso.LISTO);
            dp.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
            dp.setCiclosBloqueoRestantes(0);
            dp.avanzarPasoCpu(cicloActual);
            if (dp.getTiempoRestante() == 0) {
                terminar(dp, cicloActual);
            } else {
                planificador.encolarListo(dp);
            }
        }
    }

    void evaluarDesalojo(long cicloActual) {
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
                despachar(cicloActual);
                return;
            }
            return;
        }

        PCB candidato = planificador.peekSiguiente();
        if (candidato == null) {
            return;
        }
        // Comparar
        boolean desalojar = false;
        if (planificador.getPolitica() instanceof planificacion.Prioridades) {
            if (candidato.getPrioridad() < actual.getPrioridad()) {
                desalojar = true;
            }
        } else if (planificador.getPolitica() instanceof planificacion.EDF) {
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
        cpu.desalojar();
        actual.setEstado(EstadoProceso.LISTO);
        planificador.encolarListo(actual);
        if (siguiente.verificarDeadline(cicloActual)) {
            terminarPorDeadline(siguiente, cicloActual);
            despachar(cicloActual);
            return;
        }
        siguiente.setEstado(EstadoProceso.EJECUCION);
        cpu.asignar(siguiente);
    }

    /**
     * Limpia la CPU si el proceso que estaba en ella ya no es ejecutable o terminó.
     */
    public void limpiarCpuSiOciosa() {
        if (!cpu.estaOciosa()) {
            PCB actual = cpu.getProcesoActual();
            if (actual == null || actual.getTiempoRestante() == 0 || actual.getEstado() == EstadoProceso.TERMINADO) {
                cpu.liberar();
            }
        }
    }

    /** Indica si no queda nada por hacer (CPU ociosa y todas las colas vacías). */
    public boolean sinTrabajoPendiente() {
        limpiarCpuSiOciosa();
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

    public synchronized long getMemoriaDisponible() {
        return memoriaDisponible;
    }

    /**
     * Reserva memoria para un componente del sistema (buffers, etc.).
     *
     * @param cantidad bytes/KB a reservar.
     * @return true si hay espacio suficiente.
     */
    public synchronized boolean reservarMemoria(long cantidad) {
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
    public synchronized void liberarMemoria(long cantidad) {
        if (cantidad <= 0) {
            return;
        }
        memoriaDisponible = Math.min(memoriaTotal, memoriaDisponible + cantidad);
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
        if (simulador != null && simulador.getLogEventos() != null) {
            Iterador<PCB> it = nuevos.iterador();
            while (it.haySiguiente()) {
                PCB p = it.siguiente();
                if (!p.isCreacionRegistradaEnLog()) {
                    p.setCreacionRegistradaEnLog(true);
                    simulador.getLogEventos().registrar(simulacion.LogEventos.TipoEvento.CREACION,
                            "Creado proceso " + p.getNombre() + " (" + p.getId() + ") en estado NUEVO (RAM requerida: " + p.getMemoriaAsignada() + " KB)");
                }
            }
        }
    }

    public Simulador getSimulador() {
        return simulador;
    }

    public void registrarBuffer(Buffer buffer) {
        if (buffer == null) {
            return;
        }
        Iterador<Buffer> it = buffers.iterador();
        while (it.haySiguiente()) {
            Buffer b = it.siguiente();
            if (b != null && buffer.getId() != null && buffer.getId().equals(b.getId())) {
                return;
            }
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
