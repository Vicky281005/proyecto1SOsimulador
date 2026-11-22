package simulacion;

import disco.DiscoSimulado;
import enums.EstadoProceso;
import enums.PoliticaDisco;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import modelo.PCB;
import nucleo.Computador;

/**
 * Métricas de rendimiento de una simulación de ÁvilaOS, calculadas a partir de lo que
 * ya registran los {@link PCB}, las CPU y el disco.
 *
 * <p>Es una <b>foto</b> (snapshot): {@link #calcular(Simulador)} recorre el estado una
 * vez y devuelve un objeto inmutable. Si la simulación está corriendo, hay que llamarlo
 * dentro de {@link Simulador#leerEstado(Runnable)} para no ver las colas a medias
 * (la Vista de métricas y el reporte lo hacen así).</p>
 *
 * <h3>Definiciones (todo en ciclos de reloj)</h3>
 * <ul>
 *   <li><b>Throughput</b> = procesos completados / ciclos transcurridos. Un proceso
 *       "completado" ejecutó todas sus instrucciones; los que el sistema cerró por
 *       vencer su deadline se cuentan aparte.</li>
 *   <li><b>Tiempo de respuesta</b> = cicloInicio − cicloCreación, es decir, lo que
 *       esperó un proceso hasta recibir la CPU por primera vez. El promedio incluye a
 *       todos los procesos que ya la recibieron.</li>
 *   <li><b>% deadlines cumplidos</b> = terminados dentro de su plazo / terminados.</li>
 *   <li><b>Equidad</b> = índice de Jain sobre la fracción de CPU que recibió cada
 *       proceso (ciclos de CPU / ciclos que lleva en el sistema):
 *       J = (Σx)² / (n·Σx²). Vale 1 si todos reciben la misma fracción y baja hacia
 *       1/n cuanto más desigual es el reparto.</li>
 *   <li><b>Tiempo bloqueado</b> = ciclos que el proceso estuvo en la lista de bloqueados
 *       (E/S, espera de disco o de otro recurso).</li>
 * </ul>
 *
 * <p>Los valores que no se pueden calcular todavía (por ejemplo, el % de deadlines sin
 * ningún proceso terminado) valen {@link #NO_DISPONIBLE}.</p>
 */
public final class MetricasSimulacion {

    /** Valor de una métrica que aún no tiene datos para calcularse. */
    public static final double NO_DISPONIBLE = -1.0;

    /** Métricas de un computador. */
    public static final class PorComputador {
        public final String nombre;
        public final String politica;
        public final int procesos;
        public final int completados;
        public final int terminadosPorDeadline;
        public final int deadlinesCumplidos;
        public final int terminados;
        public final double throughput;
        public final double respuestaPromedio;
        public final double bloqueadoPromedio;
        public final double usoCpuPorcentaje;
        public final int suspensiones;
        public final int reactivaciones;
        public final int listosSuspendidos;
        public final int bloqueadosSuspendidos;

        PorComputador(String nombre, String politica, int procesos, int completados, int terminadosPorDeadline,
                      int deadlinesCumplidos, int terminados, double throughput, double respuestaPromedio,
                      double bloqueadoPromedio, double usoCpuPorcentaje,
                      int suspensiones, int reactivaciones, int listosSuspendidos, int bloqueadosSuspendidos) {
            this.nombre = nombre;
            this.politica = politica;
            this.procesos = procesos;
            this.completados = completados;
            this.terminadosPorDeadline = terminadosPorDeadline;
            this.deadlinesCumplidos = deadlinesCumplidos;
            this.terminados = terminados;
            this.throughput = throughput;
            this.respuestaPromedio = respuestaPromedio;
            this.bloqueadoPromedio = bloqueadoPromedio;
            this.usoCpuPorcentaje = usoCpuPorcentaje;
            this.suspensiones = suspensiones;
            this.reactivaciones = reactivaciones;
            this.listosSuspendidos = listosSuspendidos;
            this.bloqueadosSuspendidos = bloqueadosSuspendidos;
        }
    }

