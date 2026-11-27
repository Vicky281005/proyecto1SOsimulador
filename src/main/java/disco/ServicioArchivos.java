package disco;

import modelo.PCB;

/**
 * Contrato entre el núcleo (Computador) y el sistema de archivos.
 *
 * <p>El Computador no conoce el árbol de directorios: cuando un proceso de E/S
 * llega a su instrucción de operación, le pide a este servicio que valide la
 * operación y arme la {@link SolicitudES} para el disco. Cuando el disco termina
 * la transferencia física, el servicio aplica el efecto lógico (crear, borrar,
 * renombrar...). Ambos métodos se invocan siempre desde el hilo del Computador,
 * nunca desde el hilo del disco.</p>
 */
public interface ServicioArchivos {

    /**
     * Valida permisos y existencia, reserva lo necesario (por ejemplo los bloques
     * de un CREAR) y devuelve la solicitud que se encolará en el disco. Los datos
     * de la operación están en el PCB: operación, ruta, nuevo nombre, tamaño,
     * modo y usuario solicitante.
     *
     * @throws OperacionRechazadaException si la operación no está permitida.
     */
    SolicitudES preparar(PCB proceso, long cicloActual) throws OperacionRechazadaException;

    /**
     * Aplica el efecto lógico de la operación una vez que el disco terminó.
     *
     * @param solicitud solicitud que el disco acaba de atender.
     * @param exito     {@code true} si la transferencia terminó bien.
     */
    void completar(SolicitudES solicitud, boolean exito);
}
