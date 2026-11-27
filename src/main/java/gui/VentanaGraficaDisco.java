package gui;

import disco.DiscoSimulado;
import enums.PoliticaDisco;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
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
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import simulacion.SerieDesplazamiento;
import simulacion.Simulador;

/**
 * Gráfica JFreeChart del <b>desplazamiento acumulado del cabezal vs. tiempo</b> del disco,
 * con marcadores verticales en los ciclos donde cambia la política de planificación en caliente
 * y métricas detalladas en la barra inferior.
 */
public class VentanaGraficaDisco extends JDialog {

    private static final int MAX_PUNTOS = 1000;
    private static final int REFRESCO_MS = 500;
    private static final Color COLOR_ACUMULADO = new Color(31, 119, 180);

    private final Simulador simulador;
    private final JFreeChart grafico;
    private final ChartPanel panelGrafico;
    private final JLabel lblResumen = new JLabel(" ", SwingConstants.CENTER);
    private final Timer temporizador;
    private long ultimoCicloPintado = -1;
    private int ultimoTamanoHistorial = -1;
    private PoliticaDisco ultimaPoliticaPintada = null;
    private long ultimoTotalAtendidasPintado = -1;

    /**
     * @param dueno     ventana que abre la gráfica.
     * @param simulador simulador cuyo disco se dibuja.
     */
    public VentanaGraficaDisco(JFrame dueno, Simulador simulador) {
        super((JFrame) null, "ÁvilaOS – Desplazamiento acumulado del cabezal", false);
        this.simulador = simulador;
        setResizable(true);

        grafico = ChartFactory.createXYLineChart(
                "Desplazamiento acumulado del cabezal vs. tiempo",
                "Ciclo de reloj",
                "Bloques recorridos (acumulado)",
                new XYSeriesCollection(),
                PlotOrientation.VERTICAL,
                false,
                true,
                false);

        XYPlot plot = grafico.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(new Color(225, 225, 225));
        plot.setDomainGridlinePaint(new Color(225, 225, 225));

        NumberAxis ejeX = (NumberAxis) plot.getDomainAxis();
        ejeX.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        NumberAxis ejeY = (NumberAxis) plot.getRangeAxis();
        ejeY.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        XYLineAndShapeRenderer rend = new XYLineAndShapeRenderer(true, false);
        rend.setSeriesPaint(0, COLOR_ACUMULADO);
        rend.setSeriesStroke(0, new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        rend.setDefaultToolTipGenerator(new StandardXYToolTipGenerator(
                "Ciclo {1} → {2} bloques acumulados",
                NumberFormat.getIntegerInstance(),
                new DecimalFormat("#,##0")));
        plot.setRenderer(rend);

        panelGrafico = new ChartPanel(grafico);
        panelGrafico.setMouseWheelEnabled(true);

        JPanel barraSuperior = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        JButton btnZoom = new JButton("Restaurar zoom");
        btnZoom.addActionListener(e -> panelGrafico.restoreAutoBounds());
        barraSuperior.add(btnZoom);

        lblResumen.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));
        lblResumen.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        setLayout(new BorderLayout(4, 4));
        add(barraSuperior, BorderLayout.NORTH);
        add(panelGrafico, BorderLayout.CENTER);
        add(lblResumen, BorderLayout.SOUTH);
        setMinimumSize(new Dimension(740, 440));
        setSize(900, 540);
        setLocationRelativeTo(dueno);

