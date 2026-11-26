package gui;

import disco.DiscoSimulado;
import disco.SolicitudES;
import enums.ModoUsuario;
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
import javax.swing.text.NumberFormatter;
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
    private ModoUsuario modoUsuario = ModoUsuario.ADMINISTRADOR;

    /**
     * @param dueno     ventana que la abre.
     * @param simulador simulador cuyo disco se muestra (puede no tener disco).
     */
    public VentanaDisco(JFrame dueno, Simulador simulador) {
        super((JFrame) null, "ÁvilaOS – Disco y planificación de E/S", false);
        this.simulador = simulador;
        setResizable(true);

        if (spinVelocidad.getEditor() instanceof JSpinner.NumberEditor ne
                && ne.getTextField().getFormatter() instanceof NumberFormatter nf) {
            nf.setAllowsInvalid(false);
            nf.setCommitsOnValidEdit(true);
        }

        JPanel control = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        control.add(new JLabel("Política:"));
        control.add(comboPolitica);
        control.add(new JLabel("Bloques por ciclo del cabezal:"));
        control.add(spinVelocidad);
        comboPolitica.addActionListener(e -> {
            DiscoSimulado d = simulador.getDisco();
            if (!actualizando && d != null && modoUsuario.esAdministrador()) {
                d.setPolitica((PoliticaDisco) comboPolitica.getSelectedItem());
            }
        });
        spinVelocidad.addChangeListener(e -> {
            DiscoSimulado d = simulador.getDisco();
            if (!actualizando && d != null && modoUsuario.esAdministrador()) {
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
        centro.setBorder(BorderFactory.createTitledBorder("Mapa de bloques (pase el cursor para ver el archivo)"));
        JScrollPane scrollMapa = new JScrollPane(mapa);
        scrollMapa.setBorder(null);
        centro.add(scrollMapa, BorderLayout.CENTER);

        setLayout(new BorderLayout(6, 6));
        add(control, BorderLayout.NORTH);
        add(centro, BorderLayout.CENTER);
        add(derecha, BorderLayout.EAST);
        add(leyenda(), BorderLayout.SOUTH);
        refrescar(); // primera lectura antes de pack(): así la ventana mide el mapa completo
        pack();
        java.awt.Dimension tam = getSize();
        java.awt.Rectangle usable = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int maxW = Math.max(760, Math.min(1180, usable.width - 40));
        int maxH = Math.max(520, Math.min(760, usable.height - 40));
        setSize(Math.min(tam.width, maxW), Math.min(tam.height, maxH));
        setLocationRelativeTo(dueno);

        temporizador = new Timer(REFRESCO_MS, e -> refrescar());
        temporizador.start();
    }

    private static JPanel leyenda() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        p.add(muestra(PanelMapaDisco.COLOR_LIBRE, "Libre"));
        p.add(muestraMultiColor("Archivos (1 color por archivo)"));
        p.add(muestra(PanelMapaDisco.COLOR_SWAP_LIBRE, "Swap libre"));
        p.add(muestra(PanelMapaDisco.COLOR_SWAP_OCUPADO, "Swap ocupado"));
        p.add(new JLabel("Borde rojo: cabezal   Borde azul: en curso   ●: en espera"));
        return p;
    }

    private static JLabel muestraMultiColor(String texto) {
        JLabel l = new JLabel(texto);
        l.setIcon(new javax.swing.Icon() {
            public int getIconWidth() { return 28; }
            public int getIconHeight() { return 14; }
            public void paintIcon(java.awt.Component comp, java.awt.Graphics g, int x, int y) {
                Color[] cols = {
                    new Color(0x3498DB), new Color(0x2ECC71), new Color(0x9B59B6), new Color(0xE84393)
                };
                for (int i = 0; i < cols.length; i++) {
                    g.setColor(cols[i]);
                    g.fillRect(x + i * 7, y, 7, 14);
                }
                g.setColor(Color.GRAY);
                g.drawRect(x, y, 27, 13);
            }
        });
        return l;
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
        DiscoSimulado[] refDisco = new DiscoSimulado[1];
        int[] cabezalRef = new int[1];
        boolean[] subeRef = new boolean[1];
        int[] actualDestinoRef = new int[]{-1};
        int[] actualBloquesRef = new int[1];
        String[] actualTextoRef = new String[]{"ninguna"};
        PoliticaDisco[] politicaRef = new PoliticaDisco[1];
        int[] velocidadRef = new int[1];
        String[] colaTextoRef = new String[1];
        int[][] destinosRef = new int[1][];
        @SuppressWarnings("unchecked")
        ListaEnlazada<disco.BloqueDisco>[] copiaRef = new ListaEnlazada[1];
        int[] espacioRef = new int[4]; // libresDatos, totalDatos, libresSwap, tamSwap
        long[] statsLongRef = new long[2]; // atendidas, despTotal
        double[] esperaPromRef = new double[1];
        PoliticaDisco[] todasPoliticas = PoliticaDisco.values();
        long[] porPoliticaRef = new long[todasPoliticas.length];

        simulador.leerEstado(() -> {
            DiscoSimulado d = simulador.getDisco();
            refDisco[0] = d;
            if (d == null) {
                return;
            }
            int cab = d.getPosicionCabezal();
            cabezalRef[0] = cab;
            subeRef[0] = d.isDireccionAscendente();
            SolicitudES act = d.getSolicitudActual();
            if (act != null) {
                actualDestinoRef[0] = act.getBloqueDestino();
                actualBloquesRef[0] = act.getCantidadBloques();
                actualTextoRef[0] = act + " (" + act.getBloquesRestantesTransferencia() + " bloque(s) por transferir)";
            }
            politicaRef[0] = d.getPoliticaActual();
            velocidadRef[0] = d.getVelocidadCabezal();

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
                cola.append(String.format("%2d. %s  (d=%d)%n", n, s, Math.abs(s.getBloqueDestino() - cab)));
            }
            if (n == 0) {
                cola.append("(vacía)");
            }
            if (n < destinos.length) {
                int[] recortado = new int[n];
                for (int i = 0; i < n; i++) {
                    recortado[i] = destinos[i];
                }
                destinos = recortado;
            }
            colaTextoRef[0] = cola.toString();
            destinosRef[0] = destinos;
            copiaRef[0] = d.copiarBloques();

            espacioRef[0] = d.getBloquesLibresDatos();
            espacioRef[1] = d.getTotalBloquesDatos();
            espacioRef[2] = d.getBloquesLibresSwap();
            espacioRef[3] = d.getTamanoSwap();
            statsLongRef[0] = d.getTotalSolicitudesAtendidas();
            statsLongRef[1] = d.getDesplazamientoTotal();
            esperaPromRef[0] = d.getTiempoEsperaPromedio();
            for (int i = 0; i < todasPoliticas.length; i++) {
                porPoliticaRef[i] = d.getSolicitudesAtendidas(todasPoliticas[i]);
            }
        });

        if (refDisco[0] == null) {
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
        aplicarPermisosModo(true);

        int cabezal = cabezalRef[0];
        boolean sube = subeRef[0];
        PoliticaDisco politica = politicaRef[0];
        int velocidad = velocidadRef[0];

        mapa.actualizar(copiaRef[0], cabezal, actualDestinoRef[0], actualBloquesRef[0], destinosRef[0]);

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
        lblActual.setText("En atención: " + actualTextoRef[0]);
        lblEspacio.setText(String.format("Libres: %d de %d bloques de datos  |  swap libre: %d de %d",
                espacioRef[0], espacioRef[1], espacioRef[2], espacioRef[3]));
        lblEstadisticas.setText(String.format("Atendidas: %d  |  desplazamiento total: %d bloques  |  espera prom.: %.1f ciclos",
                statsLongRef[0], statsLongRef[1], esperaPromRef[0]));
        StringBuilder pp = new StringBuilder("Por política: ");
        for (int i = 0; i < todasPoliticas.length; i++) {
            pp.append(todasPoliticas[i].getCodigoJson()).append('=').append(porPoliticaRef[i]).append("  ");
        }
        lblPorPolitica.setText(pp.toString());
        areaCola.setText(colaTextoRef[0]);
        areaCola.setCaretPosition(0);
    }

    /** Habilita o deshabilita el cambio de política/velocidad según el modo Administrador o Usuario. */
    public void setModoUsuario(ModoUsuario modo) {
        this.modoUsuario = (modo != null) ? modo : ModoUsuario.ADMINISTRADOR;
        aplicarPermisosModo(simulador.getDisco() != null);
    }

    private void aplicarPermisosModo(boolean hayDisco) {
        boolean esAdmin = modoUsuario.esAdministrador();
        boolean habilitar = hayDisco && esAdmin;
        comboPolitica.setEnabled(habilitar);
        spinVelocidad.setEnabled(habilitar);
        String tip = (!hayDisco || esAdmin) ? null : "Solo permitido en Modo Administrador";
        comboPolitica.setToolTipText(tip);
        spinVelocidad.setToolTipText(tip);
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
