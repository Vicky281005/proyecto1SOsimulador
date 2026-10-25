package simulacion;

import estructuras.Iterador;
import estructuras.ListaEnlazada;
import hardware.Reloj;
import modelo.PCB;
import nucleo.Computador;
import sincronizacion.Buffer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Motor de simulación de ÁvilaOS.
 *
 * <p>Es el "director de orquesta": es dueño del {@link Reloj} global y de los
 * {@link Computador}es registrados. Mientras algún computador tenga trabajo
 * pendiente, avanza el reloj ciclo a ciclo, de modo que todos los componentes
 * reaccionen sincronizados (primero decide el sistema operativo de cada
 * computador, después ejecuta su CPU).</p>
 *
 * <p>Como cada instrucción consume exactamente un ciclo, el número de ciclos
 * de una simulación es acotado. Aun así, un error de programación o un proceso
 * que quede bloqueado sin nadie que lo despierte podrían impedir que la
 * simulación termine; por eso existe un <b>límite de seguridad</b> de ciclos:
 * al alcanzarlo, la ejecución se detiene y se marca
 * {@link #isLimiteAlcanzado()} en lugar de quedarse en un bucle infinito.</p>
 *
 * <h2>Dos formas de ejecutar</h2>
 * <ul>
 *   <li>{@link #ejecutar()}: síncrona y a máxima velocidad; bloquea al llamador
 *       hasta que no queda trabajo (la usa la demo y las pruebas).</li>
 *   <li>{@link #iniciar()}: en un hilo propio, con {@link #pausar()},
 *       {@link #reanudar()}, {@link #detener()} y duración de ciclo configurable
 *       en caliente ({@link #setDuracionCicloMs(long)}). Es la que usa la GUI.
 *       Si no queda trabajo, el hilo espera sin avanzar el reloj hasta que llegue
 *       un proceso nuevo ({@link #cargarProceso(PCB)}).</li>
 * </ul>
 *
 * <h2>Exclusión mutua</h2>
 * <p>Mientras la simulación corre en su hilo, otros hilos (la GUI) cargan procesos
 * y leen el estado. Las colas son estructuras propias no seguras entre hilos, así
 * que un semáforo binario ({@code mutexEstado}) se toma durante cada ciclo, al
 * cargar un proceso y al registrar un computador. Para <b>leer</b> el estado desde
 * otro hilo use {@link #leerEstado(Runnable)}, que toma ese mismo semáforo.</p>
 */
public class Simulador {

    /** Límite de ciclos usado cuando no se especifica uno. */
    public static final long CICLOS_MAXIMOS_POR_DEFECTO = 100_000L;

    /** Duración por defecto de un ciclo en la ejecución con {@link #iniciar()}, en ms. */
    public static final long DURACION_CICLO_POR_DEFECTO_MS = 500;

    /** Espera máxima (ms) a que termine el hilo de la simulación al detenerla. */
    private static final long ESPERA_CIERRE_MS = 2000;

    /** Cuánto espera (ms) el hilo, sin avanzar el reloj, antes de volver a mirar si hay trabajo. */
    private static final long ESPERA_OCIOSO_MS = 100;

    private static final int PASO_AVANZO = 0;
    private static final int PASO_OCIOSO = 1;
    private static final int PASO_LIMITE = 2;

    private final Reloj reloj;
    private final ListaEnlazada<Computador> computadores;
    private final long ciclosMaximos;

    /** Mutex binario: protege el estado de los computadores durante ciclos, cargas y lecturas. */
    private final Semaphore mutexEstado = new Semaphore(1, true); // justo: la GUI no deja sin turno al hilo

    /** Puerta de pausa (binaria): pausar() la toma y reanudar() la suelta; el hilo la cruza en cada ciclo. */
    private final Semaphore puertaPausa = new Semaphore(1);

    /** Señal de "llegó trabajo nuevo" para despertar al hilo cuando está ocioso. */
    private final Semaphore trabajoNuevo = new Semaphore(0);

    private volatile long duracionCicloMs = DURACION_CICLO_POR_DEFECTO_MS;
    private volatile boolean limiteAlcanzado;
    private volatile boolean enMarcha;
    private volatile boolean detenida;
    private boolean pausada;
    private Thread hiloSimulacion;

    /** Utilización de CPU de cada computador, ciclo por ciclo (para la gráfica). */
    private final HistorialUtilizacion historial = new HistorialUtilizacion();
    private final LogEventos logEventos = new LogEventos(this);

    private final ListaEnlazada<Buffer> buffers = new ListaEnlazada<>();

    /** Latencia de red configurable para accesos remotos a buffers (ciclos). */
    private int latenciaRedCiclos = 3;

    /**
     * Si es true, el reloj sigue avanzando ciclo a ciclo aun cuando no haya procesos
     * activos, manteniendo la simulación en marcha y la CPU ociosa.
     * Si es false, el reloj espera síncronamente a que llegue trabajo nuevo.
     */
    private volatile boolean mantenerRelojActivo = false;
    private volatile boolean notificadoFinOcioso = false;

    public boolean isMantenerRelojActivo() {
        return mantenerRelojActivo;
    }

    public void setMantenerRelojActivo(boolean mantenerRelojActivo) {
        this.mantenerRelojActivo = mantenerRelojActivo;
    }

    public int getLatenciaRedCiclos() {
        return latenciaRedCiclos;
    }

    public void setLatenciaRedCiclos(int latenciaRedCiclos) {
        if (latenciaRedCiclos < 0) {
            throw new IllegalArgumentException("La latencia de red no puede ser negativa.");
        }
        this.latenciaRedCiclos = latenciaRedCiclos;
    }

    public void registrarBuffer(Buffer buffer) {
        if (buffer == null) {
            return;
        }
        mutexEstado.acquireUninterruptibly();
        try {
            if (buscarBuffer(buffer.getId()) != null) {
                return;
            }
            buffers.agregar(buffer);
            Computador anfitrion = buscarComputador(buffer.getIdComputadorAnfitrion());
            if (anfitrion != null) {
                anfitrion.registrarBuffer(buffer);
            }
        } finally {
            mutexEstado.release();
        }
    }

    public Buffer buscarBuffer(String id) {
        if (id == null) {
            return null;
        }
        Iterador<Buffer> it = buffers.iterador();
        while (it.haySiguiente()) {
            Buffer b = it.siguiente();
            if (id.equals(b.getId())) {
                return b;
            }
        }
        return null;
    }

    public Iterador<Buffer> iteradorBuffers() {
        return buffers.iterador();
    }

    /**
     * Historial de utilización de CPU. Léalo siempre dentro de {@link #leerEstado(Runnable)}
     * mientras la simulación corra en su hilo.
     */
    public HistorialUtilizacion getHistorialUtilizacion() {
        return historial;
    }

    public LogEventos getLogEventos() {
        return logEventos;
    }

    public Simulador() {
        this(CICLOS_MAXIMOS_POR_DEFECTO);
    }

    /**
     * @param ciclosMaximos número máximo de ciclos que puede durar una
     *                      ejecución antes de detenerse por seguridad.
     */
    public Simulador(long ciclosMaximos) {
        if (ciclosMaximos <= 0) {
            throw new IllegalArgumentException("El límite de ciclos debe ser mayor que cero.");
        }
        this.reloj = new Reloj();
        this.computadores = new ListaEnlazada<>();
        this.ciclosMaximos = ciclosMaximos;
        this.limiteAlcanzado = false;
    }

    /**
     * Agrega un computador a la simulación y lo registra en el reloj global,
     * de modo que sea notificado en cada ciclo. Solo se registra el computador,
     * nunca su CPU por separado: el computador conduce su propia CPU.
     *
     * @param computador computador a agregar.
     */
    public void registrarComputador(Computador computador) {
        if (computador == null) {
            throw new IllegalArgumentException("No se puede registrar un computador nulo.");
        }
        mutexEstado.acquireUninterruptibly();
        try {
            if (computadores.contiene(computador)) {
                return; // ya estaba registrado, no se duplica
            }
            computador.setSimulador(this);
            computadores.agregar(computador);
            reloj.registrar(computador);
        } finally {
            mutexEstado.release();
        }
    }

    /**
     * Envía un proceso nuevo al computador al que está asignado.
     *
     * @param proceso proceso en estado NUEVO.
     */
    public void cargarProceso(PCB proceso) {
        if (proceso == null) {
            throw new IllegalArgumentException("No se puede cargar un proceso nulo.");
        }
        int idComputador = proceso.getComputadorAsignado();
        if (idComputador == PCB.SIN_ASIGNAR) {
            throw new IllegalStateException(
                    "El proceso '" + proceso.getNombre() + "' no tiene computador asignado.");
        }
        mutexEstado.acquireUninterruptibly();
        try {
            Computador destino = buscarComputador(idComputador);
            if (destino == null) {
                throw new IllegalArgumentException(
                        "No existe un computador con id " + idComputador + " para el proceso '"
                                + proceso.getNombre() + "'.");
            }
            destino.recibirProceso(proceso);
            notificadoFinOcioso = false;
        } finally {
            mutexEstado.release();
        }
        trabajoNuevo.release(); // despierta al hilo de la simulación si estaba ocioso
    }

    /**
     * Ejecuta la simulación de forma síncrona y a máxima velocidad: avanza el reloj
     * ciclo a ciclo mientras quede trabajo pendiente y no se haya superado el límite
     * de seguridad. No espera la duración de ciclo configurada.
     *
     * @return el total de ciclos transcurridos desde el inicio de la simulación.
     * @throws IllegalStateException si la simulación ya fue iniciada con {@link #iniciar()}.
     */
    public long ejecutar() {
        synchronized (this) {
            if (hiloSimulacion != null) {
                throw new IllegalStateException(
                        "La simulación ya corre en su propio hilo; use pausar/reanudar/detener.");
            }
        }
        limiteAlcanzado = false;
        int paso;
        do {
            paso = darPaso();
        } while (paso == PASO_AVANZO);
        // Se salió del bucle por el límite, no porque la simulación terminara.
        limiteAlcanzado = (paso == PASO_LIMITE);
        return reloj.getCicloActual();
    }

    /**
     * Un paso de la simulación, bajo exclusión mutua: si hay trabajo y no se llegó al
     * límite, avanza un ciclo del reloj.
     *
     * @return {@code PASO_AVANZO}, {@code PASO_OCIOSO} (no hay trabajo) o
     *         {@code PASO_LIMITE} (hay trabajo pero se alcanzó el límite de ciclos).
     */
    private int darPaso() {
        mutexEstado.acquireUninterruptibly();
        try {
            boolean hayTrabajo = hayTrabajoPendiente();
            if (!hayTrabajo) {
                // Manejar el estado de la CPU limpiamente en todos los computadores
                Iterador<Computador> it = computadores.iterador();
                while (it.haySiguiente()) {
                    Computador c = it.siguiente();
                    if (c != null && c.getCantidadNuevos() == 0 && c.getPlanificador().estaVacio() && c.getCantidadBloqueados() == 0) {
                        if (!c.getCpu().estaOciosa()) {
                            c.getCpu().liberar();
                        }
                    }
                }
                if (!notificadoFinOcioso && tieneProcesosCreados()) {
                    notificadoFinOcioso = true;
                    if (logEventos != null) {
                        logEventos.registrar(LogEventos.TipoEvento.OTRO,
                                "Sin procesos activos en el clúster (Nuevos: 0, Listos: 0, Bloqueados: 0). CPU ociosa a la espera de nuevos procesos (ciclo "
                                        + reloj.getCicloActual() + ").");
                    }
                }
                if (!mantenerRelojActivo) {
                    return PASO_OCIOSO;
                }
            } else {
                notificadoFinOcioso = false;
            }

            if (reloj.getCicloActual() >= ciclosMaximos) {
                return PASO_LIMITE;
            }
            reloj.avanzarCiclo();
            historial.registrarCiclo(reloj.getCicloActual(), computadores.iterador());
            return PASO_AVANZO;
        } finally {
            mutexEstado.release();
        }
    }

    private boolean tieneProcesosCreados() {
        Iterador<Computador> it = computadores.iterador();
        while (it.haySiguiente()) {
            Computador c = it.siguiente();
            if (c != null && (c.getCantidadTerminados() > 0 || c.getCantidadNuevos() > 0 || c.getCantidadBloqueados() > 0 || !c.getPlanificador().estaVacio())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Inicia la simulación en un hilo propio. Avanza un ciclo cada
     * {@link #getDuracionCicloMs()} milisegundos. Cuando no hay trabajo, el hilo espera
     * sin avanzar el reloj hasta que llegue un proceso nuevo; se detiene con
     * {@link #detener()} o al alcanzar el límite de seguridad de ciclos.
     *
     * @throws IllegalStateException si ya fue iniciada o detenida (no es reutilizable).
     */
    public synchronized void iniciar() {
        if (hiloSimulacion != null || detenida) {
            throw new IllegalStateException("La simulación ya fue iniciada.");
        }
        limiteAlcanzado = false;
        enMarcha = true;
        hiloSimulacion = new Thread(this::bucle, "Simulador");
        hiloSimulacion.setDaemon(true);
        hiloSimulacion.start();
    }

    /** Bucle del hilo de la simulación. */
    private void bucle() {
        try {
            while (!detenida) {
                // Puerta de pausa: si alguien llamó pausar(), el hilo se queda aquí.
                puertaPausa.acquire();
                puertaPausa.release();
                if (detenida) {
                    break;
                }
                int paso = darPaso();
                if (paso == PASO_LIMITE) {
                    limiteAlcanzado = true;
                    break;
                }
                if (paso == PASO_AVANZO) {
                    long espera = duracionCicloMs;
                    if (espera > 0) {
                        Thread.sleep(espera);
                    }
                } else if (trabajoNuevo.tryAcquire(ESPERA_OCIOSO_MS, TimeUnit.MILLISECONDS)) {
                    trabajoNuevo.drainPermits();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            enMarcha = false;
        }
    }

    /** Pausa la simulación: el ciclo en curso termina y el siguiente no empieza. Idempotente. */
    public synchronized void pausar() {
        if (!pausada) {
            puertaPausa.acquireUninterruptibly();
            pausada = true;
        }
    }

    /** Reanuda una simulación pausada. Idempotente. */
    public synchronized void reanudar() {
        if (pausada) {
            pausada = false;
            puertaPausa.release();
        }
    }

    /**
     * Detiene la simulación definitivamente: termina el hilo de la simulación y los
     * hilos del reloj. Para empezar de nuevo se debe crear otro Simulador. Idempotente.
     * No debe llamarse desde dentro de {@link #leerEstado(Runnable)}.
     */
    public void detener() {
        Thread hilo;
        synchronized (this) {
            if (detenida) {
                return;
            }
            detenida = true;
            reanudar();
            hilo = hiloSimulacion;
        }
        if (hilo != null) {
            hilo.interrupt();
            try {
                hilo.join(ESPERA_CIERRE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        reloj.detener();
        enMarcha = false;
    }

    /**
     * Cambia la duración de un ciclo en la ejecución con {@link #iniciar()}; se aplica
     * a partir del siguiente ciclo.
     *
     * @param milisegundos duración en ms; mayor o igual a 0 (0 = a máxima velocidad).
     * @throws IllegalArgumentException si es negativa.
     */
    public void setDuracionCicloMs(long milisegundos) {
        if (milisegundos < 0) {
            throw new IllegalArgumentException("La duración del ciclo no puede ser negativa.");
        }
        this.duracionCicloMs = milisegundos;
    }

    /** Duración de un ciclo, en milisegundos. */
    public long getDuracionCicloMs() {
        return duracionCicloMs;
    }

    /** Indica si el hilo de la simulación está vivo (no detenido ni terminado por el límite). */
    public boolean estaEnMarcha() {
        return enMarcha;
    }

    /** Indica si la simulación está en pausa. */
    public synchronized boolean estaPausada() {
        return pausada;
    }

    /**
     * Ejecuta {@code lector} con el estado de los computadores congelado: ningún ciclo
     * avanza, ni se carga ningún proceso, mientras dura. Es la forma correcta de que la
     * GUI (u otro hilo) recorra colas, PCB y memoria sin leer estructuras a medias.
     *
     * <p>El lector debe ser breve y NO debe llamar a {@link #cargarProceso(PCB)},
     * {@link #registrarComputador(Computador)}, {@link #ejecutar()} ni a este mismo
     * método (el semáforo no es reentrante y se bloquearía).</p>
     *
     * @param lector código de solo lectura a ejecutar.
     */
    public void leerEstado(Runnable lector) {
        if (lector == null) {
            throw new IllegalArgumentException("El lector no puede ser nulo.");
        }
        mutexEstado.acquireUninterruptibly();
        try {
            lector.run();
        } finally {
            mutexEstado.release();
        }
    }

    /** Indica si todavía queda trabajo por hacer en algún computador. */
    public boolean hayTrabajoPendiente() {
        Iterador<Computador> iterador = computadores.iterador();
        while (iterador.haySiguiente()) {
            if (!iterador.siguiente().sinTrabajoPendiente()) {
                return true;
            }
        }
        return false;
    }

    /** Indica si la simulación terminó normalmente (ya no queda trabajo). */
    public boolean isTerminada() {
        return !hayTrabajoPendiente();
    }

    public Computador buscarComputador(int id) {
        // 1. Buscar por ID de computador exacto
        Iterador<Computador> iterador = computadores.iterador();
        while (iterador.haySiguiente()) {
            Computador computador = iterador.siguiente();
            if (computador != null && computador.getId() == id) {
                return computador;
            }
        }
        // 2. Si no coincide con ningún ID, verificar si se pasó un índice numérico (base 0 o base 1)
        return computadores.obtenerSeguro(id);
    }

    /** Reloj global de la simulación. */
    public Reloj getReloj() {
        return reloj;
    }

    /** Número del ciclo actual (0 antes de la primera ejecución). */
    public long getCicloActual() {
        return reloj.getCicloActual();
    }

    /** Límite máximo de ciclos configurado. */
    public long getCiclosMaximos() {
        return ciclosMaximos;
    }

    /**
     * Indica si la última ejecución se detuvo por alcanzar el límite de ciclos
     * en lugar de terminar normalmente.
     */
    public boolean isLimiteAlcanzado() {
        return limiteAlcanzado;
    }

    /** Cantidad de computadores registrados. */
    public int getCantidadComputadores() {
        return computadores.getTamano();
    }

    /** Iterador sobre los computadores, para observarlos sin exponer la lista interna. */
    public Iterador<Computador> iteradorComputadores() {
        return computadores.iterador();
    }
}
