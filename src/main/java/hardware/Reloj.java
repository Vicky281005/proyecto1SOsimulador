package hardware;

import estructuras.Iterador;
import estructuras.ListaEnlazada;

/**
 * Reloj global de ÁvilaOS.
 *
 * <p>Marca el ritmo de toda la simulación y mantiene sincronizados a los
 * componentes que reaccionan al reloj (CPU, DMA, etc.). En cada llamada a
 * {@link #avanzarCiclo()} el reloj avanza un ciclo y notifica, en orden de
 * registro, a todos los componentes, de modo que todos observen el mismo
 * número de ciclo.</p>
 */
public class Reloj {

    private long cicloActual;
    private final ListaEnlazada<ComponenteReloj> componentes;

    public Reloj() {
        this.cicloActual = 0;
        this.componentes = new ListaEnlazada<>();
    }

    /**
     * Registra un componente para que sea notificado en cada ciclo.
     * Si ya estaba registrado, no se duplica.
     *
     * @param componente componente reactivo al reloj.
     */
    public void registrar(ComponenteReloj componente) {
        if (componente == null) {
            throw new IllegalArgumentException("No se puede registrar un componente nulo.");
        }
        if (!componentes.contiene(componente)) {
            componentes.agregar(componente);
        }
    }

    /**
     * Quita un componente del reloj.
     *
     * @param componente componente que deja de reaccionar al reloj.
     */
    public void desregistrar(ComponenteReloj componente) {
        componentes.eliminar(componente);
    }

    /**
     * Avanza un ciclo de reloj y notifica a todos los componentes registrados.
     * El primer ciclo de la simulación es el 1.
     */
    public void avanzarCiclo() {
        cicloActual++;
        Iterador<ComponenteReloj> iterador = componentes.iterador();
        while (iterador.haySiguiente()) {
            iterador.siguiente().tick(cicloActual);
        }
    }

    /** Número del ciclo actual (0 antes del primer avance). */
    public long getCicloActual() {
        return cicloActual;
    }

    /** Cantidad de componentes registrados actualmente. */
    public int getCantidadComponentes() {
        return componentes.getTamano();
    }

    /** Reinicia el contador de ciclos a 0 conservando los componentes. */
    public void reiniciar() {
        cicloActual = 0;
    }
}
