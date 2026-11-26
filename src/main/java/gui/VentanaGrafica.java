package gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import simulacion.HistorialUtilizacion;
import simulacion.Simulador;

/**
 * Gráfica detallada de utilización de CPU por computador.
 *
 * <p>Permite alternar entre tres vistas limpias:
 * <ul>
 *   <li><b>Detallada:</b> Tendencia reciente (ventana de 5 ciclos) junto con la utilización acumulada real (%).</li>
 *   <li><b>Modos de ejecución:</b> Desglose entre ejecución en Modo Usuario vs. Modo Kernel (E/S).</li>
 *   <li><b>Clásica:</b> Promedio móvil de {@value #VENTANA_PROMEDIO} ciclos.</li>
 * </ul>
 * </p>
 */
public class VentanaGrafica extends JDialog {

    /** Ciclos base usados en el modo clásico de promedio móvil. */
    public static final int VENTANA_PROMEDIO = 10;
    private static final int VENTANA_DETALLADA = 5;
    private static final int MAX_PUNTOS = 1000;
    private static final int REFRESCO_MS = 450;

    private static final String VISTA_DETALLADA = "Detallada: Tendencia (5 ciclos) + Acumulada real";
    private static final String VISTA_MODOS = "Modos de ejecución: Usuario vs. Kernel (E/S)";
    private static final String VISTA_CLASICA = "Clásica: Promedio móvil (" + VENTANA_PROMEDIO + " ciclos)";

    private static final Color[] COLORES = {
        new Color(31, 119, 180), new Color(44, 160, 44), new Color(214, 39, 40),
        new Color(255, 127, 14), new Color(148, 103, 189), new Color(140, 86, 75)
    };

    private final Simulador simulador;
    private final JFreeChart grafico;
    private final ChartPanel panelGrafico;
    private final JComboBox<String> comboVista = new JComboBox<>(
            new String[]{VISTA_DETALLADA, VISTA_MODOS, VISTA_CLASICA});
    private final JLabel lblResumen = new JLabel(" ", SwingConstants.CENTER);
    private final Timer temporizador;
    private long ultimoCicloPintado = -1;
    private int ultimaCantidadSeries = -1;
    private String ultimaVistaPintada = null;

