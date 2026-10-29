package gui;

import estructuras.Iterador;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import nucleo.Computador;
import simulacion.LogEventos;
import simulacion.Simulador;
import sincronizacion.Buffer;

/**
 * Vista 2 de ÁvilaOS: ventana de monitoreo de la simulación.
 *
 * <p>Muestra el ciclo global, permite iniciar, pausar, reanudar y detener la
 * simulación, ajustar la duración del ciclo en caliente y presenta un
 * {@link PanelComputador} por cada computador registrado.</p>
 *
 * <p>La ventana no contiene lógica del sistema: solo observa. Se refresca con un
 * {@link javax.swing.Timer} de Swing (corre en el hilo de la GUI) y lee el estado
 * dentro de {@link Simulador#leerEstado(Runnable)}, de modo que nunca ve las
 * colas a medias aunque la simulación corra en otros hilos.</p>
 */
public class VentanaEstado extends JFrame {

    /** Cada cuántos milisegundos se refresca la pantalla. */
    private static final int REFRESCO_MS = 150;
    private static final int DURACION_MINIMA_MS = 20;
    private static final int DURACION_MAXIMA_MS = 1000;

    private final Simulador simulador;
    private final PanelComputador[] paneles;
    private final Timer temporizador;

    private final JLabel lblCiclo = new JLabel("Ciclo: 0");
    private final JLabel lblEstado = new JLabel();
    private final JLabel lblDuracion = new JLabel();
    private final JButton btnIniciar = new JButton("Iniciar");
    private final JButton btnPausa = new JButton("Pausar");
    private final JButton btnDetener = new JButton("Detener");
    private final JButton btnReiniciar = new JButton("Reiniciar");
    private final JButton btnCrear = new JButton("Crear proceso…");
    private VentanaCrearProceso ventanaCrear;
    private final JButton btnCrearBuffer = new JButton("Crear buffer");
    private final JButton btnGrafica = new JButton("Gráfica CPU");
    private VentanaGrafica ventanaGrafica;
    private final JButton btnMetricas = new JButton("Métricas");
    private VentanaMetricas ventanaMetricas;
    private final JButton btnConfiguracion = new JButton("Configuración…");
    private final JSlider deslizador;
    private final javax.swing.JTextArea areaLog = new javax.swing.JTextArea(6, 80);
    private final javax.swing.JScrollPane scrollLog = new javax.swing.JScrollPane(areaLog);

    private boolean iniciada;
    private boolean detenida;

