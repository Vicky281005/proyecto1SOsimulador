package nucleo;

import disco.DiscoSimulado;
import disco.OperacionRechazadaException;
import disco.ServicioArchivos;
import disco.SolicitudES;
import enums.EstadoProceso;
import enums.ModoCPU;
import enums.ModoUsuario;
import enums.MotivoBloqueo;
import enums.PermisoArchivo;
import enums.TipoInstruccion;
import enums.TipoOperacionES;
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
import java.util.concurrent.Semaphore;

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

    // Modelo de 7 estados: procesos cuya imagen está en el área de swap del disco
    private final ListaEnlazada<PCB> listosSuspendidos;
    private final ListaEnlazada<PCB> bloqueadosSuspendidos;

    // Proyecto 2: disco y sistema de archivos
    private DiscoSimulado disco;
    private ServicioArchivos servicioArchivos;

    /**
     * Operaciones que el disco terminó. El disco corre en su propio hilo, así que
     * solo deja aquí el aviso (protegido por {@code mutexFinES}); el Computador lo
     * atiende dentro de su propio tick para no tocar sus colas desde otro hilo.
     */
    private final ListaEnlazada<FinES> finesES = new ListaEnlazada<>();
    private final Semaphore mutexFinES = new Semaphore(1);

    private static final class FinES {
        final SolicitudES solicitud;
        final boolean exito;
        final String detalle;

        FinES(SolicitudES solicitud, boolean exito, String detalle) {
            this.solicitud = solicitud;
            this.exito = exito;
            this.detalle = detalle;
        }
    }

    // Planificador a mediano plazo (modelo de 7 estados)
    /** Proceso de Listo/Suspendido que se está trayendo de vuelta desde el swap (uno a la vez). */
    private PCB reactivandoEnCurso;
    private int totalSuspensiones;
    private int totalReactivaciones;

    /** Plazo relativo muy grande para que un proceso de E/S nunca venza por deadline. */
    private static final long DEADLINE_PROCESO_ES = 1_000_000L;

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
        this.listosSuspendidos = new ListaEnlazada<>();
        this.bloqueadosSuspendidos = new ListaEnlazada<>();
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
                    "Creado proceso " + proceso.getNombre() + " (" + proceso.getId() + ") en estado NUEVO (RAM requerida: " + proceso.getMemoriaAsignada() + " bloques)");
        }
    }

    @Override
    public void tick(long cicloActual) {
        // Métrica: cada proceso bloqueado al empezar este ciclo suma un ciclo de bloqueo.
        contarCiclosBloqueados();

        // Procesar bloqueos por latencia de red antes de admitir/despachar
        procesarBloqueosPorLatencia(cicloActual);

        // Procesar transiciones a bloqueo diferidas (E/S ejecutada en el ciclo previo en modo KERNEL)
        procesarBloqueosIOPendientes(cicloActual);

        // Despertar procesos cuyas operaciones de disco ya terminaron. Va después de
        // aplicar los bloqueos pendientes para que el proceso ya esté en Bloqueados.
        procesarFinesES(cicloActual);

        // Planificador a mediano plazo: avanzar suspendidos y reactivar si hay RAM.
        procesarSuspendidosPorTemporizador();
        reactivarSiHayMemoria(cicloActual);

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
                } else if (enEjecucion.getTiempoRestante() == 0 && !enEjecucion.isBloqueoIOPendiente()) {
                    terminar(enEjecucion, cicloActual);
                }
            }
        }

        // Evaluar desalojo al final por quantum.
        if (cpu.getProcesoActual() == null || !cpu.getProcesoActual().isBloqueoIOPendiente()) {
            evaluarDesalojo(cicloActual);
        }
        if (cpu.estaOciosa()) {
            despachar(cicloActual);
        }
    }

    /** Suma un ciclo de tiempo bloqueado a cada proceso que está en la lista de bloqueados. */
    private void contarCiclosBloqueados() {
        Iterador<PCB> it = bloqueados.iterador();
        while (it.haySiguiente()) {
            PCB p = it.siguiente();
            if (p != null) {
                p.incrementarTiempoBloqueado();
            }
        }
        Iterador<PCB> itSusp = bloqueadosSuspendidos.iterador();
        while (itSusp.haySiguiente()) {
            PCB p = itSusp.siguiente();
            if (p != null) {
                p.incrementarTiempoBloqueado();
            }
        }
    }

    private boolean esInstruccionKernel(TipoInstruccion tipo) {
        return tipo == TipoInstruccion.SEM_WAIT
                || tipo == TipoInstruccion.SEM_SIGNAL
                || tipo == TipoInstruccion.PRODUCIR
                || tipo == TipoInstruccion.CONSUMIR
                || tipo == TipoInstruccion.READ_IO
                || tipo == TipoInstruccion.WRITE_IO
                || tipo == TipoInstruccion.OPERACION_ES;
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
                    return procesarIO(proceso, instr, cicloActual);
                }
                case OPERACION_ES: {
                    return procesarOperacionES(proceso, cicloActual);
                }
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================================
    // PLANIFICADOR A MEDIANO PLAZO: SUSPENSIÓN Y REACTIVACIÓN (SWAP)
    // =========================================================================

    /**
     * Un proceso es candidato a suspenderse si está bloqueado esperando E/S (disco o
     * temporizador): su espera no depende de colas de semáforos, así que sacarlo de
     * la RAM no deja estructuras inconsistentes.
     */
    private boolean esCandidatoASuspender(PCB p) {
        return p != null
                && p.getEstado() == EstadoProceso.BLOQUEADO
                && !p.isBloqueoIOPendiente()
                && p.getMemoriaAsignada() > 0
                && (p.getMotivoBloqueo() == MotivoBloqueo.ESPERA_DISCO
                    || p.getMotivoBloqueo() == MotivoBloqueo.ESPERA_IO);
    }

    /**
     * Criterio de víctima: el bloqueado de MENOR prioridad (número de prioridad más
     * alto), porque es el que menos daño hace al sistema; a igual prioridad, el que
     * lleva MÁS ciclos bloqueado, porque es el que menos probable regresa pronto.
     */
    private PCB elegirVictima(ListaEnlazada<PCB> excluidos) {
        PCB mejor = null;
        Iterador<PCB> it = bloqueados.iterador();
        while (it.haySiguiente()) {
            PCB p = it.siguiente();
            if (!esCandidatoASuspender(p) || (excluidos != null && excluidos.contiene(p))) {
                continue;
            }
            if (mejor == null
                    || p.getPrioridad() > mejor.getPrioridad()
                    || (p.getPrioridad() == mejor.getPrioridad()
                        && p.getTiempoBloqueadoAcumulado() > mejor.getTiempoBloqueadoAcumulado())) {
                mejor = p;
            }
        }
        return mejor;
    }

    /**
     * Intenta hacer espacio en la RAM para admitir un proceso nuevo suspendiendo
     * bloqueados. Pre-selecciona la secuencia exacta de víctimas y verifica que el
     * swap alcance para TODAS antes de suspender la primera (evita suspensiones parciales).
     */
    private boolean liberarMemoriaSuspendiendo(PCB nuevo, long cicloActual) {
        if (disco == null || nuevo.getMemoriaAsignada() > memoriaTotal) {
            return false;
        }
        ListaEnlazada<PCB> seleccionadas = new ListaEnlazada<>();
        long memoriaProyectada = memoriaDisponible;
        long swapRequerido = 0;
        while (memoriaProyectada < nuevo.getMemoriaAsignada()) {
            PCB victima = elegirVictima(seleccionadas);
            if (victima == null) {
                return false;
            }
            seleccionadas.agregar(victima);
            memoriaProyectada += victima.getMemoriaAsignada();
            swapRequerido += victima.getMemoriaAsignada();
        }
        if (swapRequerido > disco.getBloquesLibresSwap()) {
            return false;
        }
        Iterador<PCB> itSel = seleccionadas.iterador();
        while (itSel.haySiguiente()) {
            if (!suspender(itSel.siguiente(), nuevo, cicloActual)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Suspende un proceso bloqueado: reserva sus bloques de swap, genera la solicitud
     * de escritura al disco, libera su RAM y lo pasa a Bloqueado/Suspendido.
     */
    private boolean suspender(PCB victima, PCB paraAdmitir, long cicloActual) {
        int cantidad = (int) victima.getMemoriaAsignada();
        int primerBloque = disco.asignarBloquesSwap(victima.getNombre() + " (PID " + victima.getId() + ")", cantidad);
        if (primerBloque < 0) {
            return false;
        }
        victima.asignarSwap(primerBloque, cantidad);
        victima.setEscrituraSwapPendiente(true);
        bloqueados.eliminar(victima);
        victima.setEstado(EstadoProceso.BLOQUEADO_SUSPENDIDO);
        bloqueadosSuspendidos.agregar(victima);
        memoriaDisponible = Math.min(memoriaTotal, memoriaDisponible + victima.getMemoriaAsignada());
        totalSuspensiones++;
        disco.encolarSolicitud(new SolicitudES(victima, TipoOperacionES.SUSPENDER, "", "",
                primerBloque, cantidad, cicloActual, true));
        simulacion.LogEventos log = (simulador != null) ? simulador.getLogEventos() : null;
        if (log != null) {
            log.registrar(simulacion.LogEventos.TipoEvento.SUSPENSION,
                    "Suspensión: " + victima.getNombre() + " (prioridad " + victima.getPrioridad()
                            + ", " + victima.getTiempoBloqueadoAcumulado() + " ciclos bloqueado) se escribe en swap (primer bloque "
                            + primerBloque + ", " + cantidad + " bloques encadenados) para admitir a "
                            + paraAdmitir.getNombre() + " (necesita " + paraAdmitir.getMemoriaAsignada()
                            + " bloques de RAM)");
        }
        return true;
    }

    /**
     * Los suspendidos con E/S de temporizador (READ_IO/WRITE_IO) siguen contando su
     * espera mientras están en swap; al cumplirse pasan a Listo/Suspendido.
     * Los que esperan al disco los despierta el aviso del disco.
     */
    private void procesarSuspendidosPorTemporizador() {
        if (bloqueadosSuspendidos.estaVacia()) {
            return;
        }
        ListaEnlazada<PCB> cumplidos = new ListaEnlazada<>();
        Iterador<PCB> it = bloqueadosSuspendidos.iterador();
        while (it.haySiguiente()) {
            PCB p = it.siguiente();
            if (p != null && p.getMotivoBloqueo() == MotivoBloqueo.ESPERA_IO) {
                long restantes = p.getCiclosBloqueoRestantes();
                if (restantes > 1) {
                    p.setCiclosBloqueoRestantes(restantes - 1);
                } else {
                    cumplidos.agregar(p);
                }
            }
        }
        simulacion.LogEventos log = (simulador != null) ? simulador.getLogEventos() : null;
        Iterador<PCB> itC = cumplidos.iterador();
        while (itC.haySiguiente()) {
            PCB p = itC.siguiente();
            bloqueadosSuspendidos.eliminar(p);
            p.setCiclosBloqueoRestantes(0);
            p.setEstado(EstadoProceso.LISTO_SUSPENDIDO);
            listosSuspendidos.agregar(p);
            if (log != null) {
                log.registrar(simulacion.LogEventos.TipoEvento.DESBLOQUEO,
                        "Evento E/S completado para " + p.getNombre() + " en " + nombre
                                + ": pasa de Bloqueado/Suspendido a Listo/Suspendido");
            }
        }
    }

    /**
     * Cuando hay RAM libre, trae de vuelta desde el swap al Listo/Suspendido de mayor
     * prioridad que quepa (y cuya escritura en swap ya concluyó). Tienen preferencia sobre
     * los procesos Nuevos. La reactivación también es una solicitud al disco (lectura del swap);
     * se reserva la RAM de inmediato.
     */
    private void reactivarSiHayMemoria(long cicloActual) {
        if (disco == null || reactivandoEnCurso != null || listosSuspendidos.estaVacia()) {
            return;
        }
        PCB elegido = null;
        Iterador<PCB> it = listosSuspendidos.iterador();
        while (it.haySiguiente()) {
            PCB p = it.siguiente();
            if (p != null && !p.isEscrituraSwapPendiente()
                    && p.getMemoriaAsignada() <= memoriaDisponible
                    && p.getPrimerBloqueSwap() != PCB.SIN_ASIGNAR
                    && (elegido == null || p.getPrioridad() < elegido.getPrioridad())) {
                elegido = p;
            }
        }
        if (elegido == null) {
            return;
        }
        memoriaDisponible -= elegido.getMemoriaAsignada();
        reactivandoEnCurso = elegido;
        disco.encolarSolicitud(new SolicitudES(elegido, TipoOperacionES.REACTIVAR, "", "",
                elegido.getPrimerBloqueSwap(), elegido.getBloquesSwap(), cicloActual, true));
        simulacion.LogEventos log = (simulador != null) ? simulador.getLogEventos() : null;
        if (log != null) {
            log.registrar(simulacion.LogEventos.TipoEvento.REACTIVACION,
                    "Reactivación: se lee " + elegido.getNombre() + " desde el swap (bloque "
                            + elegido.getPrimerBloqueSwap() + ") porque hay RAM libre");
        }
    }

    /** Atiende el fin de una solicitud de swap (suspender o reactivar). */
    private void completarSwap(SolicitudES sol, boolean exito, long cicloActual) {
        PCB p = sol.getProceso();
        simulacion.LogEventos log = (simulador != null) ? simulador.getLogEventos() : null;
        if (sol.getOperacion() == TipoOperacionES.SUSPENDER) {
            if (p == null || p.getEstado() == EstadoProceso.TERMINADO) {
                return;
            }
            p.setEscrituraSwapPendiente(false);
            if (log != null) {
                log.registrar(simulacion.LogEventos.TipoEvento.SUSPENSION,
                        "Suspensión completada: " + p.getNombre() + " ya está escrito en el swap");
            }
            return;
        }
        // REACTIVAR: puede haber terminado por deadline mientras se leía.
        if (p == null || p != reactivandoEnCurso || p.getEstado() != EstadoProceso.LISTO_SUSPENDIDO) {
            return;
        }
        if (disco != null) {
            disco.liberarBloquesSwap(p.getPrimerBloqueSwap());
        }
        p.liberarSwap();
        listosSuspendidos.eliminar(p);
        reactivandoEnCurso = null;
        totalReactivaciones++;
        p.setEstado(EstadoProceso.LISTO);
        planificador.encolarListo(p);
        if (log != null) {
            log.registrar(simulacion.LogEventos.TipoEvento.REACTIVACION,
                    "Reactivación completada: " + p.getNombre() + " vuelve a Listo y libera su swap");
        }
    }

    public int getTotalSuspensiones() {
        return totalSuspensiones;
    }

    public int getTotalReactivaciones() {
        return totalReactivaciones;
    }

    // =========================================================================
    // PROYECTO 2: PROCESOS DE E/S SOBRE EL SISTEMA DE ARCHIVOS
    // =========================================================================

    public void setDisco(DiscoSimulado disco) {
        this.disco = disco;
    }

    public DiscoSimulado getDisco() {
        return disco;
    }

    public void setServicioArchivos(ServicioArchivos servicioArchivos) {
        this.servicioArchivos = servicioArchivos;
    }

    /**
     * Crea el proceso de E/S que ejecuta una operación sobre el sistema de archivos
     * y lo recibe en estado NUEVO. Es la única vía para operar sobre archivos: la
     * GUI nunca toca el disco directamente. El programa tiene dos instrucciones: la
     * operación (modo kernel, bloquea hasta que el disco termine) y un paso final de CPU.
     */
    public PCB crearProcesoES(String nombreProceso, TipoOperacionES operacion, String ruta,
                              String nuevoNombre, int tamanoBloques, ModoUsuario modo,
                              String usuario, long cicloActual) {
        return crearProcesoES(nombreProceso, operacion, ruta, nuevoNombre,
                tamanoBloques, modo, usuario, null, cicloActual);
    }

    /** Variante que permite al Administrador especificar permiso inicial (Público o Privado). */
    public PCB crearProcesoES(String nombreProceso, TipoOperacionES operacion, String ruta,
                              String nuevoNombre, int tamanoBloques, ModoUsuario modo,
                              String usuario, PermisoArchivo permisoSolicitado, long cicloActual) {
        if (operacion == null || !operacion.esOperacionArchivo()) {
            throw new IllegalArgumentException("La operación debe ser CREAR, LEER, ACTUALIZAR o ELIMINAR.");
        }
        if (ruta == null || ruta.isBlank()) {
            throw new IllegalArgumentException("La ruta objetivo no puede estar vacía.");
        }
        ListaEnlazada<Instruccion> programa = new ListaEnlazada<>();
        programa.agregar(new Instruccion(TipoInstruccion.OPERACION_ES, operacion.getCodigoJson()));
        programa.agregar(new Instruccion(TipoInstruccion.CPU));
        String nom = (nombreProceso == null || nombreProceso.isBlank())
                ? ("ES-" + operacion.getEtiqueta() + "-" + ruta) : nombreProceso;
        PCB p = new PCB(nom, id, TipoProceso.IO_BOUND, 1, 1, DEADLINE_PROCESO_ES,
                "", 0, 0, programa, cicloActual);
        p.setOperacion(operacion);
        p.setRutaObjetivo(ruta);
        p.setNuevoNombre(nuevoNombre);
        p.setTamanoArchivoBloques(tamanoBloques);
        p.setModoSolicitante(modo);
        p.setPermisoSolicitado(permisoSolicitado);
        p.setUsuario(usuario);
        recibirProceso(p);
        return p;
    }

    /**
     * Instrucción de modo kernel: pide al sistema de archivos validar y armar la
     * solicitud, la encola en el disco y marca el proceso para bloquearse (motivo E/S).
     * Si se rechaza, el proceso no se bloquea y termina; la simulación sigue.
     */
    private boolean procesarOperacionES(PCB proceso, long cicloActual) {
        simulacion.LogEventos log = (simulador != null) ? simulador.getLogEventos() : null;
        if (disco == null || servicioArchivos == null) {
            rechazarES(proceso, "no hay disco o sistema de archivos configurado", log);
            return false;
        }
        try {
            SolicitudES solicitud = servicioArchivos.preparar(proceso, cicloActual);
            // Primero se marca el bloqueo y después se encola, para que ninguna
            // respuesta del disco pueda llegar antes de que el proceso esté marcado.
            proceso.setEsperandoDisco(true);
            proceso.setCiclosBloqueoRestantes(0);
            proceso.setBloqueoIOPendiente(true);
            disco.encolarSolicitud(solicitud);
            if (log != null) {
                log.registrar(simulacion.LogEventos.TipoEvento.DISCO,
                        "Solicitud de E/S encolada en el disco: " + solicitud + " (modo kernel, "
                                + proceso.getNombre() + ")");
            }
        } catch (OperacionRechazadaException ex) {
            rechazarES(proceso, ex.getMessage(), log);
        }
        return false;
    }

    private void rechazarES(PCB proceso, String motivo, simulacion.LogEventos log) {
        proceso.setRechazoES(motivo);
        if (log != null) {
            log.registrar(simulacion.LogEventos.TipoEvento.RECHAZO_ES,
                    "Operación RECHAZADA de " + proceso.getNombre() + " (" + proceso.getModoSolicitante()
                            + ", " + proceso.getUsuario() + "): " + motivo);
        }
    }

    /**
     * Llamado desde el hilo del disco cuando termina una solicitud. Solo deja el
     * aviso en una lista protegida; no modifica ninguna cola del Computador.
     */
    public void encolarFinES(SolicitudES solicitud, boolean exito, String detalle) {
        mutexFinES.acquireUninterruptibly();
        try {
            finesES.agregar(new FinES(solicitud, exito, detalle));
        } finally {
            mutexFinES.release();
        }
    }

    /** Atiende, dentro del tick, las operaciones que el disco terminó. */
    private void procesarFinesES(long cicloActual) {
        ListaEnlazada<FinES> lote = new ListaEnlazada<>();
        mutexFinES.acquireUninterruptibly();
        try {
            while (!finesES.estaVacia()) {
                lote.agregar(finesES.eliminarPrimero());
            }
        } finally {
            mutexFinES.release();
        }
        Iterador<FinES> it = lote.iterador();
        while (it.haySiguiente()) {
            FinES fin = it.siguiente();
            SolicitudES sol = fin.solicitud;
            if (sol.getOperacion().esOperacionSwap()) {
                completarSwap(sol, fin.exito, cicloActual);
                continue;
            }
            PCB p = sol.getProceso();
            if (p == null || p.getEstado() == EstadoProceso.TERMINADO) {
                if (servicioArchivos != null) {
                    servicioArchivos.completar(sol, false);
                }
                continue;
            }
            if (servicioArchivos != null) {
                servicioArchivos.completar(sol, fin.exito);
            }
            p.setEsperandoDisco(false);
            boolean pasoAListoSuspendido = false;
            if (p.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO) {
                bloqueadosSuspendidos.eliminar(p);
                p.setEstado(EstadoProceso.LISTO_SUSPENDIDO);
                listosSuspendidos.agregar(p);
                pasoAListoSuspendido = true;
            } else if (p.getEstado() == EstadoProceso.BLOQUEADO) {
                bloqueados.eliminar(p);
                p.setEstado(EstadoProceso.LISTO);
                if (p.getTiempoRestante() == 0) {
                    terminar(p, cicloActual);
                } else {
                    planificador.encolarListo(p);
                }
            }
            simulacion.LogEventos log = (simulador != null) ? simulador.getLogEventos() : null;
            if (log != null) {
                String destinoEstado = pasoAListoSuspendido
                        ? " (pasa de Bloqueado/Suspendido a Listo/Suspendido)"
                        : "";
                log.registrar(simulacion.LogEventos.TipoEvento.DESBLOQUEO,
                        "Desbloqueado " + p.getNombre() + " en " + nombre
                                + " tras completar la operación de disco (" + (fin.exito ? "éxito" : "con error") + ")"
                                + destinoEstado);
            }
        }
    }

    private boolean procesarIO(PCB proceso, Instruccion instr, long cicloActual) {
        int duracion = Math.max(1, obtenerDuracionIO(proceso, instr));
        proceso.setCiclosBloqueoRestantes(duracion);
        proceso.setBloqueoIOPendiente(true);
        return false;
    }

    private void procesarBloqueosIOPendientes(long cicloActual) {
        PCB enCpu = cpu.getProcesoActual();
        if (enCpu != null && enCpu.isBloqueoIOPendiente()) {
            enCpu.setBloqueoIOPendiente(false);
            cpu.liberar();
            boolean esperaDisco = enCpu.isEsperandoDisco();
            MotivoBloqueo motivoBlk = esperaDisco ? MotivoBloqueo.ESPERA_DISCO : MotivoBloqueo.ESPERA_IO;
            enCpu.bloquear(motivoBlk, "");
            planificador.eliminarListo(enCpu);
            bloqueados.agregar(enCpu);
            simulacion.LogEventos logBlk = (simulador != null) ? simulador.getLogEventos() : null;
            if (logBlk != null) {
                String sufijo = esperaDisco ? "" : " (" + enCpu.getCiclosBloqueoRestantes() + " ciclos)";
                logBlk.registrar(simulacion.LogEventos.TipoEvento.BLOQUEO,
                        "Bloqueado " + enCpu.getNombre() + " en " + nombre + " por "
                                + motivoBlk.getEtiqueta() + sufijo);
            }
        }
    }

    private int obtenerDuracionIO(PCB proceso, Instruccion instr) {
        if (instr != null && instr.getParametro() != null && !instr.getParametro().trim().isEmpty()) {
            try {
                int d = Integer.parseInt(instr.getParametro().trim());
                if (d > 0) {
                    return d;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (proceso != null && proceso.getCadaCuantoCiclos() > 0) {
            return proceso.getCadaCuantoCiclos();
        }
        if (simulador != null) {
            return simulador.getDuracionIOCiclos();
        }
        return 2;
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
                Semaforo semVacios = buffer.getVacios();
                if (!proceso.isRecursoSemaforoAdquirido()) {
                    synchronized (semVacios) {
                        if (!semVacios.esperar(proceso, MotivoBloqueo.SEMAFORO_VACIO)) {
                            bloquearEnSemaforo(proceso, semVacios, MotivoBloqueo.SEMAFORO_VACIO, buffer.getId());
                            return true;
                        }
                    }
                    proceso.setRecursoSemaforoAdquirido(true);
                }
                Semaforo semMutex = buffer.getMutex().getSemaforo();
                synchronized (semMutex) {
                    if (!buffer.getMutex().esperar(proceso)) {
                        bloquearEnSemaforo(proceso, semMutex, MotivoBloqueo.MUTEX, buffer.getId());
                        return true;
                    }
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
                Semaforo semLlenos = buffer.getLlenos();
                if (!proceso.isRecursoSemaforoAdquirido()) {
                    synchronized (semLlenos) {
                        if (!semLlenos.esperar(proceso, MotivoBloqueo.SEMAFORO_LLENO)) {
                            bloquearEnSemaforo(proceso, semLlenos, MotivoBloqueo.SEMAFORO_LLENO, buffer.getId());
                            return true;
                        }
                    }
                    proceso.setRecursoSemaforoAdquirido(true);
                }
                Semaforo semMutex = buffer.getMutex().getSemaforo();
                synchronized (semMutex) {
                    if (!buffer.getMutex().esperar(proceso)) {
                        bloquearEnSemaforo(proceso, semMutex, MotivoBloqueo.MUTEX, buffer.getId());
                        return true;
                    }
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
                synchronized (s) {
                    if (!s.esperar(proceso, MotivoBloqueo.SEMAFORO_VACIO)) {
                        bloquearEnSemaforo(proceso, s, MotivoBloqueo.SEMAFORO_VACIO, buffer.getId());
                        return true;
                    }
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
            if (proceso.getMemoriaAsignada() <= memoriaDisponible
                    || liberarMemoriaSuspendiendo(proceso, cicloActual)) {
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

        proceso.setBloqueoIOPendiente(false);
        nuevos.eliminar(proceso);
        bloqueados.eliminar(proceso);
        planificador.eliminarListo(proceso);
        listosSuspendidos.eliminar(proceso);
        bloqueadosSuspendidos.eliminar(proceso);
        if (proceso.getPrimerBloqueSwap() != PCB.SIN_ASIGNAR && disco != null) {
            disco.liberarBloquesSwap(proceso.getPrimerBloqueSwap());
            proceso.liberarSwap();
        }
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
        // Un proceso suspendido no ocupa RAM, salvo que se esté reactivando (RAM ya reservada).
        boolean estabaSuspendido = proceso.getEstado().estaSuspendido();
        boolean reactivando = (proceso == reactivandoEnCurso);
        if (reactivando) {
            reactivandoEnCurso = null;
        }
        liberarRecursosProceso(proceso);

        boolean admitido = proceso.getEstado() != EstadoProceso.NUEVO
                && (!estabaSuspendido || reactivando);
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
        if (proceso == null || proceso.getEstado() == EstadoProceso.TERMINADO) {
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
        if (proceso == null || proceso.getEstado() == EstadoProceso.TERMINADO) {
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

        // Los procesos suspendidos también pueden vencer su deadline.
        ListaEnlazada<PCB> suspVencidos = new ListaEnlazada<>();
        Iterador<PCB> itLs = listosSuspendidos.iterador();
        while (itLs.haySiguiente()) {
            PCB p = itLs.siguiente();
            if (p != null && p.verificarDeadline(cicloActual) && p.getTiempoRestante() > 0) {
                suspVencidos.agregar(p);
            }
        }
        Iterador<PCB> itBs = bloqueadosSuspendidos.iterador();
        while (itBs.haySiguiente()) {
            PCB p = itBs.siguiente();
            if (p != null && p.verificarDeadline(cicloActual) && p.getTiempoRestante() > 0) {
                suspVencidos.agregar(p);
            }
        }
        Iterador<PCB> itSv = suspVencidos.iterador();
        while (itSv.haySiguiente()) {
            terminarPorDeadline(itSv.siguiente(), cicloActual);
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
            } else if (p.getMotivoBloqueo() == MotivoBloqueo.ESPERA_IO) {
                long restantes = p.getCiclosBloqueoRestantes();
                if (restantes > 1) {
                    p.setCiclosBloqueoRestantes(restantes - 1);
                    continue;
                }
                p.setCiclosBloqueoRestantes(0);
                listosParaDespertar.agregar(p);
            }
        }

        // Desbloquear procesos que completaron su latencia o E/S
        Iterador<PCB> ides = listosParaDespertar.iterador();
        while (ides.haySiguiente()) {
            PCB dp = ides.siguiente();
            MotivoBloqueo motivoPrevio = dp.getMotivoBloqueo();
            bloqueados.eliminar(dp);
            dp.setEstado(EstadoProceso.LISTO);
            dp.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
            dp.setCiclosBloqueoRestantes(0);
            if (motivoPrevio == MotivoBloqueo.LATENCIA_RED) {
                dp.avanzarPasoCpu(cicloActual);
            }
            if (dp.getTiempoRestante() == 0) {
                terminar(dp, cicloActual);
            } else {
                planificador.encolarListo(dp);
            }
            simulacion.LogEventos logDesb = (simulador != null) ? simulador.getLogEventos() : null;
            if (logDesb != null) {
                String detalle = (motivoPrevio == MotivoBloqueo.ESPERA_IO)
                        ? " tras completar operación de E/S"
                        : " tras cumplir latencia de red";
                logDesb.registrar(simulacion.LogEventos.TipoEvento.DESBLOQUEO,
                        "Desbloqueado " + dp.getNombre() + " en " + nombre + detalle);
            }
        }
    }

    void evaluarDesalojo(long cicloActual) {
        PCB actual = cpu.getProcesoActual();
        if (actual == null || planificador.estaVacio() || actual.isBloqueoIOPendiente()) {
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
                simulacion.LogEventos log = (simulador != null) ? simulador.getLogEventos() : null;
                if (log != null) {
                    log.registrar(simulacion.LogEventos.TipoEvento.DESALOJO,
                            actual.getNombre() + " desalojado en " + nombre
                                    + " (quantum agotado: " + rr.getQuantum() + ")");
                }
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
        simulacion.LogEventos log = (simulador != null) ? simulador.getLogEventos() : null;
        if (log != null) {
            log.registrar(simulacion.LogEventos.TipoEvento.DESALOJO,
                    actual.getNombre() + " desalojado en " + nombre + " por " + siguiente.getNombre()
                            + " (" + planificador.getPolitica().getNombre() + ")");
        }
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
            if (actual == null || (actual.getTiempoRestante() == 0 && !actual.isBloqueoIOPendiente()) || actual.getEstado() == EstadoProceso.TERMINADO) {
                cpu.liberar();
            }
        }
    }

    /** Indica si no queda nada por hacer (solo lectura: no muta la CPU). */
    public boolean sinTrabajoPendiente() {
        PCB actual = cpu.getProcesoActual();
        boolean cpuLibre = cpu.estaOciosa()
                || actual == null
                || (actual.getTiempoRestante() == 0 && !actual.isBloqueoIOPendiente())
                || actual.getEstado() == EstadoProceso.TERMINADO;
        return cpuLibre
                && planificador.estaVacio()
                && nuevos.estaVacia()
                && bloqueados.estaVacia()
                && listosSuspendidos.estaVacia()
                && bloqueadosSuspendidos.estaVacia();
    }

    @Override
    public String getNombre() {
        return nombre;
    }

    @Override
    public String toString() {
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

    public int getCantidadListosSuspendidos() {
        return listosSuspendidos.getTamano();
    }

    public int getCantidadBloqueadosSuspendidos() {
        return bloqueadosSuspendidos.getTamano();
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
                            "Creado proceso " + p.getNombre() + " (" + p.getId() + ") en estado NUEVO (RAM requerida: " + p.getMemoriaAsignada() + " bloques)");
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

    public Iterador<PCB> iteradorListosSuspendidos() {
        return listosSuspendidos.iterador();
    }

    public Iterador<PCB> iteradorBloqueadosSuspendidos() {
        return bloqueadosSuspendidos.iterador();
    }
}
