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

    /** Datos de un computador: estado ciclo a ciclo desde el primero en que se registró. */
    private static final class Serie {
        private final int idComputador;
        private final String nombre;
        private final long cicloInicial;
        private final ListaEnlazada<Boolean> ocupada = new ListaEnlazada<>();
        private final ListaEnlazada<Boolean> enKernel = new ListaEnlazada<>();
        private long acumuladoAnterior;
        private long acumuladoKernelAnterior;
        private long ciclosOcupados;
        private long ciclosUsuario;
        private long ciclosKernel;

        private Serie(int idComputador, String nombre, long cicloInicial) {
            this.idComputador = idComputador;
            this.nombre = nombre;
            this.cicloInicial = cicloInicial;
        }
    }

    private enum FiltroSerie {
        TOTAL,
        USUARIO,
        KERNEL
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
            long acumKernel = c.getCpu().getCiclosKernel();
            boolean estuvoOcupada = acumulado > s.acumuladoAnterior;
            boolean fueKernel = estuvoOcupada && (acumKernel > s.acumuladoKernelAnterior);
            s.acumuladoAnterior = acumulado;
            s.acumuladoKernelAnterior = acumKernel;
            s.ocupada.agregar(estuvoOcupada);
            s.enKernel.agregar(fueKernel);
            if (estuvoOcupada) {
                s.ciclosOcupados++;
                if (fueKernel) {
                    s.ciclosKernel++;
                } else {
                    s.ciclosUsuario++;
                }
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

    /** Porcentaje de ciclos totales en modo usuario (0 a 100). */
    public double getUtilizacionUsuario(int indice) {
        Serie s = serie(indice);
        int n = s.ocupada.getTamano();
        return n == 0 ? 0.0 : 100.0 * s.ciclosUsuario / n;
    }

    /** Porcentaje de ciclos totales en modo kernel / E/S (0 a 100). */
    public double getUtilizacionKernel(int indice) {
        Serie s = serie(indice);
        int n = s.ocupada.getTamano();
        return n == 0 ? 0.0 : 100.0 * s.ciclosKernel / n;
    }

    public long getCiclosOcupados(int indice) {
        return serie(indice).ciclosOcupados;
    }

    public long getCiclosUsuario(int indice) {
        return serie(indice).ciclosUsuario;
    }

    public long getCiclosKernel(int indice) {
        return serie(indice).ciclosKernel;
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
        return calcularVentana(serie(indice), FiltroSerie.TOTAL, ventana, maxPuntos);
    }

    /** Promedio móvil solo para ejecución en modo Usuario. */
    public double[][] utilizacionUsuario(int indice, int ventana, int maxPuntos) {
        return calcularVentana(serie(indice), FiltroSerie.USUARIO, ventana, maxPuntos);
    }

    /** Promedio móvil solo para ejecución en modo Kernel / E/S. */
    public double[][] utilizacionKernel(int indice, int ventana, int maxPuntos) {
        return calcularVentana(serie(indice), FiltroSerie.KERNEL, ventana, maxPuntos);
    }

    /**
     * Serie continua de utilización acumulada real desde el inicio hasta cada ciclo
     * ({@code 100 * ciclosOcupadosHastaT / ciclosTranscurridosHastaT}).
     */
    public double[][] utilizacionAcumulada(int indice, int maxPuntos) {
        if (maxPuntos < 2) {
            throw new IllegalArgumentException("maxPuntos debe ser >= 2.");
        }
        Serie s = serie(indice);
        int total = s.ocupada.getTamano();
        if (total == 0) {
            return new double[][]{new double[0], new double[0]};
        }
        int paso = (total + maxPuntos - 1) / maxPuntos;
        int cantidad = contarMuestras(total, paso);
        double[] x = new double[cantidad];
        double[] y = new double[cantidad];

        long acum = 0;
        int salida = 0;
        int i = 0;
        Iterador<Boolean> it = s.ocupada.iterador();
        while (it.haySiguiente()) {
            if (Boolean.TRUE.equals(it.siguiente())) {
                acum++;
            }
            boolean ultimo = (i == total - 1);
            if (i % paso == 0 || ultimo) {
                x[salida] = s.cicloInicial + i;
                y[salida] = 100.0 * acum / (i + 1);
                salida++;
            }
            i++;
        }
        return new double[][]{x, y};
    }

    private double[][] calcularVentana(Serie s, FiltroSerie filtro, int ventana, int maxPuntos) {
        if (ventana < 1 || maxPuntos < 2) {
            throw new IllegalArgumentException("La ventana debe ser >= 1 y maxPuntos >= 2.");
        }
        int total = s.ocupada.getTamano();
        if (total == 0) {
            return new double[][]{new double[0], new double[0]};
        }
        int paso = (total + maxPuntos - 1) / maxPuntos;
        int cantidad = contarMuestras(total, paso);
        double[] x = new double[cantidad];
        double[] y = new double[cantidad];

        int[] recientes = new int[ventana];
        int suma = 0;
        int salida = 0;
        int i = 0;
        Iterador<Boolean> itOcup = s.ocupada.iterador();
        Iterador<Boolean> itKern = s.enKernel.iterador();
        while (itOcup.haySiguiente()) {
            boolean estaOcupada = Boolean.TRUE.equals(itOcup.siguiente());
            boolean esKernel = Boolean.TRUE.equals(itKern.haySiguiente() ? itKern.siguiente() : Boolean.FALSE);
            int valor = valorCiclo(estaOcupada, esKernel, filtro);
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

    private static int valorCiclo(boolean estaOcupada, boolean esKernel, FiltroSerie filtro) {
        if (!estaOcupada) {
            return 0;
        }
        return switch (filtro) {
            case TOTAL -> 1;
            case KERNEL -> esKernel ? 1 : 0;
            case USUARIO -> esKernel ? 0 : 1;
        };
    }

    private static int contarMuestras(int total, int paso) {
        int cantidad = (total - 1) / paso + 1;
        if ((total - 1) % paso != 0) {
            cantidad++;
        }
        return cantidad;
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