    private final long ciclos;
    private final int procesos;
    private final int terminados;
    private final int completados;
    private final int terminadosPorDeadline;
    private final int deadlinesCumplidos;
    private final int bloqueadosAhora;
    private final int suspendidosAhora;
    private final int totalSuspensiones;
    private final int totalReactivaciones;
    private final double throughput;
    private final double respuestaPromedio;
    private final double bloqueadoPromedio;
    private final long bloqueadoMaximo;
    private final double equidad;
    private final PorComputador[] computadores;
    private final MetricasDisco disco;

    /** Foto de las estadísticas del disco simulado (o "sin disco" si el simulador no tiene uno). */
    public static final class MetricasDisco {
        public final boolean hayDisco;
        public final PoliticaDisco politica;
        public final long desplazamientoTotal;
        public final long solicitudesAtendidas;
        public final double esperaPromedio;
        /** Solicitudes atendidas por cada política, en el orden de {@link PoliticaDisco#values()}. */
        public final long[] porPolitica;

        MetricasDisco(boolean hayDisco, PoliticaDisco politica, long desplazamientoTotal,
                      long solicitudesAtendidas, double esperaPromedio, long[] porPolitica) {
            this.hayDisco = hayDisco;
            this.politica = politica;
            this.desplazamientoTotal = desplazamientoTotal;
            this.solicitudesAtendidas = solicitudesAtendidas;
            this.esperaPromedio = esperaPromedio;
            this.porPolitica = porPolitica;
        }

        static MetricasDisco de(DiscoSimulado d) {
            long[] pp = new long[PoliticaDisco.values().length];
            if (d == null) {
                return new MetricasDisco(false, null, 0, 0, 0.0, pp);
            }
            for (PoliticaDisco p : PoliticaDisco.values()) {
                pp[p.ordinal()] = d.getSolicitudesAtendidas(p);
            }
            return new MetricasDisco(true, d.getPoliticaActual(), d.getDesplazamientoTotal(),
                    d.getTotalSolicitudesAtendidas(), d.getTiempoEsperaPromedio(), pp);
        }
    }

    private MetricasSimulacion(long ciclos, int procesos, int terminados, int completados, int terminadosPorDeadline,
                               int deadlinesCumplidos, int bloqueadosAhora, int suspendidosAhora,
                               int totalSuspensiones, int totalReactivaciones, double throughput,
                               double respuestaPromedio, double bloqueadoPromedio, long bloqueadoMaximo,
                               double equidad, PorComputador[] computadores,
                               MetricasDisco disco) {
        this.disco = disco;
        this.ciclos = ciclos;
        this.procesos = procesos;
        this.terminados = terminados;
        this.completados = completados;
        this.terminadosPorDeadline = terminadosPorDeadline;
        this.deadlinesCumplidos = deadlinesCumplidos;
        this.bloqueadosAhora = bloqueadosAhora;
        this.suspendidosAhora = suspendidosAhora;
        this.totalSuspensiones = totalSuspensiones;
        this.totalReactivaciones = totalReactivaciones;
        this.throughput = throughput;
        this.respuestaPromedio = respuestaPromedio;
        this.bloqueadoPromedio = bloqueadoPromedio;
        this.bloqueadoMaximo = bloqueadoMaximo;
        this.equidad = equidad;
        this.computadores = computadores;
    }

    // ------------------------------------------------------------------ cálculo

