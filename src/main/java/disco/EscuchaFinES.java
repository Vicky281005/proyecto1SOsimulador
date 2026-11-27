package disco;

/**
 * Contrato de notificación (listener) invocado por el disco simulado cuando culmina
 * la atención de una solicitud de Entrada/Salida.
 *
 * <p>Lo implementa el núcleo del computador ({@link nucleo.Computador}) para despertar
 * al proceso solicitante, pasándolo de {@code BLOQUEADO} a {@code LISTO} (o de
 * {@code BLOQUEADO_SUSPENDIDO} a {@code LISTO_SUSPENDIDO}) y completar las transiciones
 * del planificador a mediano plazo.</p>
 */
public interface EscuchaFinES {

    /**
     * Se invoca inmediatamente cuando el disco termina de procesar una solicitud.
     *
     * @param solicitud solicitud de E/S que acaba de completarse.
     * @param exito     {@code true} si la operación se completó satisfactoriamente;
     *                  {@code false} si falló (por ejemplo, por falta de bloques o ruta inexistente).
     * @param detalle   mensaje descriptivo del resultado para registrar en el log de eventos.
     */
    void alTerminarSolicitud(SolicitudES solicitud, boolean exito, String detalle);
}
