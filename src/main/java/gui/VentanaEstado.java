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
    private final PanelExploradorArchivos panelArchivos;
    private final Timer temporizador;

    private final JLabel lblCiclo = new JLabel("Ciclo: 0");
    private final JLabel lblEstado = new JLabel();
    private final JLabel lblResumenDisco = new JLabel("Disco: —");
    private final JLabel lblDuracion = new JLabel();
    private final JButton btnIniciar = new JButton("Iniciar");
    private final JButton btnPausa = new JButton("Pausar");
    private final JButton btnDetener = new JButton("Detener");
    private final JButton btnReiniciar = new JButton("Reiniciar");
    private final JButton btnCrear = new JButton("Crear proceso…");
    private VentanaCrearProceso ventanaCrear;
    private final JButton btnGrafica = new JButton("Gráfica CPU");
    private VentanaGrafica ventanaGrafica;
    private final JButton btnGraficaDisco = new JButton("Gráfica disco");
    private VentanaGraficaDisco ventanaGraficaDisco;
    private final JButton btnMetricas = new JButton("Métricas");
    private VentanaMetricas ventanaMetricas;
    private final JButton btnDisco = new JButton("Disco");
    private VentanaDisco ventanaDisco;
    private final JButton btnConfiguracion = new JButton("Cargar JSON / Config…");
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

        // Un panel por computador (en el Proyecto 2 se usa una sola instancia).
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
        fila1.add(btnGrafica);
        fila1.add(btnGraficaDisco);
        fila1.add(btnMetricas);
        fila1.add(btnDisco);
        fila1.add(btnConfiguracion);

        // Fila 2: Indicadores de estado y control de velocidad/duración
        JPanel fila2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        fila2.add(lblCiclo);
        fila2.add(lblEstado);
        fila2.add(new javax.swing.JSeparator(javax.swing.SwingConstants.VERTICAL));
        fila2.add(lblResumenDisco);
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
        btnGrafica.addActionListener(e -> abrirGrafica());
        btnGraficaDisco.addActionListener(e -> abrirGraficaDisco());
        btnMetricas.addActionListener(e -> abrirMetricas());
        btnDisco.addActionListener(e -> abrirDisco());
        btnConfiguracion.addActionListener(e -> abrirConfiguracion());

        areaLog.setEditable(false);
        areaLog.setLineWrap(true);
        areaLog.setWrapStyleWord(true);
        scrollLog.setBorder(BorderFactory.createTitledBorder("Log de eventos"));
        JPanel inferior = new JPanel(new BorderLayout());
        inferior.add(scrollLog, BorderLayout.CENTER);

        setLayout(new BorderLayout(6, 6));
        add(barra, BorderLayout.NORTH);
        panelArchivos = new PanelExploradorArchivos(simulador);
        panelArchivos.setEscuchaCambioModo(this::aplicarPermisosModoUsuario);
        javax.swing.JSplitPane splitCentral = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(centro),
                panelArchivos);
        splitCentral.setResizeWeight(0.46);
        splitCentral.setOneTouchExpandable(true);
        add(splitCentral, BorderLayout.CENTER);
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
        setSize(Math.max(960, 420 * Math.max(1, paneles.length)), 700);
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    private void iniciar() {
        if (iniciada) {
            return;
        }
        if (simulador.isRequiereCargaJsonObligatoria() && !simulador.tieneAmbosJsonCargados()) {
            JOptionPane.showMessageDialog(this,
                    "Debe cargar obligatoriamente ambos archivos JSON (Sistema de Archivos y Procesos/Configuración)\n"
                    + "antes de iniciar el reloj global de ÁvilaOS.",
                    "Carga de JSON obligatoria", JOptionPane.WARNING_MESSAGE);
            abrirConfiguracion();
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
        ventanaGrafica.refrescar();
        ventanaGrafica.toFront();
    }

    /** Abre la gráfica del desplazamiento acumulado del cabezal; reutiliza la misma ventana. */
    private void abrirGraficaDisco() {
        if (ventanaGraficaDisco == null) {
            ventanaGraficaDisco = new VentanaGraficaDisco(this, simulador);
        }
        ventanaGraficaDisco.setVisible(true);
        ventanaGraficaDisco.refrescar();
        ventanaGraficaDisco.toFront();
    }

    /** Abre (o trae al frente) la ventana de métricas de rendimiento. */
    private void abrirMetricas() {
        if (ventanaMetricas == null) {
            ventanaMetricas = new VentanaMetricas(this, simulador);
        }
        ventanaMetricas.setVisible(true);
        ventanaMetricas.refrescar();
        ventanaMetricas.toFront();
    }

    /** Abre (o trae al frente) la vista del disco y su planificación de E/S. */
    private void abrirDisco() {
        if (ventanaDisco == null) {
            ventanaDisco = new VentanaDisco(this, simulador);
        }
        ventanaDisco.setModoUsuario(panelArchivos.getModoSeleccionado());
        ventanaDisco.setVisible(true);
        ventanaDisco.refrescar();
        ventanaDisco.toFront();
    }

    private void aplicarPermisosModoUsuario() {
        enums.ModoUsuario modo = panelArchivos.getModoSeleccionado();
        for (PanelComputador panel : paneles) {
            panel.setModoUsuario(modo);
        }
        if (ventanaDisco != null) {
            ventanaDisco.setModoUsuario(modo);
        }
        if (!modo.esAdministrador() && ventanaCrear != null && ventanaCrear.isVisible()) {
            ventanaCrear.setVisible(false);
        }
        refrescar();
    }

    /** Abre la ventana de configuración y persistencia. */
    private void abrirConfiguracion() {
        VentanaConfiguracion vc = new VentanaConfiguracion(this, simulador);
        vc.setVisible(true);
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
        modelo.PCB.reiniciarGeneradorId();
        disco.SolicitudES.reiniciarGeneradorId();
        dispose();
        SwingUtilities.invokeLater(() -> {
            Simulador nuevo = com.mycompany.proyecto1sosimulador.Proyecto1SOsimulador.crearSimuladorPendienteDeJson();
            VentanaEstado nuevaVentana = new VentanaEstado(nuevo);
            nuevaVentana.setVisible(true);
        });
    }

    private void cerrarVentanasHijas() {
        if (ventanaDisco != null) {
            ventanaDisco.dispose();
            ventanaDisco = null;
        }
        if (ventanaGrafica != null) {
            ventanaGrafica.dispose();
            ventanaGrafica = null;
        }
        if (ventanaGraficaDisco != null) {
            ventanaGraficaDisco.dispose();
            ventanaGraficaDisco = null;
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

    /**
     * Cierra la ventana actual y abre una nueva {@link VentanaEstado} enlazada
     * al {@code nuevoSimulador} construido tras cargar los JSON obligatorios.
     */
    public void cerrarYReemplazar(Simulador nuevoSimulador) {
        temporizador.stop();
        cerrarVentanasHijas();
        simulador.detener();
        dispose();
        SwingUtilities.invokeLater(() -> new VentanaEstado(nuevoSimulador).setVisible(true));
    }

    /** Lee el estado de forma segura mediante snapshots y actualiza Swing fuera de {@code mutexEstado}. */
    private void refrescar() {
        final StringBuilder logSb = new StringBuilder();
        final long[] cicloActual = new long[1];
        final boolean[] hayTrabajo = new boolean[1];
        final boolean[] faltaJson = new boolean[1];
        final boolean[] limiteAlcanzado = new boolean[1];
        final boolean[] pausada = new boolean[1];
        final String[] resumenDisco = new String[1];
        final PanelComputador.Snapshot[] snapshotsPc = new PanelComputador.Snapshot[paneles.length];
        final PanelExploradorArchivos.Snapshot[] snapshotArchivos = new PanelExploradorArchivos.Snapshot[1];

        simulador.leerEstado(() -> {
            cicloActual[0] = simulador.getCicloActual();
            hayTrabajo[0] = simulador.hayTrabajoPendiente();
            faltaJson[0] = simulador.isRequiereCargaJsonObligatoria() && !simulador.tieneAmbosJsonCargados();
            limiteAlcanzado[0] = simulador.isLimiteAlcanzado();
            pausada[0] = simulador.estaPausada();
            disco.DiscoSimulado d = simulador.getDisco();
            if (d == null) {
                resumenDisco[0] = "Disco: sin configurar";
            } else {
                resumenDisco[0] = "Disco: " + d.getPoliticaActual().getCodigoJson()
                        + " | Cabezal: " + d.getPosicionCabezal()
                        + (d.isDireccionAscendente() ? " ↑" : " ↓")
                        + " | Cola E/S: " + d.getCantidadEnColaES();
            }
            for (int i = 0; i < paneles.length; i++) {
                snapshotsPc[i] = paneles[i].capturarSnapshot();
            }
            snapshotArchivos[0] = panelArchivos.capturarSnapshot();
            LogEventos log = simulador.getLogEventos();
            if (log != null) {
                estructuras.ListaEnlazada<LogEventos.Evento> ultimos = log.getUltimos(200);
                estructuras.Iterador<LogEventos.Evento> itLog = ultimos.iterador();
                while (itLog.haySiguiente()) {
                    logSb.append(itLog.siguiente().toString()).append('\n');
                }
            }
        });

        Runnable aplicarEnEdt = () -> {
            for (int i = 0; i < paneles.length; i++) {
                paneles[i].aplicarSnapshot(snapshotsPc[i]);
            }
            panelArchivos.setOperacionesHabilitadas(!detenida && !limiteAlcanzado[0] && !faltaJson[0]);
            panelArchivos.aplicarSnapshot(snapshotArchivos[0]);

            lblCiclo.setText("Ciclo: " + cicloActual[0]);
            lblEstado.setText("Estado: " + textoEstado(hayTrabajo[0]));
            lblResumenDisco.setText(resumenDisco[0]);
            boolean esAdmin = panelArchivos.getModoSeleccionado().esAdministrador();
            btnIniciar.setEnabled(!iniciada && !detenida && !faltaJson[0]);
            btnPausa.setEnabled(iniciada && !detenida && !limiteAlcanzado[0]);
            btnPausa.setText(pausada[0] ? "Reanudar" : "Pausar");
            btnDetener.setEnabled(iniciada && !detenida);
            btnReiniciar.setEnabled(true);
            btnCrear.setEnabled(!detenida && !limiteAlcanzado[0] && !faltaJson[0] && esAdmin);
            btnCrear.setToolTipText(esAdmin
                    ? "Crear un nuevo proceso manual"
                    : "Gestionar procesos solo está permitido en Modo Administrador");
            btnConfiguracion.setEnabled(esAdmin || faltaJson[0]);
            btnConfiguracion.setToolTipText((esAdmin || faltaJson[0])
                    ? "Configuración y carga de JSON"
                    : "Cambiar configuración y políticas solo está permitido en Modo Administrador");

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
        };

        if (SwingUtilities.isEventDispatchThread()) {
            aplicarEnEdt.run();
        } else {
            SwingUtilities.invokeLater(aplicarEnEdt);
        }
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

    private String textoEstado(boolean hayTrabajo) {
        if (detenida) {
            return "detenida (haga clic en Reiniciar para comenzar de nuevo)";
        }
        if (!iniciada) {
            if (simulador.isRequiereCargaJsonObligatoria() && !simulador.tieneAmbosJsonCargados()) {
                return "pendiente cargar ambos JSON obligatorios";
            }
            return "sin iniciar";
        }
        if (simulador.estaPausada()) {
            return "pausada";
        }
        if (simulador.isLimiteAlcanzado()) {
            return "límite de ciclos alcanzado (detenida)";
        }
        if (!hayTrabajo) {
            return "finalizada / ociosa (esperando nuevos procesos)";
        }
        return "en marcha";
    }
}
