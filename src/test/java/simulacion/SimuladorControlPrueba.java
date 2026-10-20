package simulacion;

import enums.PoliticaPlanificacion;
import enums.TipoInstruccion;
import enums.TipoProceso;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import modelo.Instruccion;
import modelo.PCB;
import nucleo.Computador;
import planificacion.FabricaPoliticas;

/**
 * Pruebas del control de la simulación (issue #31): ejecución en hilo propio, pausa,
 * reanudar, detener, duración de ciclo en caliente, carga de procesos en marcha y
 * lectura del estado desde otro hilo. Se ejecutan como programa normal y terminan con
 * código 1 si algo falla. No requieren JUnit.
 */
public class SimuladorControlPrueba {

    private static int fallos = 0;

    private static void comprobar(boolean condicion, String mensaje) {
        if (condicion) {
            System.out.println("  OK    " + mensaje);
        } else {
            fallos++;
            System.out.println("  FALLO " + mensaje);
        }
    }

    private static PCB proceso(String nombre, int prioridad, int instrucciones, long memoria,
                               long deadlineRelativo, int computador) {
        ListaEnlazada<Instruccion> programa = new ListaEnlazada<>();
        for (int i = 0; i < instrucciones; i++) {
            programa.agregar(new Instruccion(TipoInstruccion.CPU));
        }
        return new PCB(nombre, computador, TipoProceso.CPU_BOUND, prioridad, memoria,
                deadlineRelativo, "", 0, 0, programa, 0);
    }

    /** Escenario de la demo: 3 computadores, 8 procesos. */
    private static Simulador escenarioDemo() {
        Simulador sim = new Simulador();
        Computador pc1 = new Computador(1, "PC-1", 1024, FabricaPoliticas.crear(PoliticaPlanificacion.FCFS));
        Computador pc2 = new Computador(2, "PC-2", 1024, FabricaPoliticas.crear(PoliticaPlanificacion.EDF));
        Computador pc3 = new Computador(3, "PC-3", 1024, FabricaPoliticas.crear(PoliticaPlanificacion.PRIORIDAD));
        sim.registrarComputador(pc1);
        sim.registrarComputador(pc2);
        sim.registrarComputador(pc3);
        sim.cargarProceso(proceso("P1", 1, 3, 200, 40, 1));
        sim.cargarProceso(proceso("P2", 1, 5, 200, 20, 1));
        sim.cargarProceso(proceso("P3", 1, 2, 200, 30, 1));
        sim.cargarProceso(proceso("P4", 1, 4, 300, 50, 2));
        sim.cargarProceso(proceso("P5", 1, 3, 300, 10, 2));
        sim.cargarProceso(proceso("P6", 3, 2, 200, 40, 3));
        sim.cargarProceso(proceso("P7", 1, 4, 200, 30, 3));
        sim.cargarProceso(proceso("P8", 2, 3, 200, 30, 3));
        return sim;
    }

    /** Simulador con 1 computador FCFS (RAM 1024) sin procesos. */
    private static Simulador simulador1pc() {
        Simulador sim = new Simulador();
        sim.registrarComputador(new Computador(1, "PC-1", 1024, FabricaPoliticas.crear(PoliticaPlanificacion.FCFS)));
        return sim;
    }

    private static boolean esperar(BooleanSupplier condicion, long maxMs) throws InterruptedException {
        long limite = System.currentTimeMillis() + maxMs;
        while (!condicion.getAsBoolean()) {
            if (System.currentTimeMillis() > limite) {
                return false;
            }
            Thread.sleep(2);
        }
        return true;
    }

    private static boolean terminada(Simulador sim) {
        AtomicInteger r = new AtomicInteger(0);
        sim.leerEstado(() -> r.set(sim.isTerminada() ? 1 : 0));
        return r.get() == 1;
    }

    private static String reporte(Simulador sim) {
        AtomicReference<String> r = new AtomicReference<>();
        sim.leerEstado(() -> r.set(new ReporteSimulacion(sim).generar()));
        return r.get();
    }