    /**
     * @param simulador simulador ya configurado (con sus computadores registrados).
     */
    public VentanaEstado(Simulador simulador) {
        super("ÁvilaOS – Vista 2: estado de la simulación");
        this.simulador = simulador;
        // En la interfaz gráfica se mantiene el reloj activo en ocioso para que no se congele
        this.simulador.setMantenerRelojActivo(true);

        // Un panel por computador.
        paneles = new PanelComputador[simulador.getCantidadComputadores()];
        JPanel centro = new JPanel(new GridLayout(1, Math.max(1, paneles.length), 8, 8));
        Iterador<Computador> it = simulador.iteradorComputadores();
        int i = 0;
        while (it.haySiguiente()) {
            paneles[i] = new PanelComputador(it.siguiente(), simulador);
            centro.add(paneles[i]);
            i++;
        }

        // Barra de control organizada en dos filas para garantizar visibilidad total
        deslizador = new JSlider(DURACION_MINIMA_MS, DURACION_MAXIMA_MS,
                (int) Math.max(DURACION_MINIMA_MS, Math.min(simulador.getDuracionCicloMs(), DURACION_MAXIMA_MS)));
        deslizador.setMajorTickSpacing(200);
        deslizador.setMinorTickSpacing(50);
        deslizador.setPaintTicks(true);
        deslizador.setToolTipText("Ajusta la duración de cada ciclo en milisegundos");
        deslizador.addChangeListener(e -> aplicarDuracion());

        JPanel barra = new JPanel(new GridLayout(2, 1, 0, 4));
        barra.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        // Fila 1: Botones de control de simulación y acceso a módulos
        JPanel fila1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        fila1.add(btnIniciar);
        fila1.add(btnPausa);
        fila1.add(btnDetener);
        fila1.add(btnReiniciar);
        fila1.add(new javax.swing.JSeparator(javax.swing.SwingConstants.VERTICAL));
        fila1.add(btnCrear);
        fila1.add(btnCrearBuffer);
        fila1.add(btnGrafica);
        fila1.add(btnMetricas);
        fila1.add(btnConfiguracion);

        // Fila 2: Indicadores de estado y control de velocidad/duración
        JPanel fila2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        fila2.add(lblCiclo);
        fila2.add(lblEstado);
        fila2.add(new javax.swing.JSeparator(javax.swing.SwingConstants.VERTICAL));
        fila2.add(new JLabel("Duración del ciclo:"));
        fila2.add(deslizador);
        fila2.add(lblDuracion);

        barra.add(fila1);
        barra.add(fila2);

        btnIniciar.addActionListener(e -> iniciar());
        btnPausa.addActionListener(e -> pausarOReanudar());
        btnDetener.addActionListener(e -> detener());
        btnReiniciar.addActionListener(e -> reiniciar());
        btnCrear.addActionListener(e -> abrirCrearProceso());
        btnCrearBuffer.addActionListener(e -> crearBuffer());
        btnGrafica.addActionListener(e -> abrirGrafica());
        btnMetricas.addActionListener(e -> abrirMetricas());
        btnConfiguracion.addActionListener(e -> abrirConfiguracion());

        areaLog.setEditable(false);
        areaLog.setLineWrap(true);
        areaLog.setWrapStyleWord(true);
        scrollLog.setBorder(BorderFactory.createTitledBorder("Log de eventos"));
        JPanel inferior = new JPanel(new BorderLayout());
        inferior.add(scrollLog, BorderLayout.CENTER);

        setLayout(new BorderLayout(6, 6));
        add(barra, BorderLayout.NORTH);
        add(new JScrollPane(centro), BorderLayout.CENTER);
        add(inferior, BorderLayout.SOUTH);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cerrar();
            }
        });

        temporizador = new Timer(REFRESCO_MS, e -> refrescar());
        aplicarDuracion();
        refrescar();
        temporizador.start();
        setSize(Math.max(700, 420 * Math.max(1, paneles.length)), 640);
        setLocationRelativeTo(null);
    }

    private void iniciar() {
        if (iniciada) {
            return;
        }
        iniciada = true;
        simulador.iniciar();
        refrescar();
    }

    /** Abre la Vista 1 (formulario de creación de procesos); reutiliza la misma ventana. */
    private void abrirCrearProceso() {
        if (ventanaCrear == null) {
            ventanaCrear = new VentanaCrearProceso(this, simulador);
        }
        ventanaCrear.setVisible(true);
        ventanaCrear.toFront();
    }

    /** Abre la gráfica de utilización de CPU; reutiliza la misma ventana. */
    private void abrirGrafica() {
        if (ventanaGrafica == null) {
            ventanaGrafica = new VentanaGrafica(this, simulador);
        }
        ventanaGrafica.setVisible(true);
        ventanaGrafica.toFront();
    }

    /** Abre (o trae al frente) la ventana de métricas de rendimiento. */
    private void abrirMetricas() {
        if (ventanaMetricas == null) {
            ventanaMetricas = new VentanaMetricas(this, simulador);
        }
        ventanaMetricas.setVisible(true);
        ventanaMetricas.toFront();
    }

    /** Abre la ventana de configuración y persistencia. */
    private void abrirConfiguracion() {
        VentanaConfiguracion vc = new VentanaConfiguracion(this, simulador);
        vc.setVisible(true);
    }

    private void crearBuffer() {
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 2, 4, 4));
        JComboBox<Computador> comboComp = new JComboBox<>();
        Iterador<Computador> it = simulador.iteradorComputadores();
        while (it.haySiguiente()) {
            comboComp.addItem(it.siguiente());
        }
        comboComp.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> lista, Object valor, int i,
                                                                  boolean sel, boolean foco) {
                Object texto = valor instanceof Computador c
                        ? c.getNombre() + " (RAM " + c.getMemoriaTotal() + " KB)" : valor;
                return super.getListCellRendererComponent(lista, texto, i, sel, foco);
            }
        });

        int nBuf = 1;
        while (simulador.buscarBuffer("B" + nBuf) != null || simulador.buscarBuffer("BUF-" + nBuf) != null) {
            nBuf++;
        }
        JTextField txtId = new JTextField("B" + nBuf);
        JTextField txtCap = new JTextField("5");
        JTextField txtTam = new JTextField("4");
        panel.add(new JLabel("ID del buffer:"));
        panel.add(txtId);
        panel.add(new JLabel("Capacidad:"));
        panel.add(txtCap);
        panel.add(new JLabel("Tamaño elemento (KB):"));
        panel.add(txtTam);
        panel.add(new JLabel("Computador anfitrión:"));
        panel.add(comboComp);
        int res = JOptionPane.showConfirmDialog(this, panel, "Crear buffer acotado", JOptionPane.OK_CANCEL_OPTION);
        if (res != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            String id = txtId.getText().trim();
            if (id.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Debe ingresar un ID para el buffer.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (simulador.buscarBuffer(id) != null) {
                JOptionPane.showMessageDialog(this, "Ya existe un buffer con el ID " + id + ".", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String capStr = txtCap.getText().trim();
            String tamStr = txtTam.getText().trim();
            int cap;
            long tam;
            try {
                cap = Integer.parseInt(capStr);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "La capacidad debe ser un valor numérico entero.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                tam = Long.parseLong(tamStr);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "El tamaño del elemento debe ser un valor numérico.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (cap <= 0) {
                JOptionPane.showMessageDialog(this, "La capacidad debe ser mayor que cero.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (tam <= 0) {
                JOptionPane.showMessageDialog(this, "El tamaño del elemento debe ser mayor que cero.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Computador anfitrion = (Computador) comboComp.getSelectedItem();
            if (anfitrion == null) {
                JOptionPane.showMessageDialog(this, "Debe seleccionar un computador anfitrión.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Buffer b = new Buffer(id, cap, anfitrion.getId(), tam, anfitrion);
            simulador.registrarBuffer(b);
            JOptionPane.showMessageDialog(this, "Buffer " + id + " creado correctamente en " + anfitrion.getNombre(), "OK", JOptionPane.INFORMATION_MESSAGE);
            if (ventanaCrear != null) {
                ventanaCrear.actualizarBuffers();
            }
            refrescar();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al crear buffer: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void pausarOReanudar() {
        if (!iniciada || detenida) {
            return;
        }
        if (simulador.estaPausada()) {
            simulador.reanudar();
        } else {
            simulador.pausar();
        }
        refrescar();
    }

    private void detener() {
        if (detenida) {
            return;
        }
        detenida = true;
        simulador.detener();
        refrescar();
    }

    private void reiniciar() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Desea reiniciar la simulación al escenario inicial?",
                "Reiniciar Simulación",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        temporizador.stop();
        cerrarVentanasHijas();
        simulador.detener();
        dispose();
        SwingUtilities.invokeLater(() -> {
            Simulador nuevo = config.EscenarioDemo.crear();
            VentanaEstado nuevaVentana = new VentanaEstado(nuevo);
            nuevaVentana.setVisible(true);
        });
    }

    private void cerrarVentanasHijas() {
        if (ventanaGrafica != null) {
            ventanaGrafica.dispose();
            ventanaGrafica = null;
        }
        if (ventanaMetricas != null) {
            ventanaMetricas.dispose();
            ventanaMetricas = null;
        }
        if (ventanaCrear != null) {
            ventanaCrear.dispose();
            ventanaCrear = null;
        }
    }

    private void aplicarDuracion() {
        simulador.setDuracionCicloMs(deslizador.getValue());
        lblDuracion.setText(deslizador.getValue() + " ms");
    }

    private void cerrar() {
        temporizador.stop();
        cerrarVentanasHijas();
        simulador.detener();
        dispose();
    }

    /** Lee el estado de forma segura y actualiza todos los paneles. */
    private void refrescar() {
        final StringBuilder logSb = new StringBuilder();
        final long[] cicloActual = new long[1];
        simulador.leerEstado(() -> {
            cicloActual[0] = simulador.getCicloActual();
            for (PanelComputador panel : paneles) {
                panel.actualizar();
            }
            LogEventos log = simulador.getLogEventos();
            if (log != null) {
                estructuras.ListaEnlazada<LogEventos.Evento> ultimos = log.getUltimos(200);
                estructuras.Iterador<LogEventos.Evento> itLog = ultimos.iterador();
                while (itLog.haySiguiente()) {
                    logSb.append(itLog.siguiente().toString()).append('\n');
                }
            }
        });

        SwingUtilities.invokeLater(() -> {
            lblCiclo.setText("Ciclo: " + cicloActual[0]);
            lblEstado.setText("Estado: " + textoEstado());
            btnIniciar.setEnabled(!iniciada && !detenida);
            btnPausa.setEnabled(iniciada && !detenida && !simulador.isLimiteAlcanzado());
            btnPausa.setText(simulador.estaPausada() ? "Reanudar" : "Pausar");
            btnDetener.setEnabled(iniciada && !detenida);
            btnReiniciar.setEnabled(true);
            btnCrear.setEnabled(!detenida && !simulador.isLimiteAlcanzado());
            btnCrearBuffer.setEnabled(!detenida && !simulador.isLimiteAlcanzado());

            if (logSb.length() > 0) {
                String nuevoTexto = logSb.toString();
                if (!nuevoTexto.equals(areaLog.getText())) {
                    javax.swing.JScrollBar scrollBar = scrollLog.getVerticalScrollBar();
                    boolean estabaAlFinal = (scrollBar.getValue() + scrollBar.getVisibleAmount() >= scrollBar.getMaximum() - 20);
                    int posPrevia = scrollBar.getValue();

                    areaLog.setText(nuevoTexto);

                    if (estabaAlFinal) {
                        areaLog.setCaretPosition(areaLog.getDocument().getLength());
                    } else {
                        SwingUtilities.invokeLater(() -> scrollBar.setValue(posPrevia));
                    }
                }
            }
        });
    }

    /**
     * Agrega una nueva línea al log de eventos de la GUI con smart auto-scroll y thread-safety.
     *
     * @param linea línea de texto a agregar al log.
     */
    public void agregarLineaLog(String linea) {
        SwingUtilities.invokeLater(() -> {
            javax.swing.JScrollBar scrollBar = scrollLog.getVerticalScrollBar();
            boolean estabaAlFinal = (scrollBar.getValue() + scrollBar.getVisibleAmount() >= scrollBar.getMaximum() - 20);
            int posPrevia = scrollBar.getValue();

            areaLog.append(linea + "\n");

            if (estabaAlFinal) {
                areaLog.setCaretPosition(areaLog.getDocument().getLength());
            } else {
                SwingUtilities.invokeLater(() -> scrollBar.setValue(posPrevia));
            }
        });
    }

    private String textoEstado() {
        if (detenida) {
            return "detenida (haga clic en Reiniciar para comenzar de nuevo)";
        }
        if (!iniciada) {
            return "sin iniciar";
        }
        if (simulador.estaPausada()) {
            return "pausada";
        }
        if (simulador.isLimiteAlcanzado()) {
            return "límite de ciclos alcanzado (detenida)";
        }
        if (!simulador.hayTrabajoPendiente()) {
            return "finalizada / ociosa (esperando nuevos procesos)";
        }
        return "en marcha";
    }
}