        temporizador = new Timer(REFRESCO_MS, e -> refrescar());
        temporizador.start();
        refrescar();
    }

    /** Vuelve a leer el historial del disco y redibuja la serie junto con los cambios de política. */
    public final void refrescar() {
        XYPlot plot = grafico.getXYPlot();
        if (!isVisible() && plot.getDataset().getSeriesCount() > 0) {
            return;
        }
        DiscoSimulado[] discoRef = new DiscoSimulado[1];
        @SuppressWarnings("unchecked")
        ListaEnlazada<Long>[] histDespRef = new ListaEnlazada[1];
        @SuppressWarnings("unchecked")
        ListaEnlazada<PoliticaDisco>[] histPolRef = new ListaEnlazada[1];
        long[] atendidasRef = new long[1];
        long[] despTotalRef = new long[1];
        boolean[] subeRef = new boolean[1];
        int[] cabezalRef = new int[1];
        double[] esperaPromRef = new double[1];
        PoliticaDisco[] todas = PoliticaDisco.values();
        long[] porPoliticaRef = new long[todas.length];
        boolean[] sinCambios = new boolean[1];
        long[] cicloCapturado = new long[1];
        int[] tamHistCapturado = new int[1];
        PoliticaDisco[] politicaCapturada = new PoliticaDisco[1];

        simulador.leerEstado(() -> {
            DiscoSimulado disco = simulador.getDisco();
            discoRef[0] = disco;
            if (disco == null) {
                return;
            }
            ListaEnlazada<Long> histDesp = disco.copiarHistorialDesplazamiento();
            int tamHist = histDesp.getTamano();
            long ciclo = simulador.getCicloActual();
            PoliticaDisco polActual = disco.getPoliticaActual();
            long atendidas = disco.getTotalSolicitudesAtendidas();

            if (ciclo == ultimoCicloPintado && tamHist == ultimoTamanoHistorial
                    && polActual == ultimaPoliticaPintada && atendidas == ultimoTotalAtendidasPintado) {
                sinCambios[0] = true;
                return;
            }

            cicloCapturado[0] = ciclo;
            tamHistCapturado[0] = tamHist;
            politicaCapturada[0] = polActual;
            histDespRef[0] = histDesp;
            histPolRef[0] = disco.copiarHistorialPolitica();
            atendidasRef[0] = atendidas;
            despTotalRef[0] = disco.getDesplazamientoTotal();
            subeRef[0] = disco.isDireccionAscendente();
            cabezalRef[0] = disco.getPosicionCabezal();
            esperaPromRef[0] = disco.getTiempoEsperaPromedio();
            for (int i = 0; i < todas.length; i++) {
                porPoliticaRef[i] = disco.getSolicitudesAtendidas(todas[i]);
            }
        });

        if (sinCambios[0]) {
            return;
        }
        if (discoRef[0] == null) {
            lblResumen.setText("No hay disco configurado en este simulador.");
            return;
        }
        ultimoCicloPintado = cicloCapturado[0];
        ultimoTamanoHistorial = tamHistCapturado[0];
        ultimaPoliticaPintada = politicaCapturada[0];
        ultimoTotalAtendidasPintado = atendidasRef[0];

        double[][] ptsDesp = SerieDesplazamiento.muestrear(histDespRef[0], MAX_PUNTOS);
        ListaEnlazada<SerieDesplazamiento.CambioPolitica> cambios =
                SerieDesplazamiento.detectarCambiosPolitica(histPolRef[0]);

        XYSeries serieAcum = new XYSeries("Desplazamiento acumulado");
        for (int k = 0; k < ptsDesp[0].length; k++) {
            serieAcum.add(ptsDesp[0][k], ptsDesp[1][k]);
        }
        XYSeriesCollection colAcum = new XYSeriesCollection();
        colAcum.addSeries(serieAcum);

        actualizarMarcadoresPolitica(plot, cambios);
        plot.setDataset(colAcum);

        if (ptsDesp[0].length == 0) {
            lblResumen.setText("Sin datos todavía: inicia la simulación para observar el desplazamiento del cabezal.");
            return;
        }

        long atendidas = atendidasRef[0];
        long despTotal = despTotalRef[0];
        double promPorSol = (atendidas > 0) ? ((double) despTotal / atendidas) : 0.0;
        String dir = subeRef[0] ? "↑" : "↓";

        StringBuilder porPol = new StringBuilder();
        for (int i = 0; i < todas.length; i++) {
            if (i > 0) {
                porPol.append(' ');
            }
            porPol.append(todas[i].getCodigoJson()).append('=').append(porPoliticaRef[i]);
        }

        lblResumen.setText(String.format(
                "Cabezal: blq %d (%s)  |  Atendidas: %d  |  Desplazamiento: %d blq (prom. %.1f blq/sol)  |  Espera prom.: %.1f c  |  %s",
                cabezalRef[0], dir, atendidas, despTotal, promPorSol,
                esperaPromRef[0], porPol));
    }

    private void actualizarMarcadoresPolitica(XYPlot plot, ListaEnlazada<SerieDesplazamiento.CambioPolitica> cambios) {
        plot.clearDomainMarkers();
        if (cambios == null || cambios.estaVacia()) {
            return;
        }
        BasicStroke trazoPunteado = new BasicStroke(
                1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{5.0f, 4.0f}, 0.0f);
        Font fuenteEtiq = new Font(Font.SANS_SERIF, Font.BOLD, 11);

        int idx = 0;
        Iterador<SerieDesplazamiento.CambioPolitica> it = cambios.iterador();
        while (it.haySiguiente()) {
            SerieDesplazamiento.CambioPolitica cp = it.siguiente();
            Color col = colorDePolitica(cp.politica);

            ValueMarker marker = new ValueMarker(cp.ciclo, col, trazoPunteado);
            marker.setLabel(idx == 0
                    ? cp.politica.getCodigoJson()
                    : "c." + cp.ciclo + " → " + cp.politica.getCodigoJson());
            marker.setLabelFont(fuenteEtiq);
            marker.setLabelPaint(col);
            marker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
            marker.setLabelTextAnchor(TextAnchor.TOP_LEFT);
            marker.setLabelOffset(new RectangleInsets(4, 4, 2, 2));
            plot.addDomainMarker(marker);
            idx++;
        }
    }

    private static Color colorDePolitica(PoliticaDisco p) {
        if (p == null) {
            return Color.DARK_GRAY;
        }
        return switch (p) {
            case FIFO -> new Color(52, 73, 94);
            case SSTF -> new Color(39, 174, 96);
            case SCAN -> new Color(142, 68, 173);
            case C_SCAN -> new Color(192, 57, 43);
        };
    }

    @Override
    public void dispose() {
        temporizador.stop();
        super.dispose();
    }
}
