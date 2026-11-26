package simulacion;

import enums.PoliticaDisco;
import estructuras.Iterador;
import estructuras.ListaEnlazada;

/**
 * Convierte los historiales ciclo a ciclo del disco (desplazamiento acumulado y
 * cambios de política) en series listas para {@link gui.VentanaGraficaDisco}.
 *
 * <p>Es lógica pura, sin Swing ni JFreeChart, para poder probarla sin pantalla. Si el
 * historial es más largo que el máximo de puntos, toma uno de cada varios y conserva
 * siempre el último, de modo que la línea termina en el valor real.</p>
 */
public final class SerieDesplazamiento {

    /** Representa el inicio o cambio de política activa del disco en un ciclo específico. */
    public static final class CambioPolitica {
        public final int ciclo;
        public final PoliticaDisco politica;

        public CambioPolitica(int ciclo, PoliticaDisco politica) {
            this.ciclo = ciclo;
            this.politica = politica;
        }
    }

    private SerieDesplazamiento() {
    }

    /**
     * @param historial  desplazamiento acumulado al final de cada ciclo (índice = ciclo).
     * @param maxPuntos  máximo de puntos a devolver (al menos 2).
     * @return arreglo {x, y}: x = número de ciclo (empieza en 1), y = bloques acumulados.
     */
    public static double[][] muestrear(ListaEnlazada<Long> historial, int maxPuntos) {
        if (historial == null) {
            throw new IllegalArgumentException("El historial no puede ser nulo.");
        }
        if (maxPuntos < 2) {
            throw new IllegalArgumentException("Se necesitan al menos 2 puntos.");
        }
        int n = historial.getTamano();
        if (n == 0) {
            return new double[][] {new double[0], new double[0]};
        }
        int paso = Math.max(1, (int) Math.ceil((double) n / maxPuntos));
        int cantidad = (n - 1) / paso + 1;
        boolean agregarUltimo = (n - 1) % paso != 0;
        if (agregarUltimo) {
            cantidad++;
        }
        double[] xs = new double[cantidad];
        double[] ys = new double[cantidad];
        int indice = 0;
        int k = 0;
        Iterador<Long> it = historial.iterador();
        while (it.haySiguiente()) {
            long valor = it.siguiente();
            if (indice % paso == 0 || indice == n - 1) {
                xs[k] = indice + 1;
                ys[k] = valor;
                k++;
            }
            indice++;
        }
        return new double[][] {xs, ys};
    }

    /**
     * Detecta la política inicial (ciclo 1) y cada ciclo en que ocurrió un cambio de
     * política en caliente durante la simulación.
     */
    public static ListaEnlazada<CambioPolitica> detectarCambiosPolitica(ListaEnlazada<PoliticaDisco> historial) {
        ListaEnlazada<CambioPolitica> cambios = new ListaEnlazada<>();
        if (historial == null || historial.estaVacia()) {
            return cambios;
        }
        PoliticaDisco previa = null;
        int ciclo = 1;
        Iterador<PoliticaDisco> it = historial.iterador();
        while (it.haySiguiente()) {
            PoliticaDisco actual = it.siguiente();
            if (actual != null && actual != previa) {
                cambios.agregar(new CambioPolitica(ciclo, actual));
                previa = actual;
            }
            ciclo++;
        }
        return cambios;
    }
}
