package simulacion;

import estructuras.Iterador;
import modelo.PCB;
import nucleo.Computador;

/**
 * Genera el resumen de una simulación de ÁvilaOS.
 *
 * <p>Produce una tabla de texto con las métricas de cada proceso terminado
 * (agrupadas por computador, mostrando su política) y un resumen global con
 * los totales y el uso de CPU de cada computador, más las métricas de rendimiento
 * de {@link MetricasSimulacion}. No imprime por sí solo si
 * se usa {@link #generar()}: eso permite reutilizar el mismo texto en la
 * consola, en logs o, más adelante, mostrarlo en la GUI.</p>
 *
 * <p>Las métricas por proceso se calculan a partir de lo que ya registra el
 * {@link PCB}:
 * <ul>
 *   <li><b>Retorno</b> = cicloFin − cicloCreacion (tiempo total en el sistema).</li>
 *   <li><b>Espera</b> = retorno − ciclos de CPU consumidos.</li>
 * </ul></p>
 */
public class ReporteSimulacion {

    private final Simulador simulador;

    /**
     * @param simulador simulación ya ejecutada, de la que se toman las métricas.
     */
    public ReporteSimulacion(Simulador simulador) {
        if (simulador == null) {
            throw new IllegalArgumentException("El simulador no puede ser nulo.");
        }
        this.simulador = simulador;
    }

    /**
     * Construye el reporte completo como texto.
     *
     * @return el reporte listo para imprimir.
     */
    public String generar() {
        StringBuilder reporte = new StringBuilder();
        reporte.append("=================================================\n");
        reporte.append(" ÁvilaOS - Reporte de simulación\n");
        reporte.append("=================================================\n");

        Iterador<Computador> computadores = simulador.iteradorComputadores();
        while (computadores.haySiguiente()) {
            agregarComputador(reporte, computadores.siguiente());
        }
        agregarResumenGlobal(reporte);
        agregarMetricas(reporte);
        return reporte.toString();
    }

    /** Imprime el reporte por la salida estándar. */
    public void imprimir() {
        System.out.print(generar());
    }

    private void agregarComputador(StringBuilder reporte, Computador computador) {
        reporte.append('\n');
        reporte.append(String.format("%s  [%s]  RAM %d KB%n",
                computador.getNombre(),
                computador.getPlanificador().getPolitica().getNombre(),
                computador.getMemoriaTotal()));

        Iterador<PCB> terminados = computador.iteradorTerminados();
        if (!terminados.haySiguiente()) {
            reporte.append("   (sin procesos terminados)\n");
            return;
        }

        reporte.append(String.format("   %-8s %-11s %6s %5s %5s %7s %8s  %s%n",
                "Proceso", "Estado", "Inicio", "Fin", "CPU", "Espera", "Retorno", "Deadline"));

        while (terminados.haySiguiente()) {
            PCB proceso = terminados.siguiente();
            long retorno = proceso.getCicloFin() - proceso.getCicloCreacion();
            long espera = retorno - proceso.getCiclosCpuConsumidos();
            reporte.append(String.format("   %-8s %-11s %6d %5d %5d %7d %8d  %s%n",
                    proceso.getNombre(),
                    proceso.getEstado().getEtiqueta(),
                    proceso.getCicloInicio(),
                    proceso.getCicloFin(),
                    proceso.getCiclosCpuConsumidos(),
                    espera,
                    retorno,
                    proceso.isDeadlineCumplido() ? "cumplido" : "INCUMPLIDO"));
        }
    }

    /** Añade las métricas de rendimiento (throughput, respuesta, deadlines, equidad, buffers...). */
    private void agregarMetricas(StringBuilder reporte) {
        reporte.append('\n');
        reporte.append("-------------------------------------------------\n");
        reporte.append(" Métricas de rendimiento\n");
        reporte.append("-------------------------------------------------\n");
        reporte.append(MetricasSimulacion.calcular(simulador).formatear());
    }

    private void agregarResumenGlobal(StringBuilder reporte) {
        long ciclos = simulador.getCicloActual();

        int terminados = 0;
        Iterador<Computador> paraContar = simulador.iteradorComputadores();
        while (paraContar.haySiguiente()) {
            terminados += paraContar.siguiente().getCantidadTerminados();
        }

        reporte.append('\n');
        reporte.append("-------------------------------------------------\n");
        reporte.append(" Resumen global\n");
        reporte.append("-------------------------------------------------\n");
        reporte.append(String.format(" Ciclos totales de simulación : %d%n", ciclos));
        reporte.append(String.format(" Procesos terminados          : %d%n", terminados));
        reporte.append(String.format(" Simulación terminada         : %s%n", simulador.isTerminada() ? "sí" : "no"));
        reporte.append(String.format(" Límite de seguridad alcanzado: %s%n", simulador.isLimiteAlcanzado() ? "SÍ" : "no"));

        reporte.append('\n');
        reporte.append(" Uso de CPU por computador:\n");
        Iterador<Computador> computadores = simulador.iteradorComputadores();
        while (computadores.haySiguiente()) {
            Computador computador = computadores.siguiente();
            long ocupado = computador.getCpu().getCiclosEjecutados();
            double porcentaje = (ciclos > 0) ? (100.0 * ocupado / ciclos) : 0.0;
            reporte.append(String.format("   %-6s %d/%d ciclos  (%.0f%%)%n",
                    computador.getNombre(), ocupado, ciclos, porcentaje));
        }
    }
}
