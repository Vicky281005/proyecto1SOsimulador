package nucleo;

import disco.DiscoSimulado;
import enums.EstadoProceso;
import enums.TipoInstruccion;
import enums.TipoProceso;
import estructuras.ListaEnlazada;
import modelo.Instruccion;
import modelo.PCB;
import planificacion.FCFS;
import simulacion.Simulador;

/**
 * Prueba del planificador a mediano plazo: suspensión al swap, reactivación, criterio
 * de víctima, sin swap y deadline vencido estando suspendido (sin devolver RAM de más).
 */
public class SuspensionSwapPrueba {

    private static int ok = 0;
    private static int fallos = 0;
    private static boolean verbose = true;

    private static void asegurar(boolean cond, String msg) {
        if (cond) { ok++; if (verbose) System.out.println("  OK    " + msg); }
        else { fallos++; System.out.println("  FALLA " + msg); }
    }

    private static Simulador sim;
    private static Computador pc;
    private static DiscoSimulado disco;

    private static void armar(int ram, int swap) {
        sim = new Simulador();
        pc = new Computador(1, "PC-1", ram, new FCFS());
        sim.registrarComputador(pc);
        disco = new DiscoSimulado("Disco", 200, 50, swap);
        sim.setDisco(disco);
        sim.setServicioArchivos(new ProcesoESPrueba.ServicioFalso(disco));
    }

    /** Programa: una instrucción CPU, una E/S de temporizador larga y una CPU final. */
    private static ListaEnlazada<Instruccion> conEspera(int ciclosES) {
        ListaEnlazada<Instruccion> prog = new ListaEnlazada<>();
        prog.agregar(new Instruccion(TipoInstruccion.CPU));
        prog.agregar(new Instruccion(TipoInstruccion.READ_IO, String.valueOf(ciclosES)));
        prog.agregar(new Instruccion(TipoInstruccion.CPU));
        return prog;
    }

    private static ListaEnlazada<Instruccion> soloCpu(int n) {
        ListaEnlazada<Instruccion> prog = new ListaEnlazada<>();
        for (int i = 0; i < n; i++) prog.agregar(new Instruccion(TipoInstruccion.CPU));
        return prog;
    }

    private static PCB crear(String nombre, int prio, int mem, long deadline, ListaEnlazada<Instruccion> prog) {
        PCB p = new PCB(nombre, 1, TipoProceso.IO_BOUND, prio, mem, deadline, "", 0, 0, prog, sim.getCicloActual());
        pc.recibirProceso(p);
        return p;
    }

    private static void ciclo() { sim.getReloj().avanzarCiclo(); }

    private static void cerrar() { sim.getReloj().detener(); }

    private static void escenarioCompleto() {
        if (verbose) System.out.println("-- Suspender al swap para admitir un Nuevo y reactivar al liberarse la RAM");
        armar(10, 20);
        PCB a = crear("A", 5, 6, 100000, conEspera(30));
        PCB n = crear("N", 1, 8, 100000, soloCpu(3));
        boolean vioSusp = false, vioListoSusp = false, nAdmitidoConASuspendido = false;
        int ordenSusp = -1, ordenListoSusp = -1, paso = 0;
        for (int c = 0; c < 500 && !(a.getEstado() == EstadoProceso.TERMINADO && n.getEstado() == EstadoProceso.TERMINADO); c++) {
            ciclo();
            if (a.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO) {
                if (!vioSusp) { vioSusp = true; ordenSusp = paso++; }
                if (n.getEstado() != EstadoProceso.NUEVO) nAdmitidoConASuspendido = true;
            }
            if (a.getEstado() == EstadoProceso.LISTO_SUSPENDIDO && !vioListoSusp) { vioListoSusp = true; ordenListoSusp = paso++; }
        }
        asegurar(a.getEstado() == EstadoProceso.TERMINADO && n.getEstado() == EstadoProceso.TERMINADO, "ambos procesos terminan");
        asegurar(vioSusp, "A pasó a Bloqueado/Suspendido");
        asegurar(nAdmitidoConASuspendido, "N (que no cabía) fue admitido mientras A estaba en swap");
        asegurar(vioListoSusp && ordenListoSusp > ordenSusp, "luego A pasó a Listo/Suspendido");
        asegurar(pc.getTotalSuspensiones() == 1, "1 suspensión contada");
        asegurar(pc.getTotalReactivaciones() == 1, "1 reactivación contada");
        asegurar(disco.getBloquesLibresSwap() == 20, "el swap quedó libre al reactivar");
        asegurar(pc.getMemoriaDisponible() == pc.getMemoriaTotal(), "la RAM quedó completa");
        asegurar(disco.getTotalSolicitudesAtendidas() == 2, "el disco atendió escritura y lectura del swap");
        asegurar(n.getCicloFin() < a.getCicloFin(), "N terminó antes de que A volviera");
        cerrar();
    }

