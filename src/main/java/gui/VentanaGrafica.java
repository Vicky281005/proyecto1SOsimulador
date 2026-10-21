package gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import simulacion.HistorialUtilizacion;
import simulacion.Simulador;

/**
 * Gráfica de utilización de CPU (requisito 8 del enunciado): un solo gráfico con una
 * línea por computador, que muestra el porcentaje de ocupación de su CPU contra el
 * número de ciclo.
 *
 * <p>Cada punto es el promedio de los últimos {@value #VENTANA_PROMEDIO} ciclos (un
 * valor ciclo a ciclo sería solo 0 % o 100 %). La gráfica se refresca con un
 * {@link javax.swing.Timer} y lee el historial dentro de
 * {@link Simulador#leerEstado(Runnable)}, igual que la Vista 2.</p>
 */
public class VentanaGrafica extends JDialog {

    /** Ciclos que entran en cada promedio móvil. */
    public static final int VENTANA_PROMEDIO = 10;
    /** Máximo de puntos por línea; con más ciclos se toma uno de cada varios. */
    private static final int MAX_PUNTOS = 1000;
    private static final int REFRESCO_MS = 500;
    /** Colores de las líneas, uno por computador (se repiten si hay más). */
    private static final Color[] COLORES = {
        new Color(31, 119, 180), new Color(255, 127, 14), new Color(44, 160, 44),
        new Color(214, 39, 40), new Color(148, 103, 189), new Color(140, 86, 75)
    };

    private final Simulador simulador;
    private final JFreeChart grafico;
    private final JLabel lblResumen = new JLabel(" ", SwingConstants.CENTER);
    private final Timer temporizador;

    /**
     * @param dueno     ventana que abre la gráfica.
     * @param simulador simulador cuyo historial de utilización se dibuja.
     */
    public VentanaGrafica(JFrame dueno, Simulador simulador) {
        super(dueno, "ÁvilaOS – Utilización de CPU por computador", false);
        this.simulador = simulador;

        grafico = ChartFactory.createXYLineChart(
                "Utilización de CPU por computador",
                "Ciclo de reloj",
                "Utilización (%) – promedio de " + VENTANA_PROMEDIO + " ciclos",
                new XYSeriesCollection(),
                PlotOrientation.VERTICAL,
                true,    // leyenda
                true,    // tooltips
                false);  // urls
        XYPlot grafica = grafico.getXYPlot();
        NumberAxis ejeY = (NumberAxis) grafica.getRangeAxis();
        ejeY.setRange(0.0, 105.0); // un poco de margen para que la línea del 100 % no quede pegada al borde

        // Fondo blanco y líneas gruesas. Cada línea es más ancha que la anterior: si dos
        // computadores coinciden en 100 %, se ven como bandas de colores en vez de taparse.
        grafica.setBackgroundPaint(Color.WHITE);
        grafica.setRangeGridlinePaint(Color.LIGHT_GRAY);
        grafica.setDomainGridlinePaint(Color.LIGHT_GRAY);
        XYLineAndShapeRenderer renderizador = new XYLineAndShapeRenderer(true, false);
        for (int i = 0; i < COLORES.length; i++) {
            renderizador.setSeriesPaint(i, COLORES[i]);
            renderizador.setSeriesStroke(i, new BasicStroke(2.0f + 1.5f * i));
        }
        grafica.setRenderer(renderizador);

        setLayout(new BorderLayout());
        add(new ChartPanel(grafico), BorderLayout.CENTER);
        add(lblResumen, BorderLayout.SOUTH);
        setSize(820, 520);
        setLocationRelativeTo(dueno);

        temporizador = new Timer(REFRESCO_MS, e -> refrescar());
        temporizador.start();
    }

    /** Vuelve a leer el historial y redibuja. No hace nada si la ventana está oculta. */
    private void refrescar() {
        if (!isVisible()) {
            return;
        }
        XYSeriesCollection[] datos = new XYSeriesCollection[1];
        String[] resumen = new String[1];
        simulador.leerEstado(() -> {
            HistorialUtilizacion h = simulador.getHistorialUtilizacion();
            XYSeriesCollection coleccion = new XYSeriesCollection();
            StringBuilder texto = new StringBuilder("Utilización total:  ");
            for (int i = 0; i < h.getCantidadSeries(); i++) {
                double[][] puntos = h.utilizacion(i, VENTANA_PROMEDIO, MAX_PUNTOS);
                XYSeries serie = new XYSeries(h.getNombre(i));
                for (int k = 0; k < puntos[0].length; k++) {
                    serie.add(puntos[0][k], puntos[1][k]);
                }
                coleccion.addSeries(serie);
                texto.append(h.getNombre(i)).append(" ")
                        .append(String.format("%.1f", h.getUtilizacionTotal(i))).append(" %    ");
            }
            datos[0] = coleccion;
            resumen[0] = h.getCantidadSeries() == 0 ? "Sin datos: inicia la simulación." : texto.toString();
        });
        // El conjunto de datos se arma completo y se cambia de una vez (una sola notificación).
        grafico.getXYPlot().setDataset(datos[0]);
        lblResumen.setText(resumen[0]);
    }

    @Override
    public void dispose() {
        temporizador.stop();
        super.dispose();
    }
}
