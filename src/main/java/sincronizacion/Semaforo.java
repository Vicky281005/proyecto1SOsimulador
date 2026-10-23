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
 */
public class Semaforo {

    private final String id;
    private int valor;
    private final ListaEnlazada<PCB> colaEspera = new ListaEnlazada<>();

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
     * Intenta adquirir. Si valor > 0 decrementa y retorna true.
     * Si valor == 0 encola el proceso y retorna false.
     */
    public synchronized boolean esperar(PCB proceso, MotivoBloqueo motivo) {
        if (proceso == null) {
            throw new IllegalArgumentException("El proceso no puede ser nulo.");
        }
        if (valor > 0) {
            valor--;
            return true;
        }
        proceso.setMotivoBloqueo(motivo != null ? motivo : MotivoBloqueo.NINGUNO);
        colaEspera.agregar(proceso);
        return false;
    }

    /**
     * Libera. Si hay procesos en espera despierta al primero, sino incrementa.
     */
    public synchronized PCB senalizar() {
        if (!colaEspera.estaVacia()) {
            return colaEspera.eliminarPrimero();
        }
        valor++;
        return null;
    }

    public synchronized int getCantidadEsperando() {
        return colaEspera.getTamano();
    }

    public synchronized boolean hayEsperando() {
        return !colaEspera.estaVacia();
    }
}
