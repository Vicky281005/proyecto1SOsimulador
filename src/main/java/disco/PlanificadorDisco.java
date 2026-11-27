package disco;

import enums.PoliticaDisco;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import java.util.concurrent.Semaphore;

/**
 * Planificador de solicitudes de Entrada/Salida del disco simulado (SD) de ÁvilaOS.
 *
 * <p>Administra la <b>cola única de E/S del disco</b> protegida con un
 * {@link Semaphore} binario justo y delega el criterio de selección a una
 * estrategia que implementa {@link PoliticaPlanificacionDisco} (Patrón Strategy).
 * Permite cambiar la política de disco en caliente sin perder solicitudes.</p>
 */
public class PlanificadorDisco {

    private final ListaEnlazada<SolicitudES> colaES;
    private final Semaphore mutexCola = new Semaphore(1, true);
    private PoliticaPlanificacionDisco politica;

    /** Crea un planificador de disco inicializado con la política FIFO. */
    public PlanificadorDisco() {
        this(PoliticaDisco.FIFO);
    }

    /**
     * Crea un planificador de disco con la política indicada.
     *
     * @param politicaInicial política inicial de planificación.
     */
    public PlanificadorDisco(PoliticaDisco politicaInicial) {
        this.colaES = new ListaEnlazada<>();
        this.politica = FabricaPoliticasDisco.crear(
                (politicaInicial != null) ? politicaInicial : PoliticaDisco.FIFO);
    }

    /**
     * Encola una nueva solicitud en la cola única de E/S del disco.
     *
     * @param solicitud solicitud de E/S a atender.
     */
    public void encolar(SolicitudES solicitud) {
        if (solicitud == null) {
            throw new IllegalArgumentException("La solicitud de E/S no puede ser nula.");
        }
        mutexCola.acquireUninterruptibly();
        try {
            colaES.agregar(solicitud);
        } finally {
            mutexCola.release();
        }
    }

    /**
     * Selecciona y extrae de la cola única la siguiente solicitud a atender
     * utilizando la política de planificación activa.
     *
     * @param posicionCabezal     posición actual del cabezal del disco.
     * @param direccionAscendente dirección actual de desplazamiento del cabezal.
     * @param totalBloques        límite de bloques del barrido del disco.
     * @return la solicitud elegida, o {@code null} si la cola está vacía.
     */
    public SolicitudES seleccionarSiguiente(int posicionCabezal,
                                            boolean direccionAscendente,
                                            int totalBloques) {
        mutexCola.acquireUninterruptibly();
        try {
            if (colaES.estaVacia()) {
                return null;
            }
            return politica.seleccionar(colaES, posicionCabezal, direccionAscendente, totalBloques);
        } finally {
            mutexCola.release();
        }
    }

    /**
     * Cambia la política de planificación en caliente sin perder ninguna solicitud en espera.
     *
     * @param nuevaPolitica identificador de la nueva política.
     */
    public void setPolitica(PoliticaDisco nuevaPolitica) {
        if (nuevaPolitica == null) {
            throw new IllegalArgumentException("La política de disco no puede ser nula.");
        }
        mutexCola.acquireUninterruptibly();
        try {
            this.politica = FabricaPoliticasDisco.crear(nuevaPolitica);
        } finally {
            mutexCola.release();
        }
    }

    /**
     * Establece directamente una estrategia personalizada que implemente
     * {@link PoliticaPlanificacionDisco}.
     *
     * @param estrategia estrategia de planificación.
     */
    public void setEstrategia(PoliticaPlanificacionDisco estrategia) {
        if (estrategia == null) {
            throw new IllegalArgumentException("La estrategia de disco no puede ser nula.");
        }
        mutexCola.acquireUninterruptibly();
        try {
            this.politica = estrategia;
        } finally {
            mutexCola.release();
        }
    }

    public PoliticaDisco getPoliticaActual() {
        mutexCola.acquireUninterruptibly();
        try {
            return politica.getTipo();
        } finally {
            mutexCola.release();
        }
    }

    public PoliticaPlanificacionDisco getEstrategia() {
        mutexCola.acquireUninterruptibly();
        try {
            return politica;
        } finally {
            mutexCola.release();
        }
    }

    public boolean estaVacia() {
        mutexCola.acquireUninterruptibly();
        try {
            return colaES.estaVacia();
        } finally {
            mutexCola.release();
        }
    }

    public int getCantidadPendientes() {
        mutexCola.acquireUninterruptibly();
        try {
            return colaES.getTamano();
        } finally {
            mutexCola.release();
        }
    }

    /**
     * Vacía completamente la cola única de solicitudes de E/S pendientes.
     */
    public void vaciar() {
        mutexCola.acquireUninterruptibly();
        try {
            colaES.vaciar();
        } finally {
            mutexCola.release();
        }
    }

    /**
     * Devuelve un iterador seguro (snapshot) sobre las solicitudes actualmente en espera.
     */
    public Iterador<SolicitudES> iteradorCola() {
        ListaEnlazada<SolicitudES> copia = new ListaEnlazada<>();
        mutexCola.acquireUninterruptibly();
        try {
            Iterador<SolicitudES> it = colaES.iterador();
            while (it.haySiguiente()) {
                copia.agregar(it.siguiente());
            }
        } finally {
            mutexCola.release();
        }
        return copia.iterador();
    }
}