    private static long hilosVivos(String prefijo) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && t.getName().startsWith(prefijo)).count();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("1) iniciar() en hilo propio da el mismo resultado que ejecutar()");
        Simulador a = escenarioDemo();
        a.ejecutar();
        String esperado = reporte(a);
        a.detener();
        PCB.reiniciarGeneradorId();
        Simulador b = escenarioDemo();
        b.setDuracionCicloMs(0);
        b.iniciar();
        comprobar(esperar(() -> terminada(b), 10_000), "la simulación en hilo propio termina");
        comprobar(esperado.equals(reporte(b)), "el reporte es idéntico al de ejecutar() síncrono");
        comprobar(b.getCicloActual() == 10, "10 ciclos en total (ciclo " + b.getCicloActual() + ")");
        b.detener();

        System.out.println("2) pausar congela el reloj y reanudar lo continúa");
        Simulador c = simulador1pc();
        c.setDuracionCicloMs(1);
        c.cargarProceso(proceso("Largo", 1, 3000, 100, 100000, 1));
        c.iniciar();
        comprobar(esperar(() -> c.getCicloActual() >= 30, 10_000), "el reloj avanza");
        c.pausar();
        Thread.sleep(150); // deja terminar el ciclo en curso
        long congelado = c.getCicloActual();
        Thread.sleep(250);
        comprobar(c.getCicloActual() == congelado, "en pausa el ciclo no cambia (" + congelado + ")");
        comprobar(c.estaPausada() && c.estaEnMarcha(), "estaPausada() y estaEnMarcha() son true");
        c.reanudar();
        comprobar(esperar(() -> c.getCicloActual() >= congelado + 30, 10_000), "tras reanudar vuelve a avanzar");

        System.out.println("3) la duración del ciclo cambia en caliente");
        c.setDuracionCicloMs(40);
        Thread.sleep(80);
        long antes = c.getCicloActual();
        Thread.sleep(400);
        long lento = c.getCicloActual() - antes;
        c.setDuracionCicloMs(1);
        Thread.sleep(80);
        antes = c.getCicloActual();
        Thread.sleep(400);
        long rapido = c.getCicloActual() - antes;
        comprobar(lento <= 14, "con 40 ms por ciclo avanzó poco en 400 ms (" + lento + " ciclos)");
        comprobar(rapido > lento * 3, "con 1 ms avanzó mucho más (" + rapido + " ciclos)");
        c.detener();

        System.out.println("4) sin trabajo el reloj no avanza; al cargar un proceso en marcha, se ejecuta");
        Simulador d = simulador1pc();
        d.setDuracionCicloMs(0);
        d.iniciar();
        Thread.sleep(300);
        comprobar(d.getCicloActual() == 0 && d.estaEnMarcha(), "sin procesos el ciclo se queda en 0");
        d.cargarProceso(proceso("A", 1, 5, 100, 1000, 1));
        comprobar(esperar(() -> terminada(d) && d.getCicloActual() == 5, 5000), "el proceso A (5 instrucciones) termina en el ciclo 5");
        d.cargarProceso(proceso("B", 1, 3, 100, 1000, 1));
        comprobar(esperar(() -> terminada(d) && d.getCicloActual() == 8, 5000), "un segundo proceso cargado después termina en el ciclo 8");
        d.detener();

        System.out.println("5) estrés: una hebra lee el estado mientras otra carga procesos");
        Simulador e = new Simulador();
        e.registrarComputador(new Computador(1, "PC-1", 4096, FabricaPoliticas.crear(PoliticaPlanificacion.FCFS)));
        e.registrarComputador(new Computador(2, "PC-2", 4096, FabricaPoliticas.crear(PoliticaPlanificacion.EDF)));
        e.setDuracionCicloMs(0);
        e.iniciar();
        AtomicInteger errores = new AtomicInteger(0);
        AtomicInteger lecturas = new AtomicInteger(0);
        Thread lector = new Thread(() -> {
            long fin = System.currentTimeMillis() + 2500;
            while (System.currentTimeMillis() < fin) {
                try {
                    e.leerEstado(() -> {
                        Iterador<Computador> it = e.iteradorComputadores();
                        while (it.haySiguiente()) {
                            Computador pc = it.siguiente();
                            Iterador<PCB> n = pc.iteradorNuevos();
                            while (n.haySiguiente()) {
                                n.siguiente().getNombre();
                            }
                            Iterador<PCB> l = pc.getPlanificador().iteradorListos();
                            while (l.haySiguiente()) {
                                l.siguiente().getEstado();
                            }
                            Iterador<PCB> t = pc.iteradorTerminados();
                            while (t.haySiguiente()) {
                                t.siguiente().getTiempoRestante();
                            }
                            pc.getMemoriaDisponible();
                            pc.getCpu().getProcesoActual();
                        }
                    });
                    lecturas.incrementAndGet();
                } catch (RuntimeException ex) {
                    errores.incrementAndGet();
                }
            }
        }, "Lector-prueba");
        lector.start();
        // Dos hebras cargan procesos sin pausa mientras la simulación los admite y los ejecuta
        // y el lector recorre las colas: las tres chocan si no hay exclusión mutua.
        final int porHebra = 1500;
        Thread[] cargadores = new Thread[2];
        for (int k = 0; k < cargadores.length; k++) {
            final int base = k;
            cargadores[k] = new Thread(() -> {
                for (int i = 0; i < porHebra; i++) {
                    e.cargarProceso(proceso("X" + base + "-" + i, 1 + i % 3, 1, 1, 1000000, 1 + i % 2));
                }
            }, "Cargador-" + k);
            cargadores[k].start();
        }
        for (Thread t : cargadores) {
            t.join();
        }
        lector.join();
        final int total = porHebra * cargadores.length;
        comprobar(esperar(() -> terminada(e), 20_000), "la simulación termina con los " + total + " procesos");
        AtomicInteger terminados = new AtomicInteger(0);
        e.leerEstado(() -> {
            Iterador<Computador> it = e.iteradorComputadores();
            while (it.haySiguiente()) {
                terminados.addAndGet(it.siguiente().getCantidadTerminados());
            }
        });
        comprobar(terminados.get() == total, "ningún proceso se perdió (" + terminados.get() + " de " + total + " terminados)");
        comprobar(errores.get() == 0 && lecturas.get() > 50,
                "el lector hizo " + lecturas.get() + " lecturas sin errores (errores=" + errores.get() + ")");
        e.detener();

        System.out.println("6) el límite de seguridad detiene el hilo");
        Simulador f = new Simulador(30);
        f.registrarComputador(new Computador(1, "PC-1", 1024, FabricaPoliticas.crear(PoliticaPlanificacion.FCFS)));
        f.cargarProceso(proceso("Infinito", 1, 100, 100, 100000, 1));
        f.setDuracionCicloMs(0);
        f.iniciar();
        comprobar(esperar(() -> !f.estaEnMarcha(), 5000), "el hilo se detiene solo");
        comprobar(f.isLimiteAlcanzado() && f.getCicloActual() == 30,
                "límite alcanzado en el ciclo " + f.getCicloActual());
        f.detener();

        System.out.println("7) ejecutar() síncrono con límite sigue marcando limiteAlcanzado");
        Simulador g = new Simulador(10);
        g.registrarComputador(new Computador(1, "PC-1", 1024, FabricaPoliticas.crear(PoliticaPlanificacion.FCFS)));
        g.cargarProceso(proceso("Largo", 1, 50, 100, 100000, 1));
        g.ejecutar();
        comprobar(g.isLimiteAlcanzado() && g.getCicloActual() == 10, "ejecutar() se detuvo en 10 ciclos con el límite activado");
        g.detener();

        System.out.println("8) detener libera los hilos; validaciones de uso");
        comprobar(hilosVivos("Simulador") == 0 && hilosVivos("Hilo-") == 0, "no quedan hilos del simulador ni del reloj vivos");
        Simulador h = simulador1pc();
        h.iniciar();
        boolean lanzo = false;
        try {
            h.iniciar();
        } catch (IllegalStateException ex) {
            lanzo = true;
        }
        comprobar(lanzo, "iniciar() dos veces lanza IllegalStateException");
        lanzo = false;
        try {
            h.ejecutar();
        } catch (IllegalStateException ex) {
            lanzo = true;
        }
        comprobar(lanzo, "ejecutar() con el hilo en marcha lanza IllegalStateException");
        lanzo = false;
        try {
            h.setDuracionCicloMs(-1);
        } catch (IllegalArgumentException ex) {
            lanzo = true;
        }
        comprobar(lanzo, "duración negativa lanza IllegalArgumentException");
        h.pausar();
        h.detener(); // detener estando en pausa no debe colgarse
        h.detener(); // idempotente
        comprobar(!h.estaEnMarcha() && hilosVivos("Simulador") == 0 && hilosVivos("Hilo-") == 0,
                "detener en pausa funciona y no deja hilos vivos");

        System.out.println();
        System.out.println(fallos == 0 ? "TODAS LAS PRUEBAS PASARON" : "FALLARON " + fallos + " COMPROBACIONES");
        System.exit(fallos == 0 ? 0 : 1);
    }
}
