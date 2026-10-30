package simulacion;

import enums.EstadoProceso;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import modelo.PCB;
import nucleo.Computador;
import sincronizacion.Buffer;

/**
 * Métricas de rendimiento de una simulación de ÁvilaOS, calculadas a partir de lo que
 * ya registran los {@link PCB}, los {@link Buffer} y las CPU.
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
 *       (semáforo, mutex o latencia de red).</li>
 *   <li><b>Producidos / consumidos</b> = elementos insertados / extraídos de cada buffer.</li>
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

        PorComputador(String nombre, String politica, int procesos, int completados, int terminadosPorDeadline,
                      int deadlinesCumplidos, int terminados, double throughput, double respuestaPromedio,
                      double bloqueadoPromedio, double usoCpuPorcentaje) {
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
        }
    }

    /** Métricas de un buffer. */
    public static final class PorBuffer {
        public final String id;
        public final int idAnfitrion;
        public final int capacidad;
        public final int ocupacion;
        public final long producidos;
        public final long consumidos;

        PorBuffer(String id, int idAnfitrion, int capacidad, int ocupacion, long producidos, long consumidos) {
            this.id = id;
            this.idAnfitrion = idAnfitrion;
            this.capacidad = capacidad;
            this.ocupacion = ocupacion;
            this.producidos = producidos;
            this.consumidos = consumidos;
        }
    }

    private final long ciclos;
    private final int procesos;
    private final int terminados;
    private final int completados;
    private final int terminadosPorDeadline;
    private final int deadlinesCumplidos;
    private final int bloqueadosAhora;
    private final double throughput;
    private final double respuestaPromedio;
    private final double bloqueadoPromedio;
    private final long bloqueadoMaximo;
    private final double equidad;
    private final PorComputador[] computadores;
    private final PorBuffer[] buffers;

    private MetricasSimulacion(long ciclos, int procesos, int terminados, int completados, int terminadosPorDeadline,
                               int deadlinesCumplidos, int bloqueadosAhora, double throughput,
                               double respuestaPromedio, double bloqueadoPromedio, long bloqueadoMaximo,
                               double equidad, PorComputador[] computadores, PorBuffer[] buffers) {
        this.ciclos = ciclos;
        this.procesos = procesos;
        this.terminados = terminados;
        this.completados = completados;
        this.terminadosPorDeadline = terminadosPorDeadline;
        this.deadlinesCumplidos = deadlinesCumplidos;
        this.bloqueadosAhora = bloqueadosAhora;
        this.throughput = throughput;
        this.respuestaPromedio = respuestaPromedio;
        this.bloqueadoPromedio = bloqueadoPromedio;
        this.bloqueadoMaximo = bloqueadoMaximo;
        this.equidad = equidad;
        this.computadores = computadores;
        this.buffers = buffers;
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
        Iterador<Computador> itPc = simulador.iteradorComputadores();
        while (itPc.haySiguiente()) {
            Computador pc = itPc.siguiente();
            ListaEnlazada<PCB> deEste = procesosDe(pc);
            porPc[k++] = resumirComputador(pc, deEste, ciclos);
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

        PorBuffer[] porBuffer = new PorBuffer[contar(simulador.iteradorBuffers())];
        int b = 0;
        Iterador<Buffer> itB = simulador.iteradorBuffers();
        while (itB.haySiguiente()) {
            Buffer buf = itB.siguiente();
            porBuffer[b++] = new PorBuffer(buf.getId(), buf.getIdComputadorAnfitrion(), buf.getCapacidad(),
                    buf.getCantidadElementos(), buf.getTotalProducidos(), buf.getTotalConsumidos());
        }

        return new MetricasSimulacion(ciclos, total, terminados, completados, porDeadline, cumplidos,
                bloqueadosAhora, throughput, respuesta, bloqueado, maxBloqueado, equidad, porPc, porBuffer);
    }

    private static <T> int contar(Iterador<T> it) {
        int n = 0;
        while (it.haySiguiente()) {
            it.siguiente();
            n++;
        }
        return n;
    }

    /** Todos los procesos de un computador, sin repetir: nuevos, listos, en CPU, bloqueados y terminados. */
    private static ListaEnlazada<PCB> procesosDe(Computador pc) {
        ListaEnlazada<PCB> lista = new ListaEnlazada<>();
        agregarTodos(lista, pc.iteradorNuevos());
        agregarTodos(lista, pc.getPlanificador().iteradorListos());
        PCB enCpu = pc.getCpu().getProcesoActual();
        if (enCpu != null && !lista.contiene(enCpu)) {
            lista.agregar(enCpu);
        }
        agregarTodos(lista, pc.iteradorBloqueados());
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
                ciclos > 0 ? 100.0 * ocupado / ciclos : 0.0);
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

    public int getCantidadBuffers() {
        return buffers.length;
    }

    public PorBuffer getBuffer(int indice) {
        return buffers[indice];
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

        sb.append("\n Por buffer:\n");
        if (buffers.length == 0) {
            sb.append("   (no hay buffers)\n");
        } else {
            sb.append(String.format("   %-12s %5s %9s %10s %10s%n", "Buffer", "PC", "Ocupación", "Producidos", "Consumidos"));
            for (int i = 0; i < buffers.length; i++) {
                PorBuffer b = buffers[i];
                sb.append(String.format("   %-12s %5d %4d/%-4d %10d %10d%n", b.id, b.idAnfitrion, b.ocupacion,
                        b.capacidad, b.producidos, b.consumidos));
            }
        }
        return sb.toString();
    }
}
