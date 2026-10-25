package sincronizacion;

import enums.MotivoBloqueo;
import estructuras.ListaEnlazada;
import modelo.PCB;
import nucleo.Computador;

/**
 * Buffer acotado para el problema productor-consumidor.
 *
 * <p>Reside en la RAM del computador anfitrión. Usa los semáforos simulados
 * {@code vacios}, {@code llenos} y {@code mutex}. Cada acceso se sincroniza
 * mediante el algoritmo del anexo.</p>
 */
public class Buffer {

    private final String id;
    private final int capacidad;
    private final int idComputadorAnfitrion;
    private final ListaEnlazada<Object> elementos = new ListaEnlazada<>();

    private final Semaforo vacios;
    private final Semaforo llenos;
    private final SemaforoBinario mutex;
    private boolean registradoEnLog;

    /**
     * Crea un buffer y reserva su memoria en el computador anfitrión.
     *
     * @param id                        identificador del buffer.
     * @param capacidad                 número máximo de elementos.
     * @param idComputadorAnfitrion     id del computador donde reside.
     * @param tamanoElementoBytes       tamaño aproximado por elemento (para reservar RAM).
     * @param anfitrion                 computador anfitrión.
     */
    public Buffer(String id, int capacidad, int idComputadorAnfitrion, long tamanoElementoBytes, Computador anfitrion) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("El id del buffer no puede estar vacío.");
        }
        if (capacidad <= 0) {
            throw new IllegalArgumentException("La capacidad del buffer debe ser mayor que cero.");
        }
        if (anfitrion == null) {
            throw new IllegalArgumentException("El computador anfitrión no puede ser nulo.");
        }

        this.id = id.trim();
        this.capacidad = capacidad;
        this.idComputadorAnfitrion = idComputadorAnfitrion;

        // Semáforos según anexo
        this.vacios = new Semaforo("VACIOS_" + this.id, capacidad);
        this.llenos = new Semaforo("LLENOS_" + this.id, 0);
        this.mutex = new SemaforoBinario("MUTEX_" + this.id, true);

        // Reservar memoria en el anfitrión
        long memoriaAReservar = capacidad * Math.max(1, tamanoElementoBytes);
        if (!anfitrion.reservarMemoria(memoriaAReservar)) {
            throw new IllegalStateException("No hay memoria suficiente en " + anfitrion.getNombre() + " para crear el buffer " + this.id);
        }

        if (anfitrion.getSimulador() != null && anfitrion.getSimulador().getLogEventos() != null) {
            registrarEnLogSiNecesario(anfitrion.getSimulador().getLogEventos());
        }
    }

    /**
     * Registra el evento de creación del buffer en el log si aún no ha sido emitido.
     * Formato: "[ciclo X] BUFFER - Creado buffer [ID] con capacidad N (vacíos=N, llenos=0, mutex=1)"
     *
     * @param log gestor de eventos de la simulación.
     */
    public synchronized void registrarEnLogSiNecesario(simulacion.LogEventos log) {
        if (!registradoEnLog && log != null) {
            registradoEnLog = true;
            log.registrar(simulacion.LogEventos.TipoEvento.BUFFER,
                    "Creado buffer " + this.id + " con capacidad " + capacidad
                            + " (vacíos=" + capacidad + ", llenos=0, mutex=1)");
        }
    }

    public String getId() {
        return id;
    }

    public int getCapacidad() {
        return capacidad;
    }

    public int getIdComputadorAnfitrion() {
        return idComputadorAnfitrion;
    }

    public int getIdComputadorHost() {
        return idComputadorAnfitrion;
    }

    public int getCantidadElementos() {
        return elementos.getTamano();
    }

    public Semaforo getVacios() {
        return vacios;
    }

    public Semaforo getLlenos() {
        return llenos;
    }

    public SemaforoBinario getMutex() {
        return mutex;
    }

    public int getValorVacios() {
        return vacios.getValor();
    }

    public int getValorLlenos() {
        return llenos.getValor();
    }

    public int getValorMutex() {
        return mutex.getValor();
    }

    public int getCantidadEsperandoVacios() {
        return vacios.getCantidadEsperando();
    }

    public int getCantidadEsperandoLlenos() {
        return llenos.getCantidadEsperando();
    }

    public synchronized boolean insertar(Object elemento) {
        elementos.agregar(elemento);
        return true;
    }

    public synchronized Object extraer() {
        if (elementos.estaVacia()) {
            return null;
        }
        Object primero = elementos.obtener(0);
        elementos.eliminarEn(0);
        return primero;
    }

    public String getResumen() {
        return "Buffer " + id + " [" + elementos.getTamano() + "/" + capacidad + "] vacios=" + vacios.getValor()
                + " llenos=" + llenos.getValor() + " mutex=" + mutex.getValor();
    }
}