    /**
     * @param dueno     ventana que abre la gráfica.
     * @param simulador simulador cuyo historial de utilización se dibuja.
     */
    public VentanaGrafica(JFrame dueno, Simulador simulador) {
        super(dueno, "ÁvilaOS – Utilización detallada de CPU", false);
        this.simulador = simulador;
        setResizable(true);

        grafico = ChartFactory.createXYLineChart(
                "Utilización de CPU",
                "Ciclo de reloj",
                "Utilización (%)",
                new XYSeriesCollection(),
                PlotOrientation.VERTICAL,
                true,
                true,
                false);

        XYPlot grafica = grafico.getXYPlot();
        NumberAxis ejeY = (NumberAxis) grafica.getRangeAxis();
        ejeY.setRange(-2.0, 105.0);
        ejeY.setNumberFormatOverride(new DecimalFormat("0.0'%'"));
        NumberAxis ejeX = (NumberAxis) grafica.getDomainAxis();
        ejeX.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        grafica.setBackgroundPaint(Color.WHITE);
        grafica.setRangeGridlinePaint(new Color(225, 225, 225));
        grafica.setDomainGridlinePaint(new Color(225, 225, 225));

        panelGrafico = new ChartPanel(grafico);
        panelGrafico.setMouseWheelEnabled(true);

        JPanel barraSuperior = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        barraSuperior.add(new JLabel("Vista:"));
        barraSuperior.add(comboVista);
        comboVista.addActionListener(e -> refrescar());
        JButton btnZoom = new JButton("Restaurar zoom");
        btnZoom.addActionListener(e -> panelGrafico.restoreAutoBounds());
        barraSuperior.add(btnZoom);

        lblResumen.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));
        lblResumen.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        setLayout(new BorderLayout(4, 4));
        add(barraSuperior, BorderLayout.NORTH);
        add(panelGrafico, BorderLayout.CENTER);
        add(lblResumen, BorderLayout.SOUTH);
        setMinimumSize(new Dimension(740, 460));
        setSize(900, 540);
        setLocationRelativeTo(dueno);

        temporizador = new Timer(REFRESCO_MS, e -> refrescar());
        temporizador.start();
        refrescar();
    }

    /** Vuelve a leer el historial y redibuja según la vista seleccionada. */
    public final void refrescar() {
        if (!isVisible() && grafico.getXYPlot().getDataset().getSeriesCount() > 0) {
            return;
        }
        String vista = (String) comboVista.getSelectedItem();
        XYSeriesCollection[] datos = new XYSeriesCollection[1];
        String[] resumen = new String[1];
        boolean[] sinCambios = new boolean[1];
        long[] cicloCapturado = new long[1];
        int[] seriesCapturadas = new int[1];

        simulador.leerEstado(() -> {
            HistorialUtilizacion h = simulador.getHistorialUtilizacion();
            long ciclo = simulador.getCicloActual();
            int nSeries = h.getCantidadSeries();
            if (ciclo == ultimoCicloPintado && nSeries == ultimaCantidadSeries
                    && vista != null && vista.equals(ultimaVistaPintada)) {
                sinCambios[0] = true;
                return;
            }
            cicloCapturado[0] = ciclo;
            seriesCapturadas[0] = nSeries;

            XYSeriesCollection coleccion = new XYSeriesCollection();
            StringBuilder texto = new StringBuilder();

            for (int i = 0; i < nSeries; i++) {
                String prefijo = (nSeries > 1) ? (h.getNombre(i) + " – ") : "";
                if (VISTA_MODOS.equals(vista)) {
                    agregarSerie(coleccion, prefijo + "Modo Usuario (%)",
                            h.utilizacionUsuario(i, VENTANA_DETALLADA, MAX_PUNTOS));
                    agregarSerie(coleccion, prefijo + "Modo Kernel / E/S (%)",
                            h.utilizacionKernel(i, VENTANA_DETALLADA, MAX_PUNTOS));
                } else if (VISTA_CLASICA.equals(vista)) {
                    agregarSerie(coleccion, h.getNombre(i) + " (ventana " + VENTANA_PROMEDIO + "c)",
                            h.utilizacion(i, VENTANA_PROMEDIO, MAX_PUNTOS));
                } else {
                    agregarSerie(coleccion, prefijo + "Tendencia (" + VENTANA_DETALLADA + " ciclos)",
                            h.utilizacion(i, VENTANA_DETALLADA, MAX_PUNTOS));
                    agregarSerie(coleccion, prefijo + "Acumulada real (%)",
                            h.utilizacionAcumulada(i, MAX_PUNTOS));
                }

                int totalCiclos = h.getCantidadCiclos(i);
                long ocupados = h.getCiclosOcupados(i);
                long usuario = h.getCiclosUsuario(i);
                long kernel = h.getCiclosKernel(i);
                long ociosos = Math.max(0L, totalCiclos - ocupados);
                double pctTotal = h.getUtilizacionTotal(i);
                double pctUsuario = h.getUtilizacionUsuario(i);
                double pctKernel = h.getUtilizacionKernel(i);
                double pctOciosa = totalCiclos == 0 ? 0.0 : (100.0 * ociosos / totalCiclos);

                if (i > 0) {
                    texto.append("   ||   ");
                }
                texto.append(String.format(
                        "%s — Total: %.1f %% (%d/%d c)  |  Usuario: %.1f %% (%d)  |  Kernel/E/S: %.1f %% (%d)  |  Ociosa: %.1f %% (%d)",
                        h.getNombre(i), pctTotal, ocupados, totalCiclos,
                        pctUsuario, usuario, pctKernel, kernel, pctOciosa, ociosos));
            }

            datos[0] = coleccion;
            resumen[0] = nSeries == 0
                    ? "Sin datos todavía: inicia la simulación para observar el detalle por ciclo."
                    : texto.toString();
        });

        if (sinCambios[0]) {
            return;
        }
        ultimoCicloPintado = cicloCapturado[0];
        ultimaCantidadSeries = seriesCapturadas[0];
        ultimaVistaPintada = vista;

        XYPlot grafica = grafico.getXYPlot();
        XYLineAndShapeRenderer renderizador = new XYLineAndShapeRenderer(true, false);
        renderizador.setDefaultToolTipGenerator(new StandardXYToolTipGenerator(
                "{0}: Ciclo {1} → {2} %",
                NumberFormat.getIntegerInstance(),
                new DecimalFormat("0.00")));
        for (int idx = 0; idx < datos[0].getSeriesCount(); idx++) {
            String clave = String.valueOf(datos[0].getSeriesKey(idx));
            renderizador.setSeriesPaint(idx, colorPara(clave, idx));
            renderizador.setSeriesStroke(idx, new BasicStroke(2.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        }
        grafica.setRenderer(renderizador);
        grafica.setDataset(datos[0]);
        lblResumen.setText(resumen[0]);
    }

    private static void agregarSerie(XYSeriesCollection coleccion, String titulo, double[][] puntos) {
        XYSeries serie = new XYSeries(titulo);
        for (int k = 0; k < puntos[0].length; k++) {
            serie.add(puntos[0][k], puntos[1][k]);
        }
        coleccion.addSeries(serie);
    }

    private static Color colorPara(String clave, int idx) {
        if (clave.contains("Acumulada real")) {
            return new Color(44, 160, 44);
        }
        if (clave.contains("Modo Usuario")) {
            return new Color(44, 160, 44);
        }
        if (clave.contains("Modo Kernel")) {
            return new Color(214, 39, 40);
        }
        if (clave.contains("Tendencia")) {
            return new Color(31, 119, 180);
        }
        return COLORES[idx % COLORES.length];
    }

    @Override
    public void dispose() {
        temporizador.stop();
        super.dispose();
    }
}
