package disco;

import enums.PoliticaDisco;
import estructuras.ListaEnlazada;

/**
 * Contrato del comportamiento de una política de planificación de disco en ÁvilaOS
 * (Patrón Strategy).
 *
 * <p>El planificador de disco mantiene una referencia a esta interfaz para elegir
 * qué solicitud de la cola única de E/S se atiende a continuación según la posición
 * y dirección actual del cabezal. Agregar una política nueva (por ejemplo LOOK o C-LOOK)
 * consiste únicamente en implementar esta interfaz sin modificar el código del disco.</p>
 */
public interface PoliticaPlanificacionDisco {

    /**
     * Selecciona y extrae de la cola de E/S la siguiente solicitud que debe atender el disco.
     *
     * @param colaES              cola única de solicitudes de E/S pendientes.
     * @param posicionCabezal     bloque donde se encuentra actualmente el cabezal.
     * @param direccionAscendente {@code true} si el cabezal se mueve hacia bloques mayores (↑);
     *                            {@code false} si se mueve hacia bloques menores (↓).
     * @param totalBloques        cantidad total de bloques del disco.
     * @return la solicitud seleccionada, o {@code null} si la cola está vacía.
     */
    SolicitudES seleccionar(ListaEnlazada<SolicitudES> colaES,
                            int posicionCabezal,
                            boolean direccionAscendente,
                            int totalBloques);

    /**
     * Devuelve el identificador enumerado de la política.
     *
     * @return tipo de política de disco.
     */
    PoliticaDisco getTipo();

    /**
     * Nombre legible de la política para mostrar en la GUI y en el log de eventos.
     *
     * @return nombre de la política.
     */
    default String getNombre() {
        return getTipo().getEtiqueta();
    }
}
