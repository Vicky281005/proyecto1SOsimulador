package disco;

import enums.PoliticaDisco;

/**
 * Fábrica de estrategias de planificación de disco para ÁvilaOS.
 *
 * <p>Traduce el enum {@link PoliticaDisco} a la implementación concreta de
 * {@link PoliticaPlanificacionDisco}. Agregar una política nueva (como LOOK
 * o C-LOOK) solo requiere crear su clase e incorporarla aquí.</p>
 */
public final class FabricaPoliticasDisco {

    private FabricaPoliticasDisco() {
        // Clase de utilidad: no se instancia.
    }

    /**
     * Crea una instancia de la política de planificación de disco indicada.
     *
     * @param politica identificador de la política deseada.
     * @return implementación de {@link PoliticaPlanificacionDisco}.
     */
    public static PoliticaPlanificacionDisco crear(PoliticaDisco politica) {
        if (politica == null) {
            throw new IllegalArgumentException("La política de disco no puede ser nula.");
        }
        return switch (politica) {
            case FIFO -> new DiscoFIFO();
            case SSTF -> new DiscoSSTF();
            case SCAN -> new DiscoSCAN();
            case C_SCAN -> new DiscoCSCAN();
        };
    }
}
