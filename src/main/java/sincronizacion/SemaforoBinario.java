package sincronizacion;

import enums.MotivoBloqueo;
import modelo.PCB;

/**
 * Semáforo binario / Mutex simulado.
 */
public class SemaforoBinario {

    private final Semaforo semaforo;

    public SemaforoBinario(String id) {
        this.semaforo = new Semaforo(id, 1);
    }

    public SemaforoBinario(String id, boolean libre) {
        this.semaforo = new Semaforo(id, libre ? 1 : 0);
    }

    public String getId() {
        return semaforo.getId();
    }

    public int getValor() {
        return semaforo.getValor();
    }

    public boolean esperar(PCB proceso) {
        return semaforo.esperar(proceso, MotivoBloqueo.MUTEX);
    }

    public PCB senalizar() {
        return semaforo.senalizar();
    }

    public Semaforo getSemaforo() {
        return semaforo;
    }
}