    /**
     * Calcula las métricas con el estado actual del simulador.
     *
     * @param simulador simulación (en marcha, pausada o terminada).
     * @return la foto de las métricas.
     */
    public static MetricasSimulacion calcular(Simulador simulador) {
        if (simulador == null) {
            throw new IllegalArgumentException("El simulador no puede ser nulo.");
        }
        long ciclos = simulador.getCicloActual();

        PorComputador[] porPc = new PorComputador[simulador.getCantidadComputadores()];
        ListaEnlazada<PCB> todos = new ListaEnlazada<>();
        int k = 0;
        int totalSuspensiones = 0;
        int totalReactivaciones = 0;
        Iterador<Computador> itPc = simulador.iteradorComputadores();
        while (itPc.haySiguiente()) {
            Computador pc = itPc.siguiente();
            ListaEnlazada<PCB> deEste = procesosDe(pc);
            porPc[k] = resumirComputador(pc, deEste, ciclos);
            totalSuspensiones += porPc[k].suspensiones;
            totalReactivaciones += porPc[k].reactivaciones;
            k++;
            Iterador<PCB> it = deEste.iterador();
            while (it.haySiguiente()) {
                todos.agregar(it.siguiente());
            }
        }

        int terminados = 0;
        int completados = 0;
        int porDeadline = 0;
        int cumplidos = 0;
        int bloqueadosAhora = 0;
        int suspendidosAhora = 0;
        long sumaRespuesta = 0;
        int conRespuesta = 0;
        long sumaBloqueado = 0;
        long maxBloqueado = 0;
        double sumaX = 0;
        double sumaX2 = 0;
        int nEquidad = 0;
        Iterador<PCB> it = todos.iterador();
        while (it.haySiguiente()) {
            PCB p = it.siguiente();
            boolean fin = p.getEstado() == EstadoProceso.TERMINADO;
            if (fin) {
                terminados++;
                if (p.getTiempoRestante() == 0) {
                    completados++;
                } else {
                    porDeadline++;
                }
                if (p.isDeadlineCumplido()) {
                    cumplidos++;
                }
            }
            if (p.getEstado() == EstadoProceso.BLOQUEADO) {
                bloqueadosAhora++;
            }
            if (p.getEstado().estaSuspendido()) {
                suspendidosAhora++;
            }
            if (p.getCicloInicio() >= 0) {
                sumaRespuesta += p.getCicloInicio() - p.getCicloCreacion();
                conRespuesta++;
            }
            sumaBloqueado += p.getTiempoBloqueadoAcumulado();
            maxBloqueado = Math.max(maxBloqueado, p.getTiempoBloqueadoAcumulado());
            long enSistema = (fin && p.getCicloFin() >= 0 ? p.getCicloFin() : ciclos) - p.getCicloCreacion();
            if (enSistema > 0) {
                double x = Math.min(1.0, (double) p.getCiclosCpuConsumidos() / enSistema);
                sumaX += x;
                sumaX2 += x * x;
                nEquidad++;
            }
        }

        int total = todos.getTamano();
        double throughput = ciclos > 0 ? (double) completados / ciclos : 0.0;
        double respuesta = conRespuesta > 0 ? (double) sumaRespuesta / conRespuesta : NO_DISPONIBLE;
        double bloqueado = total > 0 ? (double) sumaBloqueado / total : NO_DISPONIBLE;
        double equidad = nEquidad == 0 ? NO_DISPONIBLE : (sumaX2 == 0 ? 1.0 : (sumaX * sumaX) / (nEquidad * sumaX2));

        return new MetricasSimulacion(ciclos, total, terminados, completados, porDeadline, cumplidos,
                bloqueadosAhora, suspendidosAhora, totalSuspensiones, totalReactivaciones,
                throughput, respuesta, bloqueado, maxBloqueado, equidad, porPc,
                MetricasDisco.de(simulador.getDisco()));
    }

    /** Todos los procesos de un computador, sin repetir: nuevos, listos, en CPU, bloqueados, suspendidos y terminados. */
    private static ListaEnlazada<PCB> procesosDe(Computador pc) {
        ListaEnlazada<PCB> lista = new ListaEnlazada<>();
        agregarTodos(lista, pc.iteradorNuevos());
        agregarTodos(lista, pc.getPlanificador().iteradorListos());
        PCB enCpu = pc.getCpu().getProcesoActual();
        if (enCpu != null && !lista.contiene(enCpu)) {
            lista.agregar(enCpu);
        }
        agregarTodos(lista, pc.iteradorBloqueados());
        agregarTodos(lista, pc.iteradorListosSuspendidos());
        agregarTodos(lista, pc.iteradorBloqueadosSuspendidos());
        agregarTodos(lista, pc.iteradorTerminados());
        return lista;
    }

    private static void agregarTodos(ListaEnlazada<PCB> destino, Iterador<PCB> origen) {
        while (origen.haySiguiente()) {
            PCB p = origen.siguiente();
            if (p != null && !destino.contiene(p)) {
                destino.agregar(p);
            }
        }
    }

