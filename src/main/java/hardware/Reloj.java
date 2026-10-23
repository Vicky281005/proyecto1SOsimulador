package hardware;

import estructuras.Iterador;
import estructuras.ListaEnlazada;
import java.util.concurrent.Semaphore;

/**
 * Reloj global de ÁvilaOS.
 *
 * <p>Marca el ritmo de toda la simulación y mantiene sincronizados a los
 * componentes que reaccionan al reloj (computadores, CPU, DMA, etc.). En cada
 * llamada a {@link #avanzarCiclo()} el reloj avanza un ciclo y todos los
 * componentes ejecutan su {@code tick} con el mismo número de ciclo.</p>
 *
 * <h2>Modelo de concurrencia</h2>
 * <p>Cada componente registrado corre en su <b>propio hilo</b> de Java. El reloj
 * los sincroniza con una barrera hecha con semáforos:</p>
 * <ol>
 *   <li>El reloj incrementa el ciclo y hace {@code release()} del semáforo
 *       {@code inicio} de cada componente ("empieza el ciclo").</li>
 *   <li>Cada hilo ejecuta {@code tick(ciclo)} y hace {@code release()} del
 *       semáforo común {@code finCiclo} ("terminé").</li>
 *   <li>El reloj hace {@code finCiclo.acquire(n)}: no retorna hasta que los n
 *       componentes terminaron. Así ninguno puede adelantarse a los demás.</li>
 * </ol>
 * <p>Un tercer semáforo, binario ({@code mutex}), da exclusión mutua sobre la
 * lista de componentes: no se puede registrar ni quitar un componente mientras
 * hay un ciclo en curso.</p>
 *
 * <p>{@link #avanzarCiclo()} es síncrono: quien lo llama (el Simulador) espera a
 * que termine el ciclo, igual que antes de introducir los hilos. Si un
 * componente lanza una excepción, se relanza desde {@code avanzarCiclo()}.</p>
 *
 * <p>Los hilos son demonio: no impiden que la JVM termine. Llame a
 * {@link #detener()} para liberarlos explícitamente.</p>
 */
public class Reloj {

    /** Tiempo máximo (ms) que se espera a que termine cada hilo al detener el reloj. */
    private static final long ESPERA_CIERRE_MS = 2000;

    /**
     * Un componente registrado junto con su hilo y su señal de inicio de ciclo.
     */
    private final class Trabajador implements Runnable {
        private final ComponenteReloj componente;
        /** Vale 0 hasta que el reloj da la orden de empezar el ciclo. */
        private final Semaphore inicio = new Semaphore(0);
        private volatile boolean activo = true;
        /** Excepción lanzada por el componente en el último ciclo (null si no hubo). */
        private volatile RuntimeException error;
        private Thread hilo;

