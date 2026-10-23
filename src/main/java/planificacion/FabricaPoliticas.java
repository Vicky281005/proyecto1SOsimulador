package planificacion;

import enums.PoliticaPlanificacion;

/**
 * Fábrica de políticas de planificación.
 *
 * <p>Traduce el enum {@link PoliticaPlanificacion} (el que elige el usuario en
 * la Vista 1 o en la configuración) al objeto de política concreto que espera
 * el {@link nucleo.Planificador}. Agregar una política nueva solo requiere
 * crear su clase e incorporarla aquí, sin tocar el resto del sistema.</p>
 */
public final class FabricaPoliticas {

    private FabricaPoliticas() {
        // Clase de utilidad: no se instancia.
    }

    /**
     * Crea la política indicada usando el quantum por defecto de Round Robin.
     *
     * @param politica política deseada.
     * @return una instancia nueva de la política.
     */
    public static AlgoritmoPlanificacion crear(PoliticaPlanificacion politica) {
        return crear(politica, RoundRobin.QUANTUM_POR_DEFECTO);
    }

    /**
     * Crea la política indicada.
     *
     * @param politica política deseada.
     * @param quantum  rebanada de tiempo, solo relevante para Round Robin.
     * @return una instancia nueva de la política.
     */
    public static AlgoritmoPlanificacion crear(PoliticaPlanificacion politica, int quantum) {
        if (politica == null) {
            throw new IllegalArgumentException("La política de planificación no puede ser nula.");
        }
        return switch (politica) {
            case FCFS -> new FCFS();
            case ROUND_ROBIN -> new RoundRobin(quantum);
            case EDF -> new EDF();
            case PRIORIDAD -> new Prioridades();
        };
    }
}
