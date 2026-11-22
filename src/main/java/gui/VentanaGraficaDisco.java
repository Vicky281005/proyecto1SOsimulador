package gui;

import disco.DiscoSimulado;
import estructuras.ListaEnlazada;
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
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import simulacion.SerieDesplazamiento;
import simulacion.Simulador;

/**
 * Gráfica JFreeChart del <b>desplazamiento acumulado del cabezal</b> del disco contra el
 * número de ciclo. Se refresca con un {@link javax.swing.Timer} y lee una copia del
 * historial del disco (que se toma bajo el mutex del propio disco).
 */
public class VentanaGraficaDisco extends JDialog {

    private static final int MAX_PUNTOS = 1000;
    private static final int REFRESCO_MS = 500;

    private final Simulador simulador;
    private final JFreeChart grafico;
    private final JLabel lblResumen = new JLabel(" ", SwingConstants.CENTER);
    private final Timer temporizador;

    /**
     * @param dueno     ventana que abre la gráfica.
     * @param simulador simulador cuyo disco se dibuja.
     */
    public VentanaGraficaDisco(JFrame dueno, Simulador simulador) {
        super(dueno, "ÁvilaOS – Desplazamiento acumulado del cabezal", false);
        this.simulador = simulador;

        grafico = ChartFactory.createXYLineChart(
                "Desplazamiento acumulado del cabezal vs. tiempo",
                "Ciclo de reloj",
                "Bloques recorridos (acumulado)",
                new XYSeriesCollection(),
                PlotOrientation.VERTICAL,
                false,   // una sola serie: no hace falta leyenda
                true,    // tooltips
                false);  // urls
        XYPlot grafica = grafico.getXYPlot();
        grafica.setBackgroundPaint(Color.WHITE);
        grafica.setRangeGridlinePaint(Color.LIGHT_GRAY);
        grafica.setDomainGridlinePaint(Color.LIGHT_GRAY);
        XYLineAndShapeRenderer renderizador = new XYLineAndShapeRenderer(true, false);
        renderizador.setSeriesPaint(0, new Color(31, 119, 180));
        renderizador.setSeriesStroke(0, new BasicStroke(2.5f));
        grafica.setRenderer(renderizador);

        setLayout(new BorderLayout());
        add(new ChartPanel(grafico), BorderLayout.CENTER);
        add(lblResumen, BorderLayout.SOUTH);
        setSize(820, 520);
        setLocationRelativeTo(dueno);

        temporizador = new Timer(REFRESCO_MS, e -> refrescar());
        temporizador.start();
    }

    /** Vuelve a leer el historial del disco y redibuja. No hace nada si la ventana está oculta. */
    private void refrescar() {
        if (!isVisible()) {
            return;
        }
        DiscoSimulado disco = simulador.getDisco();
        if (disco == null) {
            lblResumen.setText("No hay disco configurado en este simulador.");
            return;
        }
        ListaEnlazada<Long> historial = disco.copiarHistorialDesplazamiento();
        double[][] puntos = SerieDesplazamiento.muestrear(historial, MAX_PUNTOS);
        XYSeries serie = new XYSeries("Desplazamiento acumulado");
        for (int k = 0; k < puntos[0].length; k++) {
            serie.add(puntos[0][k], puntos[1][k]);
        }
        XYSeriesCollection coleccion = new XYSeriesCollection();
        coleccion.addSeries(serie);
        // El conjunto se arma completo y se cambia de una vez (una sola notificación).
        grafico.getXYPlot().setDataset(coleccion);
        lblResumen.setText(puntos[0].length == 0
                ? "Sin datos: inicia la simulación."
                : String.format("Desplazamiento total: %d bloques en %d ciclos  |  política: %s",
                        disco.getDesplazamientoTotal(), historial.getTamano(),
                        disco.getPoliticaActual().getCodigoJson()));
    }

    @Override
    public void dispose() {
        temporizador.stop();
        super.dispose();
    }
}
