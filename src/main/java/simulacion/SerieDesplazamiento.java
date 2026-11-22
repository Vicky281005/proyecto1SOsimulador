package simulacion;

import estructuras.Iterador;
import estructuras.ListaEnlazada;

/**
 * Convierte el historial ciclo a ciclo del desplazamiento acumulado del cabezal del
 * disco en los puntos (ciclo, bloques recorridos) de la gráfica.
 *
 * <p>Es lógica pura, sin Swing ni JFreeChart, para poder probarla sin pantalla. Si el
 * historial es más largo que el máximo de puntos, toma uno de cada varios y conserva
 * siempre el último, de modo que la línea termina en el valor real.</p>
 */
public final class SerieDesplazamiento {

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
}