    private static PorComputador resumirComputador(Computador pc, ListaEnlazada<PCB> procesos, long ciclos) {
        int terminados = 0;
        int completados = 0;
        int porDeadline = 0;
        int cumplidos = 0;
        long sumaResp = 0;
        int conResp = 0;
        long sumaBloq = 0;
        Iterador<PCB> it = procesos.iterador();
        while (it.haySiguiente()) {
            PCB p = it.siguiente();
            if (p.getEstado() == EstadoProceso.TERMINADO) {
                terminados++;
                if (p.getTiempoRestante() == 0) {
                    completados++;
                } else {
                    porDeadline++;
                }
                if (p.isDeadlineCumplido()) {
                    cumplidos++;
                }
            }
            if (p.getCicloInicio() >= 0) {
                sumaResp += p.getCicloInicio() - p.getCicloCreacion();
                conResp++;
            }
            sumaBloq += p.getTiempoBloqueadoAcumulado();
        }
        int n = procesos.getTamano();
        long ocupado = pc.getCpu().getCiclosEjecutados();
        return new PorComputador(pc.getNombre(), pc.getPlanificador().getPolitica().getNombre(), n, completados,
                porDeadline, cumplidos, terminados,
                ciclos > 0 ? (double) completados / ciclos : 0.0,
                conResp > 0 ? (double) sumaResp / conResp : NO_DISPONIBLE,
                n > 0 ? (double) sumaBloq / n : NO_DISPONIBLE,
                ciclos > 0 ? 100.0 * ocupado / ciclos : 0.0,
                pc.getTotalSuspensiones(), pc.getTotalReactivaciones(),
                pc.getCantidadListosSuspendidos(), pc.getCantidadBloqueadosSuspendidos());
    }

    // ------------------------------------------------------------------ acceso

    public long getCiclos() {
        return ciclos;
    }

    /** Procesos que existen (en cualquier estado). */
    public int getProcesos() {
        return procesos;
    }

    /** Procesos en estado TERMINADO (completados + cerrados por deadline). */
    public int getTerminados() {
        return terminados;
    }

    /** Procesos que ejecutaron todo su programa. */
    public int getCompletados() {
        return completados;
    }

    /** Procesos que el sistema cerró porque vencieron su deadline sin terminar. */
    public int getTerminadosPorDeadline() {
        return terminadosPorDeadline;
    }

    public int getDeadlinesCumplidos() {
        return deadlinesCumplidos;
    }

    public int getSuspendidosAhora() {
        return suspendidosAhora;
    }

    public int getTotalSuspensiones() {
        return totalSuspensiones;
    }

    public int getTotalReactivaciones() {
        return totalReactivaciones;
    }

    public int getBloqueadosAhora() {
        return bloqueadosAhora;
    }

    /** Procesos completados por ciclo de reloj. */
    public double getThroughput() {
        return throughput;
    }

    /** Promedio de espera hasta la primera CPU, en ciclos, o {@link #NO_DISPONIBLE}. */
    public double getRespuestaPromedio() {
        return respuestaPromedio;
    }

    /** Porcentaje (0–100) de terminados que cumplieron su deadline, o {@link #NO_DISPONIBLE}. */
    public double getPorcentajeDeadlines() {
        return terminados > 0 ? 100.0 * deadlinesCumplidos / terminados : NO_DISPONIBLE;
    }

    /** Índice de equidad de Jain (de 1/n a 1), o {@link #NO_DISPONIBLE}. */
    public double getEquidad() {
        return equidad;
    }

    /** Ciclos bloqueados promedio por proceso, o {@link #NO_DISPONIBLE}. */
    public double getBloqueadoPromedio() {
        return bloqueadoPromedio;
    }

    public long getBloqueadoMaximo() {
        return bloqueadoMaximo;
    }

    public int getCantidadComputadores() {
        return computadores.length;
    }

    public PorComputador getComputador(int indice) {
        return computadores[indice];
    }

    /** Estadísticas del disco (nunca nulo; {@code hayDisco} indica si el simulador tiene uno). */
    public MetricasDisco getDisco() {
        return disco;
    }

