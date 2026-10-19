package simulacion;

import estructuras.Iterador;
import estructuras.ListaEnlazada;
import hardware.Reloj;
import modelo.PCB;
import nucleo.Computador;

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
 */
public class Simulador {

    /** Límite de ciclos usado cuando no se especifica uno. */
    public static final long CICLOS_MAXIMOS_POR_DEFECTO = 100_000L;

    private final Reloj reloj;
    private final ListaEnlazada<Computador> computadores;
    private final long ciclosMaximos;

    private boolean limiteAlcanzado;

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
        if (computadores.contiene(computador)) {
            return; // ya estaba registrado, no se duplica
        }
        computadores.agregar(computador);
        reloj.registrar(computador);
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
        Computador destino = buscarComputador(idComputador);
        if (destino == null) {
            throw new IllegalArgumentException(
                    "No existe un computador con id " + idComputador + " para el proceso '"
                            + proceso.getNombre() + "'.");
        }
        destino.recibirProceso(proceso);
    }

    /**
     * Ejecuta la simulación: avanza el reloj ciclo a ciclo mientras quede
     * trabajo pendiente y no se haya superado el límite de seguridad.
     *
     * @return el total de ciclos transcurridos desde el inicio de la simulación.
     */
    public long ejecutar() {
        limiteAlcanzado = false;
        while (hayTrabajoPendiente() && reloj.getCicloActual() < ciclosMaximos) {
            reloj.avanzarCiclo();
        }
        if (hayTrabajoPendiente()) {
            // Se salió del bucle por el límite, no porque la simulación terminara.
            limiteAlcanzado = true;
        }
        return reloj.getCicloActual();
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

    private Computador buscarComputador(int id) {
        Iterador<Computador> iterador = computadores.iterador();
        while (iterador.haySiguiente()) {
            Computador computador = iterador.siguiente();
            if (computador.getId() == id) {
                return computador;
            }
        }
        return null;
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
