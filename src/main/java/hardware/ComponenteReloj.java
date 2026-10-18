package hardware;

/**
 * Contrato común de todo componente de ÁvilaOS que reacciona al reloj global.
 *
 * El reloj de la simulación invoca {@link #tick(long)} exactamente una vez
 * por ciclo sobre cada componente registrado (CPU, DMA, etc.), de modo que
 * todos avancen sincronizados al mismo ritmo.
 */
public interface ComponenteReloj {

    /**
     * Se ejecuta una vez por cada ciclo del reloj global.
     *
     * @param cicloActual número del ciclo que se está procesando.
     */
    void tick(long cicloActual);

    /**
     * Nombre legible del componente, para mostrarlo en la GUI y en los logs.
     *
     * @return el nombre del componente.
     */
    String getNombre();
}