        private Trabajador(ComponenteReloj componente) {
            this.componente = componente;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    inicio.acquire();
                } catch (InterruptedException e) {
                    break;
                }
                if (!activo) {
                    break;
                }
                try {
                    componente.tick(cicloActual);
                } catch (RuntimeException e) {
                    error = e;
                } finally {
                    finCiclo.release();
                }
            }
        }

        /** Ordena al hilo terminar y espera a que lo haga. */
        private void cerrar() {
            activo = false;
            inicio.release();
            try {
                hilo.join(ESPERA_CIERRE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private volatile long cicloActual;
    private final ListaEnlazada<Trabajador> trabajadores;
    private boolean detenido;

    /** Señal compartida: cada componente hace release() al terminar su ciclo. */
    private final Semaphore finCiclo = new Semaphore(0);

    /** Mutex binario: protege la lista de trabajadores y serializa los ciclos. */
    private final Semaphore mutex = new Semaphore(1);

    public Reloj() {
        this.cicloActual = 0;
        this.trabajadores = new ListaEnlazada<>();
        this.detenido = false;
    }

    /**
     * Registra un componente para que sea notificado en cada ciclo. Se le crea
     * un hilo propio. Si ya estaba registrado, no se duplica.
     *
     * @param componente componente reactivo al reloj.
     * @throws IllegalArgumentException si el componente es nulo.
     * @throws IllegalStateException    si el reloj ya fue detenido.
     */
    public void registrar(ComponenteReloj componente) {
        if (componente == null) {
            throw new IllegalArgumentException("No se puede registrar un componente nulo.");
        }
        mutex.acquireUninterruptibly();
        try {
            verificarNoDetenido();
            if (buscar(componente) == null) {
                Trabajador t = new Trabajador(componente);
                t.hilo = new Thread(t, "Hilo-" + componente.getNombre());
                t.hilo.setDaemon(true);
                trabajadores.agregar(t);
                t.hilo.start();
            }
        } finally {
            mutex.release();
        }
    }

    /**
     * Quita un componente del reloj y termina su hilo.
     *
     * @param componente componente que deja de reaccionar al reloj.
     */
    public void desregistrar(ComponenteReloj componente) {
        mutex.acquireUninterruptibly();
        try {
            Trabajador t = buscar(componente);
            if (t != null) {
                trabajadores.eliminar(t);
                t.cerrar();
            }
        } finally {
            mutex.release();
        }
    }

    /**
     * Avanza un ciclo de reloj: todos los componentes registrados ejecutan su
     * {@code tick} en paralelo, cada uno en su hilo, y este método retorna cuando
     * todos terminaron. El primer ciclo de la simulación es el 1.
     *
     * @throws IllegalStateException si el reloj fue detenido.
     * @throws RuntimeException      la primera excepción lanzada por un componente, si hubo alguna.
     */
    public void avanzarCiclo() {
        mutex.acquireUninterruptibly();
        try {
            verificarNoDetenido();
            cicloActual++;
            int n = trabajadores.getTamano();

            // 1) Orden de inicio a todos los componentes.
            Iterador<Trabajador> it = trabajadores.iterador();
            while (it.haySiguiente()) {
                it.siguiente().inicio.release();
            }
            // 2) Barrera: esperar a que los n componentes terminen el ciclo.
            finCiclo.acquireUninterruptibly(n);

            // 3) Propagar el primer error ocurrido en algún componente.
            RuntimeException primero = null;
            it = trabajadores.iterador();
            while (it.haySiguiente()) {
                Trabajador t = it.siguiente();
                if (t.error != null && primero == null) {
                    primero = t.error;
                }
                t.error = null;
            }
            if (primero != null) {
                throw primero;
            }
        } finally {
            mutex.release();
        }
    }

    /**
     * Detiene el reloj: termina los hilos de todos los componentes y los quita.
     * Un reloj detenido no se puede reutilizar (cree uno nuevo). Es idempotente.
     * No debe llamarse desde dentro de un {@code tick}.
     */
    public void detener() {
        mutex.acquireUninterruptibly();
        try {
            if (detenido) {
                return;
            }
            detenido = true;
            Iterador<Trabajador> it = trabajadores.iterador();
            while (it.haySiguiente()) {
                it.siguiente().cerrar();
            }
            trabajadores.vaciar();
        } finally {
            mutex.release();
        }
    }

    /** Número del ciclo actual (0 antes del primer avance). */
    public long getCicloActual() {
        return cicloActual;
    }

    /** Cantidad de componentes registrados actualmente. */
    public int getCantidadComponentes() {
        return trabajadores.getTamano();
    }

    /** Reinicia el contador de ciclos a 0 conservando los componentes. */
    public void reiniciar() {
        mutex.acquireUninterruptibly();
        try {
            cicloActual = 0;
        } finally {
            mutex.release();
        }
    }

    private Trabajador buscar(ComponenteReloj componente) {
        Iterador<Trabajador> it = trabajadores.iterador();
        while (it.haySiguiente()) {
            Trabajador t = it.siguiente();
            if (t.componente == componente) {
                return t;
            }
        }
        return null;
    }

    private void verificarNoDetenido() {
        if (detenido) {
            throw new IllegalStateException("El reloj ya fue detenido.");
        }
    }
}
