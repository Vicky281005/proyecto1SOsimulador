package gui;

import disco.DiscoSimulado;
import disco.SolicitudES;
import enums.PoliticaDisco;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import simulacion.Simulador;

/**
 * Vista del disco: mapa de bloques con el cabezal, la solicitud en curso y la cola de
 * espera, estadísticas del planificador y control en caliente de la política y de la
 * velocidad del cabezal.
 *
 * <p>Se refresca con un {@link javax.swing.Timer} de Swing y lee el disco solo con sus
 * métodos públicos (que usan su propio mutex), nunca sus listas internas.</p>
 */
public class VentanaDisco extends JDialog {

    private static final int REFRESCO_MS = 250;

    private final Simulador simulador;
    private final PanelMapaDisco mapa = new PanelMapaDisco();
    private final JComboBox<PoliticaDisco> comboPolitica = new JComboBox<>(PoliticaDisco.values());
    private final JSpinner spinVelocidad = new JSpinner(new SpinnerNumberModel(
            DiscoSimulado.VELOCIDAD_CABEZAL_POR_DEFECTO, 1, 1000, 1));
    private final JLabel lblCabezal = new JLabel();
    private final JLabel lblActual = new JLabel();
    private final JLabel lblEspacio = new JLabel();
    private final JLabel lblEstadisticas = new JLabel();
    private final JLabel lblPorPolitica = new JLabel();
    private final JTextArea areaCola = new JTextArea(10, 34);
    private final Timer temporizador;
    private boolean actualizando;

    /**
     * @param dueno     ventana que la abre.
     * @param simulador simulador cuyo disco se muestra (puede no tener disco).
     */
    public VentanaDisco(JFrame dueno, Simulador simulador) {
        super(dueno, "ÁvilaOS – Disco y planificación de E/S", false);
        this.simulador = simulador;

        JPanel control = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        control.add(new JLabel("Política:"));
        control.add(comboPolitica);
        control.add(new JLabel("Bloques por ciclo del cabezal:"));
        control.add(spinVelocidad);
        comboPolitica.addActionListener(e -> {
            DiscoSimulado d = simulador.getDisco();
            if (!actualizando && d != null) {
                d.setPolitica((PoliticaDisco) comboPolitica.getSelectedItem());
            }
        });
        spinVelocidad.addChangeListener(e -> {
            DiscoSimulado d = simulador.getDisco();
            if (!actualizando && d != null) {
                d.setVelocidadCabezal((Integer) spinVelocidad.getValue());
            }
        });

        JPanel datos = new JPanel(new GridLayout(0, 1, 0, 2));
        datos.setBorder(BorderFactory.createTitledBorder("Estado del disco"));
        datos.add(lblCabezal);
        datos.add(lblActual);
        datos.add(lblEspacio);
        datos.add(lblEstadisticas);
        datos.add(lblPorPolitica);

        areaCola.setEditable(false);
        areaCola.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollCola = new JScrollPane(areaCola);
        scrollCola.setBorder(BorderFactory.createTitledBorder("Cola de solicitudes (en espera)"));

        JPanel derecha = new JPanel(new BorderLayout(0, 6));
        derecha.add(datos, BorderLayout.NORTH);
        derecha.add(scrollCola, BorderLayout.CENTER);

        JPanel centro = new JPanel(new BorderLayout());
        centro.setBorder(BorderFactory.createTitledBorder("Mapa de bloques"));
        centro.add(mapa, BorderLayout.CENTER);

        setLayout(new BorderLayout(6, 6));
        add(control, BorderLayout.NORTH);
        add(centro, BorderLayout.CENTER);
        add(derecha, BorderLayout.EAST);
        add(leyenda(), BorderLayout.SOUTH);
        refrescar(); // primera lectura antes de pack(): así la ventana mide el mapa completo
        pack();
        setLocationRelativeTo(dueno);

        temporizador = new Timer(REFRESCO_MS, e -> refrescar());
        temporizador.start();
    }

