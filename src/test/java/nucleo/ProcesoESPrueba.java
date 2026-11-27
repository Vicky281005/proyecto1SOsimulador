package nucleo;

import disco.DiscoSimulado;
import disco.OperacionRechazadaException;
import disco.ServicioArchivos;
import disco.SolicitudES;
import enums.EstadoProceso;
import enums.ModoCPU;
import enums.MotivoBloqueo;
import enums.PoliticaDisco;
import enums.TipoOperacionES;
import enums.ModoUsuario;
import estructuras.Iterador;
import modelo.PCB;
import planificacion.FCFS;
import simulacion.LogEventos;
import simulacion.Simulador;

/**
 * Prueba de punta a punta de los procesos de E/S: proceso -> CPU (kernel) -> solicitud
 * al disco -> Bloqueado (motivo disco) -> el disco avisa -> Listo -> Terminado.
 * Usa un servicio de archivos falso que solo reserva bloques; el real es del sistema de archivos.
 */
public class ProcesoESPrueba {

    private static int ok = 0;
    private static int fallos = 0;

    private static void asegurar(boolean cond, String msg) {
        if (cond) { ok++; System.out.println("  OK    " + msg); }
        else { fallos++; System.out.println("  FALLA " + msg); }
    }

    /** Servicio falso: CREAR reserva bloques; LEER va a un bloque fijo. */
    static class ServicioFalso implements ServicioArchivos {
        DiscoSimulado disco;
        boolean rechazar;
        int bloqueLectura = 100;
        volatile int completadas;
        long hiloCompletar = -1;

        ServicioFalso(DiscoSimulado d) { this.disco = d; }

        @Override
        public SolicitudES preparar(PCB p, long ciclo) {
            if (rechazar) {
                throw new OperacionRechazadaException("modo Usuario no puede operar sobre " + p.getRutaObjetivo());
            }
            if (p.getOperacion() == TipoOperacionES.CREAR) {
                int b = disco.asignarBloquesArchivo(p.getRutaObjetivo(), p.getTamanoArchivoBloques(), "#2e7d32");
                if (b < 0) throw new OperacionRechazadaException("sin espacio en el disco");
                return new SolicitudES(p, p.getOperacion(), p.getRutaObjetivo(), p.getNuevoNombre(),
                        b, p.getTamanoArchivoBloques(), ciclo, false);
            }
            return new SolicitudES(p, p.getOperacion(), p.getRutaObjetivo(), p.getNuevoNombre(),
                    bloqueLectura, 1, ciclo, false);
        }

        @Override
        public void completar(SolicitudES s, boolean exito) {
            hiloCompletar = Thread.currentThread().threadId();
            completadas++;
        }
    }

    private static Simulador armar(ServicioFalso[] out, PoliticaDisco pol) {
        Simulador sim = new Simulador();
        Computador pc = new Computador(1, "PC-1", 1024, new FCFS());
        sim.registrarComputador(pc);
        DiscoSimulado d = new DiscoSimulado("Disco", 200, 50, 0);
        d.setPolitica(pol);
        sim.setDisco(d);
        ServicioFalso sf = new ServicioFalso(d);
        sim.setServicioArchivos(sf);
        out[0] = sf;
        return sim;
    }

    private static boolean todosTerminados(PCB[] ps) {
        for (PCB p : ps) if (p.getEstado() != EstadoProceso.TERMINADO) return false;
        return true;
    }

    private static void correrHasta(Simulador sim, PCB[] ps, int maxCiclos) {
        for (int c = 0; c < maxCiclos && !todosTerminados(ps); c++) sim.getReloj().avanzarCiclo();
    }

    private static void escenarioCrear() {
        System.out.println("-- CREAR 3 bloques: kernel, bloqueo por disco, despertar y terminar");
        ServicioFalso[] sf = new ServicioFalso[1];
        Simulador sim = armar(sf, PoliticaDisco.FIFO);
        Computador pc = sim.buscarComputador(1);
        PCB p = sim.solicitarOperacionES(1, "ES-crear", TipoOperacionES.CREAR, "/docs/a.txt", "", 3,
                ModoUsuario.ADMINISTRADOR, "admin");
        boolean vioBloqueadoDisco = false, vioKernel = false, vioDespertado = false;
        for (int c = 0; c < 300 && p.getEstado() != EstadoProceso.TERMINADO; c++) {
            sim.getReloj().avanzarCiclo();
            if (p.getEstado() == EstadoProceso.BLOQUEADO && p.getMotivoBloqueo() == MotivoBloqueo.ESPERA_DISCO) vioBloqueadoDisco = true;
            if (pc.getCpu().getProcesoActual() == p && pc.getCpu().getModo() == ModoCPU.KERNEL) vioKernel = true;
            if (vioBloqueadoDisco && p.getEstado() != EstadoProceso.BLOQUEADO) vioDespertado = true;
        }
        asegurar(p.getEstado() == EstadoProceso.TERMINADO, "el proceso de E/S termina");
        asegurar(vioKernel, "ejecutó la operación en modo kernel");
        asegurar(vioBloqueadoDisco, "pasó a Bloqueado con motivo 'esperando al disco'");
        asegurar(vioDespertado, "el disco lo despertó (salió de Bloqueado)");
        asegurar(sim.getDisco().getBloquesLibresDatos() == 197, "se asignaron 3 bloques encadenados");
        asegurar(sim.getDisco().getDesplazamientoTotal() > 0, "el cabezal se movió");
        asegurar(sf[0].completadas == 1, "el sistema de archivos aplicó el efecto una sola vez");
        asegurar(!p.fueRechazadoES(), "no fue rechazado");
        asegurar(pc.getMemoriaDisponible() == pc.getMemoriaTotal(), "la RAM del proceso se liberó");
        sim.getReloj().detener();
    }

