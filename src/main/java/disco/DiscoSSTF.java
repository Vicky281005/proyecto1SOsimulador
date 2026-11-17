package disco;

import enums.PoliticaDisco;
import estructuras.ListaEnlazada;

/**
 * Política SSTF (Shortest Seek Time First) para el planificador de disco.
 *
 * <p>Selecciona en cada paso la solicitud pendiente cuyo bloque destino se
 * encuentre a la menor distancia absoluta desde la {@code posicionCabezal}
 * actual. En caso de empate en distancia, respeta el orden de llegada (FIFO).</p>
 */
public class DiscoSSTF implements PoliticaPlanificacionDisco {

    @Override
    public SolicitudES seleccionar(ListaEnlazada<SolicitudES> colaES,
                                 int posicionCabezal,
                                 boolean direccionAscendente,
                                 int totalBloques) {
        if (colaES == null || colaES.estaVacia()) {
            return null;
        }
        int mejorIndice = 0;
        int mejorDistancia = Math.abs(colaES.obtener(0).getBloqueDestino() - posicionCabezal);

        for (int i = 1; i < colaES.getTamano(); i++) {
            SolicitudES candidata = colaES.obtener(i);
            int distancia = Math.abs(candidata.getBloqueDestino() - posicionCabezal);
            if (distancia < mejorDistancia) {
                mejorDistancia = distancia;
                mejorIndice = i;
            }
        }
        return colaES.eliminarEn(mejorIndice);
    }

    @Override
    public PoliticaDisco getTipo() {
        return PoliticaDisco.SSTF;
    }
}