    private static JPanel leyenda() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        p.add(muestra(PanelMapaDisco.COLOR_LIBRE, "Libre"));
        p.add(muestra(PanelMapaDisco.COLOR_ARCHIVO_DEFECTO, "Archivo (color propio)"));
        p.add(muestra(PanelMapaDisco.COLOR_SWAP_LIBRE, "Swap libre"));
        p.add(muestra(PanelMapaDisco.COLOR_SWAP_OCUPADO, "Swap ocupado"));
        p.add(new JLabel("Rojo: cabezal   Azul: solicitud en curso   ●: solicitud en espera"));
        return p;
    }

    private static JLabel muestra(Color c, String texto) {
        JLabel l = new JLabel(texto);
        l.setIcon(new javax.swing.Icon() {
            public int getIconWidth() { return 14; }
            public int getIconHeight() { return 14; }
            public void paintIcon(java.awt.Component comp, java.awt.Graphics g, int x, int y) {
                g.setColor(c);
                g.fillRect(x, y, 14, 14);
                g.setColor(Color.GRAY);
                g.drawRect(x, y, 13, 13);
            }
        });
        return l;
    }

    /** Vuelve a leer el disco y redibuja. No hace nada si la ventana está oculta. */
    void refrescar() {
        if (!isVisible() && !lblCabezal.getText().isEmpty()) {
            return;
        }
        DiscoSimulado d = simulador.getDisco();
        if (d == null) {
            lblCabezal.setText("No hay disco configurado en este simulador.");
            lblActual.setText(" ");
            lblEspacio.setText(" ");
            lblEstadisticas.setText(" ");
            lblPorPolitica.setText(" ");
            areaCola.setText("");
            comboPolitica.setEnabled(false);
            spinVelocidad.setEnabled(false);
            return;
        }
        comboPolitica.setEnabled(true);
        spinVelocidad.setEnabled(true);

        int cabezal = d.getPosicionCabezal();
        boolean sube = d.isDireccionAscendente();
        SolicitudES actual = d.getSolicitudActual();
        PoliticaDisco politica = d.getPoliticaActual();
        int velocidad = d.getVelocidadCabezal();

        StringBuilder cola = new StringBuilder();
        int[] destinos = new int[d.getCantidadEnColaES()];
        int n = 0;
        Iterador<SolicitudES> it = d.iteradorColaES();
        while (it.haySiguiente()) {
            SolicitudES s = it.siguiente();
            if (n < destinos.length) {
                destinos[n] = s.getBloqueDestino();
            }
            n++;
            cola.append(String.format("%2d. %s  (d=%d)%n", n, s, Math.abs(s.getBloqueDestino() - cabezal)));
        }
        if (n == 0) {
            cola.append("(vacía)");
        }
        if (n < destinos.length) {
            // La cola cambió entre contar e iterar: se recorta el arreglo a lo realmente leído.
            int[] recortado = new int[n];
            for (int i = 0; i < n; i++) {
                recortado[i] = destinos[i];
            }
            destinos = recortado;
        }

        ListaEnlazada<disco.BloqueDisco> copia = d.copiarBloques();
        mapa.actualizar(copia, cabezal, actual == null ? -1 : actual.getBloqueDestino(),
                actual == null ? 0 : actual.getCantidadBloques(), destinos);

        actualizando = true;
        try {
            if (comboPolitica.getSelectedItem() != politica) {
                comboPolitica.setSelectedItem(politica);
            }
            if (!spinVelocidad.getValue().equals(velocidad)) {
                spinVelocidad.setValue(velocidad);
            }
        } finally {
            actualizando = false;
        }

        lblCabezal.setText("Cabezal en el bloque " + cabezal + (sube ? "  (sentido ascendente)" : "  (sentido descendente)"));
        lblActual.setText("En atención: " + (actual == null ? "ninguna" : actual
                + " (" + actual.getBloquesRestantesTransferencia() + " bloque(s) por transferir)"));
        lblEspacio.setText(String.format("Libres: %d de %d bloques de datos  |  swap libre: %d de %d",
                d.getBloquesLibresDatos(), d.getTotalBloquesDatos(), d.getBloquesLibresSwap(), d.getTamanoSwap()));
        lblEstadisticas.setText(String.format("Atendidas: %d  |  desplazamiento total: %d bloques  |  espera prom.: %.1f ciclos",
                d.getTotalSolicitudesAtendidas(), d.getDesplazamientoTotal(), d.getTiempoEsperaPromedio()));
        StringBuilder pp = new StringBuilder("Por política: ");
        for (PoliticaDisco p : PoliticaDisco.values()) {
            pp.append(p.getCodigoJson()).append('=').append(d.getSolicitudesAtendidas(p)).append("  ");
        }
        lblPorPolitica.setText(pp.toString());
        areaCola.setText(cola.toString());
        areaCola.setCaretPosition(0);
    }

    /** Texto de la cola mostrada (para las pruebas). */
    String getTextoCola() {
        return areaCola.getText();
    }

    String getTextoCabezal() {
        return lblCabezal.getText();
    }

    PanelMapaDisco getMapa() {
        return mapa;
    }

    @Override
    public void dispose() {
        temporizador.stop();
        super.dispose();
    }
}
