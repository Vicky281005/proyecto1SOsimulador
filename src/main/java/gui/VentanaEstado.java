package gui;

import estructuras.Iterador;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.Timer;
import nucleo.Computador;
import simulacion.Simulador;

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
        barra.add(new JLabel("Duración del ciclo:"));
        barra.add(deslizador);
        barra.add(lblDuracion);
        barra.setBorder(BorderFactory.createEtchedBorder());

        btnIniciar.addActionListener(e -> iniciar());
        btnPausa.addActionListener(e -> pausarOReanudar());
        btnDetener.addActionListener(e -> detener());

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