    private static void escenarioRechazo() {
        System.out.println("-- Operación rechazada: no bloquea, termina y queda en el log");
        ServicioFalso[] sf = new ServicioFalso[1];
        Simulador sim = armar(sf, PoliticaDisco.FIFO);
        sf[0].rechazar = true;
        PCB p = sim.solicitarOperacionES(1, "ES-rechazo", TipoOperacionES.ELIMINAR, "/admin/x", "", 1,
                ModoUsuario.USUARIO, "ana");
        boolean vioBloqueado = false;
        for (int c = 0; c < 100 && p.getEstado() != EstadoProceso.TERMINADO; c++) {
            sim.getReloj().avanzarCiclo();
            if (p.getEstado() == EstadoProceso.BLOQUEADO) vioBloqueado = true;
        }
        asegurar(p.getEstado() == EstadoProceso.TERMINADO, "el proceso rechazado termina igual");
        asegurar(!vioBloqueado, "no se bloqueó");
        asegurar(p.fueRechazadoES(), "guardó el motivo del rechazo");
        boolean enLog = false;
        Iterador<LogEventos.Evento> it = sim.getLogEventos().iterador();
        while (it.haySiguiente()) if (it.siguiente().getMensaje().contains("RECHAZADA")) enLog = true;
        asegurar(enLog, "el rechazo aparece en el log");
        asegurar(sim.getDisco().getTotalSolicitudesAtendidas() == 0, "el disco no recibió nada");
        sim.getReloj().detener();
    }

    private static void escenarioVarios(PoliticaDisco pol, boolean cambiarEnCaliente) {
        ServicioFalso[] sf = new ServicioFalso[1];
        Simulador sim = armar(sf, pol);
        sf[0].bloqueLectura = 150;
        PCB a = sim.solicitarOperacionES(1, "ES-1", TipoOperacionES.LEER, "/a", "", 1, ModoUsuario.ADMINISTRADOR, "admin");
        sf[0].bloqueLectura = 60;
        PCB b = sim.solicitarOperacionES(1, "ES-2", TipoOperacionES.LEER, "/b", "", 1, ModoUsuario.ADMINISTRADOR, "admin");
        sf[0].bloqueLectura = 120;
        PCB c = sim.solicitarOperacionES(1, "ES-3", TipoOperacionES.LEER, "/c", "", 1, ModoUsuario.ADMINISTRADOR, "admin");
        PCB[] ps = {a, b, c};
        for (int i = 0; i < 600 && !todosTerminados(ps); i++) {
            sim.getReloj().avanzarCiclo();
            if (cambiarEnCaliente && i == 3) sim.getDisco().setPolitica(PoliticaDisco.SCAN);
        }
        boolean ok3 = todosTerminados(ps) && sim.getDisco().getTotalSolicitudesAtendidas() == 3 && sf[0].completadas == 3;
        sim.getReloj().detener();
        if (!ok3) { fallos++; System.out.println("  FALLA 3 procesos con " + pol + (cambiarEnCaliente ? " + cambio en caliente" : "")); }
        else ok++;
    }

    public static void main(String[] args) {
        escenarioCrear();
        escenarioRechazo();
        System.out.println("-- 3 procesos de E/S simultáneos, 4 políticas, y cambio de política en caliente");
        PoliticaDisco[] pols = {PoliticaDisco.FIFO, PoliticaDisco.SSTF, PoliticaDisco.SCAN, PoliticaDisco.C_SCAN};
        int fallosAntes = fallos;
        for (PoliticaDisco p : pols) escenarioVarios(p, false);
        escenarioVarios(PoliticaDisco.SSTF, true);
        asegurar(fallos == fallosAntes, "los 3 procesos terminan con cada política");

        int repeticiones = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        System.out.println("-- Repetición x" + repeticiones + " para detectar errores de hilos");
        int antes = fallos;
        for (int i = 0; i < repeticiones; i++) {
            escenarioVarios(pols[i % 4], i % 5 == 0);
        }
        asegurar(fallos == antes, "sin fallos en las " + repeticiones + " repeticiones");

        System.out.println();
        System.out.println(fallos == 0 ? "TODAS LAS PRUEBAS PASARON (" + ok + ")" : "HAY " + fallos + " FALLOS");
        System.exit(fallos == 0 ? 0 : 1);
    }
}
