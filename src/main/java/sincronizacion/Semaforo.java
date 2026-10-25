package sincronizacion;

import enums.MotivoBloqueo;
import estructuras.ListaEnlazada;
import modelo.PCB;

/**
 * Semáforo simulado para ÁvilaOS.
 *
 * <p>No bloquea el hilo de Java. Cuando un proceso debe esperar se deja en la
 * cola del semáforo y el kernel lo pasa a BLOQUEADO; al hacer señal se
 * despierta al primero (FIFO).</p>
 *
 * <p>Mantiene un registro de permisos reservados para procesos despertados por senalizar(),
 * garantizando que tengan prioridad estricta para consumir su permiso frente a nuevos procesos
 * entrantes, manteniendo orden FIFO sin saltos de turno y eliminando cualquier inanición.</p>
 */
public class Semaforo {

    private final String id;
    private int valor;
    private final ListaEnlazada<PCB> colaEspera = new ListaEnlazada<>();
    private final ListaEnlazada<PCB> permisosReservados = new ListaEnlazada<>();

    public Semaforo(String id, int valor) {
        this.id = (id != null && !id.isEmpty()) ? id : "SEM";
        this.valor = valor;
    }

    public String getId() {
        return id;
    }

    public synchronized int getValor() {
        return valor;
    }

    /**
     * Intenta adquirir un permiso del semáforo.
     *
     * <p>Si el proceso fue previamente despertado por {@link #senalizar()}, tiene
     * prioridad estricta y consume su permiso reservado. Si es un nuevo proceso entrante,
     * solo puede adquirir si hay permisos libres no reservados. En caso contrario,
     * se encola en la cola de espera FIFO y retorna {@code false}.</p>
     *
     * @param proceso proceso que solicita el recurso.
     * @param motivo  motivo de bloqueo en caso de no poder adquirir.
     * @return true si adquirió el permiso; false si quedó bloqueado en espera.
     */
    public synchronized boolean esperar(PCB proceso, MotivoBloqueo motivo) {
        if (proceso == null) {
            throw new IllegalArgumentException("El proceso no puede ser nulo.");
        }
        // Caso 1: El proceso ya tenía un permiso reservado por haber sido despertado
        if (permisosReservados.contiene(proceso)) {
            permisosReservados.eliminar(proceso);
            valor--;
            return true;
        }

        // Caso 2: Proceso nuevo entrante; solo adquiere si hay permisos libres no reservados
        int permisosDisponibles = valor - permisosReservados.getTamano();
        if (permisosDisponibles > 0) {
            valor--;
            return true;
        }

        // Caso 3: Sin permisos libres; encolar en espera FIFO
        proceso.setMotivoBloqueo(motivo != null ? motivo : MotivoBloqueo.NINGUNO);
        colaEspera.agregar(proceso);
        return false;
    }

    /**
     * Libera un permiso del semáforo.
     *
     * <p>Incrementa el valor del semáforo. Si hay procesos en la cola de espera FIFO,
     * extrae al primero, registra su permiso en el registro de reservados y retorna el PCB
     * para que el kernel lo despierte.</p>
     *
     * @return el proceso despertado con permiso reservado, o {@code null} si nadie esperaba.
     */
    public synchronized PCB senalizar() {
        valor++;
        if (!colaEspera.estaVacia()) {
            PCB despertado = colaEspera.eliminarPrimero();
            permisosReservados.agregar(despertado);
            return despertado;
        }
        return null;
    }

    /**
     * Restaura un recurso sin despertar a procesos en espera.
     * Usado para deshacer una operación cuando un proceso muere antes de completar.
     */
    public synchronized void restaurar() {
        valor++;
    }

    /**
     * Remueve un proceso de la cola de espera o del registro de permisos reservados.
     * Usado cuando un proceso es terminado antes de que se despierte o ejecute
     * (por ejemplo por vencimiento de deadline).
     *
     * @param proceso proceso a remover.
     * @return true si el proceso estaba presente y fue removido.
     */
    public synchronized boolean remover(PCB proceso) {
        if (proceso == null) {
            return false;
        }
        boolean removidoCola = colaEspera.eliminar(proceso);
        boolean removidoReservados = permisosReservados.eliminar(proceso);
        return removidoCola || removidoReservados;
    }

    public synchronized int getCantidadEsperando() {
        return colaEspera.getTamano();
    }

    public synchronized boolean hayEsperando() {
        return !colaEspera.estaVacia();
    }

    public synchronized int getCantidadReservados() {
        return permisosReservados.getTamano();
    }
}
