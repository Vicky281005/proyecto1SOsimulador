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
import javax.swing.Timer;
import nucleo.Computador;
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
    private final JButton btnCrear = new JButton("Crear proceso…");
    private VentanaCrearProceso ventanaCrear;
    private final JButton btnCrearBuffer = new JButton("Crear buffer");
    private final JButton btnGrafica = new JButton("Gráfica CPU");
    private VentanaGrafica ventanaGrafica;
    private final JSlider deslizador;

    private boolean iniciada;
    private boolean detenida;

    /**
     * @param simulador simulador ya configurado (con sus computadores registrados).
     */
    public VentanaEstado(Simulador simulador) {
        super("ÁvilaOS – Vista 2: estado de la simulación");
        this.simulador = simulador;

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

        // Barra de control.
        deslizador = new JSlider(0, DURACION_MAXIMA_MS,
                (int) Math.min(simulador.getDuracionCicloMs(), DURACION_MAXIMA_MS));
        deslizador.addChangeListener(e -> aplicarDuracion());
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        barra.add(lblCiclo);
        barra.add(lblEstado);
        barra.add(btnIniciar);
        barra.add(btnPausa);
        barra.add(btnDetener);
        barra.add(btnCrear);
        barra.add(btnCrearBuffer);
        barra.add(btnGrafica);
        barra.add(new JLabel("Duración del ciclo:"));
        barra.add(deslizador);
        barra.add(lblDuracion);
        barra.setBorder(BorderFactory.createEtchedBorder());

        btnIniciar.addActionListener(e -> iniciar());
        btnPausa.addActionListener(e -> pausarOReanudar());
        btnDetener.addActionListener(e -> detener());
        btnCrear.addActionListener(e -> abrirCrearProceso());
        btnCrearBuffer.addActionListener(e -> crearBuffer());
        btnGrafica.addActionListener(e -> abrirGrafica());

        setLayout(new BorderLayout(6, 6));
        add(barra, BorderLayout.NORTH);
        add(new JScrollPane(centro), BorderLayout.CENTER);

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

    private void crearBuffer() {
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 2, 4, 4));
        JComboBox<Computador> comboComp = new JComboBox<>();
        Iterador<Computador> it = simulador.iteradorComputadores();
        while (it.haySiguiente()) {
            comboComp.addItem(it.siguiente());
        }
        JTextField txtId = new JTextField("BUF-1");
        JTextField txtCap = new JTextField("5");
        JTextField txtTam = new JTextField("4");
        panel.add(new JLabel("ID del buffer:"));
        panel.add(txtId);
        panel.add(new JLabel("Capacidad:"));
        panel.add(txtCap);
        panel.add(new JLabel("Tamaño elemento (bytes):"));
        panel.add(txtTam);
        panel.add(new JLabel("Computador anfitrión:"));
        panel.add(comboComp);
        int res = JOptionPane.showConfirmDialog(this, panel, "Crear buffer acotado", JOptionPane.OK_CANCEL_OPTION);
        if (res != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            String id = txtId.getText().trim();
            int cap = Integer.parseInt(txtCap.getText().trim());
            long tam = Long.parseLong(txtTam.getText().trim());
            Computador anfitrion = (Computador) comboComp.getSelectedItem();
            if (anfitrion == null || id.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Datos inválidos", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Buffer b = new Buffer(id, cap, anfitrion.getId(), tam, anfitrion);
            simulador.registrarBuffer(b);
            JOptionPane.showMessageDialog(this, "Buffer " + id + " creado correctamente en " + anfitrion.getNombre(), "OK", JOptionPane.INFORMATION_MESSAGE);
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

    private void aplicarDuracion() {
        simulador.setDuracionCicloMs(deslizador.getValue());
        lblDuracion.setText(deslizador.getValue() + " ms");
    }

    private void cerrar() {
        temporizador.stop();
        simulador.detener();
        dispose();
    }

    /** Lee el estado de forma segura y actualiza todos los paneles. */
    private void refrescar() {
        simulador.leerEstado(() -> {
            lblCiclo.setText("Ciclo: " + simulador.getCicloActual());
            for (PanelComputador panel : paneles) {
                panel.actualizar();
            }
        });
        lblEstado.setText("Estado: " + textoEstado());
        btnIniciar.setEnabled(!iniciada && !detenida);
        btnPausa.setEnabled(iniciada && !detenida);
        btnPausa.setText(simulador.estaPausada() ? "Reanudar" : "Pausar");
        btnDetener.setEnabled(!detenida);
        btnCrear.setEnabled(!detenida);
    }

    private String textoEstado() {
        if (detenida) {
            return "detenida";
        }
        if (!iniciada) {
            return "sin iniciar";
        }
        if (simulador.estaPausada()) {
            return "pausada";
        }
        if (simulador.isLimiteAlcanzado()) {
            return "límite de ciclos alcanzado";
        }
        if (!simulador.hayTrabajoPendiente()) {
            return "ociosa (esperando procesos)";
        }
        return "en marcha";
    }
}
