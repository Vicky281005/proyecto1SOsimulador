package disco;

import enums.PoliticaDisco;
import estructuras.ListaEnlazada;

/**
 * Política SCAN (Algoritmo del Ascensor) para el planificador de disco.
 *
 * <p>El cabezal recorre el disco en una dirección atendiendo todas las solicitudes
 * en su camino hasta alcanzar el extremo ({@code totalBloques - 1} si sube o {@code 0}
 * si baja), donde invierte su sentido de movimiento.</p>
 */
public class DiscoSCAN implements PoliticaPlanificacionDisco {

    @Override
    public SolicitudES seleccionar(ListaEnlazada<SolicitudES> colaES,
                                 int posicionCabezal,
                                 boolean direccionAscendente,
                                 int totalBloques) {
        if (colaES == null || colaES.estaVacia()) {
            return null;
        }
        int indiceElegido = -1;

        if (direccionAscendente) {
            // 1. Buscar el menor bloque >= posicionCabezal (en sentido ascendente ↑)
            int mejorBloque = Integer.MAX_VALUE;
            for (int i = 0; i < colaES.getTamano(); i++) {
                int bloque = colaES.obtener(i).getBloqueDestino();
                if (bloque >= posicionCabezal && bloque < mejorBloque) {
                    mejorBloque = bloque;
                    indiceElegido = i;
                }
            }
            // 2. Si no hay solicitudes adelante, tras rebotar en el tope atenderá el mayor < cabezal
            if (indiceElegido == -1) {
                int mayorBloque = -1;
                for (int i = 0; i < colaES.getTamano(); i++) {
                    int bloque = colaES.obtener(i).getBloqueDestino();
                    if (bloque > mayorBloque) {
                        mayorBloque = bloque;
                        indiceElegido = i;
                    }
                }
            }
        } else {
            // 1. Buscar el mayor bloque <= posicionCabezal (en sentido descendente ↓)
            int mejorBloque = -1;
            for (int i = 0; i < colaES.getTamano(); i++) {
                int bloque = colaES.obtener(i).getBloqueDestino();
                if (bloque <= posicionCabezal && bloque > mejorBloque) {
                    mejorBloque = bloque;
                    indiceElegido = i;
                }
            }
            // 2. Si no hay solicitudes debajo, tras rebotar en 0 atenderá el menor > cabezal
            if (indiceElegido == -1) {
                int menorBloque = Integer.MAX_VALUE;
                for (int i = 0; i < colaES.getTamano(); i++) {
                    int bloque = colaES.obtener(i).getBloqueDestino();
                    if (bloque < menorBloque) {
                        menorBloque = bloque;
                        indiceElegido = i;
                    }
                }
            }
        }

        return (indiceElegido >= 0) ? colaES.eliminarEn(indiceElegido) : colaES.eliminarPrimero();
    }

    @Override
    public PoliticaDisco getTipo() {
        return PoliticaDisco.SCAN;
    }
}