    // ------------------------------------------------------------------ texto

    private static String num(double v, String formato) {
        return v == NO_DISPONIBLE ? "n/d" : String.format(formato, v);
    }

    /** Las métricas como texto alineado, para el reporte y la ventana de métricas. */
    public String formatear() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(" Ciclos transcurridos          : %d%n", ciclos));
        sb.append(String.format(" Procesos (total / terminados): %d / %d  (completados %d, cerrados por deadline %d)%n",
                procesos, terminados, completados, terminadosPorDeadline));
        sb.append(String.format(" Throughput                   : %s procesos/ciclo  (%s por cada 100 ciclos)%n",
                num(throughput, "%.4f"), num(throughput * 100, "%.2f")));
        sb.append(String.format(" Tiempo de respuesta promedio : %s ciclos%n", num(respuestaPromedio, "%.2f")));
        sb.append(String.format(" Deadlines cumplidos          : %s  (%d de %d terminados)%n",
                num(getPorcentajeDeadlines(), "%.1f%%"), deadlinesCumplidos, terminados));
        sb.append(String.format(" Equidad (índice de Jain)     : %s  (1 = reparto igual de CPU)%n", num(equidad, "%.3f")));
        sb.append(String.format(" Tiempo bloqueado             : promedio %s ciclos, máximo %d, bloqueados ahora %d%n",
                num(bloqueadoPromedio, "%.2f"), bloqueadoMaximo, bloqueadosAhora));

        sb.append("\n Por computador:\n");
        sb.append(String.format("   %-6s %-32s %5s %5s %6s %9s %9s %8s %6s%n",
                "PC", "Política", "Proc", "Term", "Thrput", "Respuesta", "Bloqueado", "Deadline", "CPU"));
        for (int i = 0; i < computadores.length; i++) {
            PorComputador c = computadores[i];
            sb.append(String.format("   %-6s %-32s %5d %5d %6s %9s %9s %8s %6s%n", c.nombre, c.politica, c.procesos,
                    c.terminados, num(c.throughput, "%.3f"), num(c.respuestaPromedio, "%.1f"),
                    num(c.bloqueadoPromedio, "%.1f"),
                    c.terminados > 0 ? String.format("%.0f%%", 100.0 * c.deadlinesCumplidos / c.terminados) : "n/d",
                    String.format("%.0f%%", c.usoCpuPorcentaje)));
        }

        sb.append("\n Suspensión de procesos (área swap):\n");
        sb.append(String.format(" Total suspensiones / reactivaciones: %d / %d  (en swap ahora: %d)%n",
                totalSuspensiones, totalReactivaciones, suspendidosAhora));
        sb.append(String.format("   %-6s %13s %13s %12s %14s%n", "PC", "Suspensiones", "Reactivaciones",
                "Listo/Susp.", "Bloqueado/Susp."));
        for (int i = 0; i < computadores.length; i++) {
            PorComputador c = computadores[i];
            sb.append(String.format("   %-6s %13d %13d %12d %14d%n", c.nombre, c.suspensiones, c.reactivaciones,
                    c.listosSuspendidos, c.bloqueadosSuspendidos));
        }

        sb.append("\n Disco y planificación de E/S:\n");
        if (!disco.hayDisco) {
            sb.append("   (sin disco configurado)\n");
        } else {
            sb.append(String.format("   Política actual: %s%n", disco.politica.getCodigoJson()));
            sb.append(String.format("   Desplazamiento total del cabezal : %d bloques%n", disco.desplazamientoTotal));
            sb.append(String.format("   Solicitudes atendidas            : %d%n", disco.solicitudesAtendidas));
            sb.append(String.format("   Espera promedio en cola de disco : %.1f ciclos%n", disco.esperaPromedio));
            sb.append("   Atendidas por política           :");
            for (PoliticaDisco p : PoliticaDisco.values()) {
                sb.append(String.format("  %s=%d", p.getCodigoJson(), disco.porPolitica[p.ordinal()]));
            }
            sb.append('\n');
        }

        return sb.toString();
    }
}
