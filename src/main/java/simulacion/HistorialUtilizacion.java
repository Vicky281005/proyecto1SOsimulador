package simulacion;

import estructuras.Iterador;
import estructuras.ListaEnlazada;
import nucleo.Computador;

/**
 * Historial de utilización de la CPU de cada computador, ciclo por ciclo.
 *
 * <p>Después de cada ciclo, el {@link Simulador} llama a
 * {@link #registrarCiclo(Iterador)}. Para cada computador se guarda un dato por
 * ciclo: si su CPU estuvo ocupada (ejecutó una instrucción) o ociosa. A partir de
 * ahí se calculan las series que dibuja la gráfica de la Vista 2.</p>
 *
 * <p>No es seguro entre hilos por sí solo: el {@link Simulador} lo escribe con su
 * exclusión mutua y quien lo lea debe hacerlo dentro de
 * {@link Simulador#leerEstado(Runnable)}.</p>
 */
public class HistorialUtilizacion {

    /** Datos de un computador: un booleano por ciclo, desde el primero en que se registró. */
    private static final class Serie {
        private final int idComputador;
        private final String nombre;
        private final long cicloInicial;
        private final ListaEnlazada<Boolean> ocupada = new ListaEnlazada<>();
        private long acumuladoAnterior;
        private long ciclosOcupados;

        private Serie(int idComputador, String nombre, long cicloInicial) {
            this.idComputador = idComputador;
            this.nombre = nombre;
            this.cicloInicial = cicloInicial;
        }
    }

    private final ListaEnlazada<Serie> series = new ListaEnlazada<>();

    /**
     * Anota el resultado de un ciclo recién terminado para todos los computadores.
     * Un computador registrado a mitad de la simulación empieza su serie en el ciclo
     * en que se ve por primera vez.
     *
     * @param ciclo        número del ciclo que acaba de terminar.
     * @param computadores computadores de la simulación.
     */
    public void registrarCiclo(long ciclo, Iterador<Computador> computadores) {
        while (computadores.haySiguiente()) {
            Computador c = computadores.siguiente();
            Serie s = buscar(c.getId());
            if (s == null) {
                s = new Serie(c.getId(), c.getNombre(), ciclo);
                series.agregar(s);
            }
            long acumulado = c.getCpu().getCiclosEjecutados();
            boolean estuvoOcupada = acumulado > s.acumuladoAnterior;
            s.acumuladoAnterior = acumulado;
            s.ocupada.agregar(estuvoOcupada);
            if (estuvoOcupada) {
                s.ciclosOcupados++;
            }
        }
    }

    /** Cantidad de computadores con datos. */
    public int getCantidadSeries() {
        return series.getTamano();
    }

    /** Nombre del computador de la serie {@code indice} (0 a {@code getCantidadSeries()-1}). */
    public String getNombre(int indice) {
        return serie(indice).nombre;
    }

    /** Cantidad de ciclos registrados para la serie {@code indice}. */
    public int getCantidadCiclos(int indice) {
        return serie(indice).ocupada.getTamano();
    }

    /** Porcentaje de utilización de toda la simulación para la serie {@code indice} (0 a 100). */
    public double getUtilizacionTotal(int indice) {
        Serie s = serie(indice);
        int n = s.ocupada.getTamano();
        return n == 0 ? 0.0 : 100.0 * s.ciclosOcupados / n;
    }

    /**
     * Serie para graficar: la utilización como promedio móvil de los últimos
     * {@code ventana} ciclos (menos al principio, mientras no hay tantos).
     *
     * @param indice     computador (0 a {@code getCantidadSeries()-1}).
     * @param ventana    ciclos que entran en cada promedio (1 = valor instantáneo 0 o 100).
     * @param maxPuntos  máximo de puntos a devolver; si hay más ciclos se toma uno de cada
     *                   varios para que la gráfica no se vuelva lenta. El último ciclo siempre entra.
     * @return arreglo {@code [2][n]}: fila 0 = número de ciclo, fila 1 = utilización en % (0 a 100).
     */
    public double[][] utilizacion(int indice, int ventana, int maxPuntos) {
        if (ventana < 1 || maxPuntos < 2) {
            throw new IllegalArgumentException("La ventana debe ser >= 1 y maxPuntos >= 2.");
        }
        Serie s = serie(indice);
        int total = s.ocupada.getTamano();
        if (total == 0) {
            return new double[][]{new double[0], new double[0]};
        }
        int paso = (total + maxPuntos - 1) / maxPuntos;
        int cantidad = (total - 1) / paso + 1;
        boolean faltaUltimo = (total - 1) % paso != 0;
        if (faltaUltimo) {
            cantidad++;
        }
        double[] x = new double[cantidad];
        double[] y = new double[cantidad];

        int[] recientes = new int[ventana]; // anillo con los últimos valores (1 = ocupada)
        int suma = 0;
        int salida = 0;
        int i = 0;
        Iterador<Boolean> it = s.ocupada.iterador();
        while (it.haySiguiente()) {
            int valor = it.siguiente() ? 1 : 0;
            int pos = i % ventana;
            if (i >= ventana) {
                suma -= recientes[pos];
            }
            recientes[pos] = valor;
            suma += valor;
            int enVentana = Math.min(i + 1, ventana);
            boolean ultimo = (i == total - 1);
            if (i % paso == 0 || ultimo) {
                x[salida] = s.cicloInicial + i;
                y[salida] = 100.0 * suma / enVentana;
                salida++;
            }
            i++;
        }
        return new double[][]{x, y};
    }

    private Serie serie(int indice) {
        if (indice < 0 || indice >= series.getTamano()) {
            throw new IndexOutOfBoundsException("No existe la serie " + indice);
        }
        return series.obtener(indice);
    }

    private Serie buscar(int idComputador) {
        Iterador<Serie> it = series.iterador();
        while (it.haySiguiente()) {
            Serie s = it.siguiente();
            if (s.idComputador == idComputador) {
                return s;
            }
        }
        return null;
    }
}
