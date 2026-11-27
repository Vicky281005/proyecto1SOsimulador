package disco;

import enums.PoliticaDisco;
import estructuras.ListaEnlazada;

/**
 * Política C-SCAN (Circular SCAN) para el planificador de disco.
 *
 * <p>El cabezal recorre el disco en una única dirección útil (habitualmente hacia
 * bloques mayores ↑). Al alcanzar el final sin más solicitudes adelante, salta de
 * regreso al bloque {@code 0} (sin atender solicitudes en el retorno) y reinicia
 * el barrido en el mismo sentido.</p>
 */
public class DiscoCSCAN implements PoliticaPlanificacionDisco {

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
            // 1. Buscar el menor bloque >= posicionCabezal (sentido ascendente ↑)
            int mejorBloque = Integer.MAX_VALUE;
            for (int i = 0; i < colaES.getTamano(); i++) {
                int bloque = colaES.obtener(i).getBloqueDestino();
                if (bloque >= posicionCabezal && bloque < mejorBloque) {
                    mejorBloque = bloque;
                    indiceElegido = i;
                }
            }
            // 2. Si no hay solicitudes adelante, tras saltar al bloque 0 atenderá el menor global
            if (indiceElegido == -1) {
                int menorGlobal = Integer.MAX_VALUE;
                for (int i = 0; i < colaES.getTamano(); i++) {
                    int bloque = colaES.obtener(i).getBloqueDestino();
                    if (bloque < menorGlobal) {
                        menorGlobal = bloque;
                        indiceElegido = i;
                    }
                }
            }
        } else {
            // 1. Buscar el mayor bloque <= posicionCabezal (sentido descendente ↓)
            int mejorBloque = -1;
            for (int i = 0; i < colaES.getTamano(); i++) {
                int bloque = colaES.obtener(i).getBloqueDestino();
                if (bloque <= posicionCabezal && bloque > mejorBloque) {
                    mejorBloque = bloque;
                    indiceElegido = i;
                }
            }
            // 2. Si no hay solicitudes debajo, tras saltar al extremo superior atenderá el mayor global
            if (indiceElegido == -1) {
                int mayorGlobal = -1;
                for (int i = 0; i < colaES.getTamano(); i++) {
                    int bloque = colaES.obtener(i).getBloqueDestino();
                    if (bloque > mayorGlobal) {
                        mayorGlobal = bloque;
                        indiceElegido = i;
                    }
                }
            }
        }

        return (indiceElegido >= 0) ? colaES.eliminarEn(indiceElegido) : colaES.eliminarPrimero();
    }

    @Override
    public PoliticaDisco getTipo() {
        return PoliticaDisco.C_SCAN;
    }
}
