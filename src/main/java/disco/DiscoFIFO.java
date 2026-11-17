package disco;

import enums.PoliticaDisco;
import estructuras.ListaEnlazada;

/**
 * Política FIFO (First In, First Out) para el planificador de disco.
 *
 * <p>Atiende las solicitudes de Entrada/Salida en el orden estricto de llegada
 * a la cola única del disco, sin reordenarlas según la posición del cabezal.</p>
 */
public class DiscoFIFO implements PoliticaPlanificacionDisco {

    @Override
    public SolicitudES seleccionar(ListaEnlazada<SolicitudES> colaES,
                                 int posicionCabezal,
                                 boolean direccionAscendente,
                                 int totalBloques) {
        if (colaES == null || colaES.estaVacia()) {
            return null;
        }
        return colaES.eliminarPrimero();
    }

    @Override
    public PoliticaDisco getTipo() {
        return PoliticaDisco.FIFO;
    }
}
