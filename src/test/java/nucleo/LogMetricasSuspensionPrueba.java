package nucleo;

import disco.DiscoSimulado;
import enums.EstadoProceso;
import enums.ModoUsuario;
import enums.PoliticaDisco;
import enums.TipoInstruccion;
import enums.TipoOperacionES;
import enums.TipoProceso;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import modelo.Instruccion;
import modelo.PCB;
import simulacion.LogEventos;
import simulacion.MetricasSimulacion;
import simulacion.Simulador;
import planificacion.FCFS;

/** Prueba de #80: tipos de log de disco/swap, política en caliente y métricas de suspensión. */
public class LogMetricasSuspensionPrueba {

    private static int ok = 0, fallos = 0;

    private static void asegurar(boolean c, String m) {
        if (c) { ok++; System.out.println("  OK    " + m); }
        else { fallos++; System.out.println("  FALLA " + m); }
    }

    private static int contar(Simulador sim, LogEventos.TipoEvento t) {
        int n = 0;
        Iterador<LogEventos.Evento> it = sim.getLogEventos().iterador();
        while (it.haySiguiente()) if (it.siguiente().getTipo() == t) n++;
        return n;
    }

    private static boolean hayMensaje(Simulador sim, LogEventos.TipoEvento t, String frag) {
        Iterador<LogEventos.Evento> it = sim.getLogEventos().iterador();
        while (it.haySiguiente()) {
            LogEventos.Evento e = it.siguiente();
            if (e.getTipo() == t && e.getMensaje().contains(frag)) return true;
        }
        return false;
    }

    private static ListaEnlazada<Instruccion> espera(int ciclos) {
        ListaEnlazada<Instruccion> p = new ListaEnlazada<>();
        p.agregar(new Instruccion(TipoInstruccion.CPU));
        p.agregar(new Instruccion(TipoInstruccion.READ_IO, String.valueOf(ciclos)));
        p.agregar(new Instruccion(TipoInstruccion.CPU));
        return p;
    }

    private static ListaEnlazada<Instruccion> cpu(int n) {
        ListaEnlazada<Instruccion> p = new ListaEnlazada<>();
        for (int i = 0; i < n; i++) p.agregar(new Instruccion(TipoInstruccion.CPU));
        return p;
    }

    public static void main(String[] args) {
        Simulador sim = new Simulador();
        Computador pc = new Computador(1, "PC-1", 10, new FCFS());
        sim.registrarComputador(pc);
        DiscoSimulado disco = new DiscoSimulado("Disco", 200, 50, 20);
        sim.setDisco(disco);
        sim.setServicioArchivos(new ProcesoESPrueba.ServicioFalso(disco));

        PCB a = new PCB("A", 1, TipoProceso.IO_BOUND, 5, 6, 100000, "", 0, 0, espera(30), 0);
        pc.recibirProceso(a);
        PCB n = new PCB("N", 1, TipoProceso.CPU_BOUND, 1, 8, 100000, "", 0, 0, cpu(3), 0);
        pc.recibirProceso(n);
        PCB es = sim.solicitarOperacionES(1, "ES-crear", TipoOperacionES.CREAR, "/d/a.txt", "", 3,
                ModoUsuario.ADMINISTRADOR, "admin");
        PCB rech = null;

        boolean cambio = false;
        boolean vistoSusp = false;
        int maxSuspAhora = 0;
        for (int c = 0; c < 600; c++) {
            sim.getReloj().avanzarCiclo();
            if (c == 2 && !cambio) { disco.setPolitica(PoliticaDisco.SCAN); cambio = true; }
            MetricasSimulacion m = MetricasSimulacion.calcular(sim);
            if (m.getSuspendidosAhora() > maxSuspAhora) maxSuspAhora = m.getSuspendidosAhora();
            if (a.getEstado().estaSuspendido()) vistoSusp = true;
            if (a.getEstado() == EstadoProceso.TERMINADO && n.getEstado() == EstadoProceso.TERMINADO
                    && es.getEstado() == EstadoProceso.TERMINADO) break;
        }

        MetricasSimulacion m = MetricasSimulacion.calcular(sim);
        System.out.println("-- Tipos de log");
        asegurar(contar(sim, LogEventos.TipoEvento.SUSPENSION) >= 2, "log con eventos SUSPENSION");
        asegurar(contar(sim, LogEventos.TipoEvento.REACTIVACION) >= 2, "log con eventos REACTIVACION");
        asegurar(contar(sim, LogEventos.TipoEvento.DISCO) >= 3, "log con eventos DISCO");
        asegurar(hayMensaje(sim, LogEventos.TipoEvento.DISCO, "elige"), "el disco registra qué solicitud elige");
        asegurar(hayMensaje(sim, LogEventos.TipoEvento.DISCO, "distancia"), "incluye la distancia recorrida");
        asegurar(hayMensaje(sim, LogEventos.TipoEvento.CAMBIO_POLITICA, "fifo -> scan")
                || hayMensaje(sim, LogEventos.TipoEvento.CAMBIO_POLITICA, "->"), "cambio de política del disco registrado");
        asegurar(contar(sim, LogEventos.TipoEvento.OTRO) == 0, "ya no se usa el tipo OTRO");

        System.out.println("-- Métricas de suspensión");
        asegurar(vistoSusp, "A llegó a suspenderse");
        asegurar(m.getTotalSuspensiones() == pc.getTotalSuspensiones() && m.getTotalSuspensiones() >= 1,
                "suspensiones de la métrica = contador del computador");
        asegurar(m.getTotalReactivaciones() == pc.getTotalReactivaciones(), "reactivaciones coinciden");
        asegurar(maxSuspAhora >= 1, "se midió 'suspendidos ahora' >= 1 durante la corrida");
        asegurar(m.getSuspendidosAhora() == 0, "al final no quedan suspendidos");
        asegurar(m.formatear().contains("Suspensión de procesos"), "el reporte incluye la sección de suspensión");

        System.out.println("-- Conteo de procesos incluye suspendidos");
        Simulador s2 = new Simulador();
        Computador p2 = new Computador(1, "PC-1", 10, new FCFS());
        s2.registrarComputador(p2);
        DiscoSimulado d2 = new DiscoSimulado("Disco", 200, 50, 20);
        s2.setDisco(d2);
        s2.setServicioArchivos(new ProcesoESPrueba.ServicioFalso(d2));
        PCB x = new PCB("X", 1, TipoProceso.IO_BOUND, 5, 6, 100000, "", 0, 0, espera(300), 0);
        p2.recibirProceso(x);
        PCB y = new PCB("Y", 1, TipoProceso.CPU_BOUND, 1, 8, 100000, "", 0, 0, cpu(500), 0);
        p2.recibirProceso(y);
        boolean enSwap = false;
        for (int c = 0; c < 200 && !enSwap; c++) {
            s2.getReloj().avanzarCiclo();
            enSwap = x.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO;
        }
        MetricasSimulacion m2 = MetricasSimulacion.calcular(s2);
        asegurar(enSwap, "X está en Bloqueado/Suspendido");
        asegurar(m2.getSuspendidosAhora() == 1, "suspendidosAhora = 1");
        int total = 0;
        for (int i = 0; i < m2.getCantidadComputadores(); i++) total += m2.getComputador(i).procesos;
        asegurar(total == 2, "el total por computador cuenta al suspendido (2 procesos)");
        s2.getReloj().detener();
        sim.getReloj().detener();

        System.out.println(fallos == 0 ? "TODAS LAS PRUEBAS PASARON (" + ok + ")" : "HAY " + fallos + " FALLOS");
        if (fallos > 0) System.exit(1);
    }
}