    private static void escenarioSinSwap() {
        if (verbose) System.out.println("-- Sin área swap: nadie se suspende, el Nuevo espera");
        armar(10, 0);
        PCB a = crear("A", 5, 6, 100000, conEspera(20));
        PCB n = crear("N", 1, 8, 100000, soloCpu(3));
        boolean alguienSuspendido = false;
        for (int c = 0; c < 500 && !(a.getEstado() == EstadoProceso.TERMINADO && n.getEstado() == EstadoProceso.TERMINADO); c++) {
            ciclo();
            if (a.getEstado().estaSuspendido()) alguienSuspendido = true;
        }
        asegurar(!alguienSuspendido && pc.getTotalSuspensiones() == 0, "no hubo suspensiones");
        asegurar(a.getEstado() == EstadoProceso.TERMINADO && n.getEstado() == EstadoProceso.TERMINADO, "ambos terminan igual");
        cerrar();
    }

    private static void escenarioVictima() {
        if (verbose) System.out.println("-- Víctima: el bloqueado de menor prioridad");
        armar(10, 20);
        PCB b1 = crear("B1", 2, 4, 100000, conEspera(80));
        PCB b2 = crear("B2", 5, 4, 100000, conEspera(80));
        for (int c = 0; c < 50 && !(b1.getEstado() == EstadoProceso.BLOQUEADO && b2.getEstado() == EstadoProceso.BLOQUEADO); c++) ciclo();
        asegurar(b1.getEstado() == EstadoProceso.BLOQUEADO && b2.getEstado() == EstadoProceso.BLOQUEADO, "B1 y B2 están bloqueados");
        PCB n = crear("N", 1, 6, 100000, soloCpu(1));
        boolean b1Susp = false, b2Susp = false;
        for (int c = 0; c < 20; c++) {
            ciclo();
            if (b1.getEstado().estaSuspendido()) b1Susp = true;
            if (b2.getEstado().estaSuspendido()) b2Susp = true;
        }
        asegurar(b2Susp && !b1Susp, "se suspendió B2 (prioridad 5) y no B1 (prioridad 2)");
        asegurar(pc.getTotalSuspensiones() == 1, "con una sola suspensión alcanzó");
        asegurar(n.getEstado() != EstadoProceso.NUEVO, "N fue admitido");
        cerrar();
    }

    private static void escenarioDeadlineSuspendido() {
        if (verbose) System.out.println("-- Deadline vencido estando suspendido: sin devolver RAM de más");
        armar(10, 20);
        PCB a = crear("A", 5, 6, 40, conEspera(200));
        PCB n = crear("N", 1, 8, 100000, soloCpu(120));
        boolean vioSusp = false;
        for (int c = 0; c < 200 && a.getEstado() != EstadoProceso.TERMINADO; c++) {
            ciclo();
            if (a.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO) vioSusp = true;
        }
        asegurar(vioSusp, "A llegó a estar suspendido");
        asegurar(a.getEstado() == EstadoProceso.TERMINADO && !a.isDeadlineCumplido(), "A terminó por deadline vencido");
        asegurar(pc.getMemoriaDisponible() == 2, "la RAM disponible es exactamente 2 (N usa 8): no se devolvió la de A");
        asegurar(disco.getBloquesLibresSwap() == 20, "el swap de A se liberó");
        asegurar(pc.getCantidadBloqueadosSuspendidos() == 0 && pc.getCantidadListosSuspendidos() == 0, "no quedan suspendidos");
        for (int c = 0; c < 400 && n.getEstado() != EstadoProceso.TERMINADO; c++) ciclo();
        asegurar(n.getEstado() == EstadoProceso.TERMINADO && pc.getMemoriaDisponible() == 10, "N termina y la RAM queda completa");
        cerrar();
    }

    public static void main(String[] args) {
        escenarioCompleto();
        escenarioSinSwap();
        escenarioVictima();
        escenarioDeadlineSuspendido();
        int rep = args.length > 0 ? Integer.parseInt(args[0]) : 30;
        verbose = false;
        int antes = fallos;
        System.out.println("-- Repetición x" + rep + " de cada escenario (hilos)");
        for (int i = 0; i < rep; i++) {
            escenarioCompleto(); escenarioSinSwap(); escenarioVictima(); escenarioDeadlineSuspendido();
        }
        verbose = true;
        asegurar(fallos == antes, "sin fallos en las repeticiones");
        System.out.println();
        System.out.println(fallos == 0 ? "TODAS LAS PRUEBAS PASARON (" + ok + ")" : "HAY " + fallos + " FALLOS");
        System.exit(fallos == 0 ? 0 : 1);
    }
}
