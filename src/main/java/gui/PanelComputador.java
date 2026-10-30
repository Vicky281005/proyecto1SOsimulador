package gui;

import enums.PoliticaPlanificacion;
import estructuras.Iterador;
import hardware.CPU;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import modelo.PCB;
import nucleo.Computador;
import planificacion.FabricaPoliticas;
import simulacion.Simulador;
import sincronizacion.Buffer;

/**
 * Panel de la Vista 2 que muestra el estado de UN computador: el proceso que
 * está en la CPU (con su PC, prioridad y deadline), el modo de la CPU, las
 * colas de nuevos, listos, bloqueados (con su motivo) y terminados, la RAM
 * ocupada y un selector para cambiar la política de planificación.
 *
 * <p>Este panel solo <b>observa</b>: no contiene lógica del sistema. Su método
 * {@link #actualizar()} debe llamarse dentro de {@link Simulador#leerEstado(Runnable)}
 * para que las estructuras no cambien mientras se leen.</p>
 */
public class PanelComputador extends JPanel {

    private static final Color COLOR_OCIOSA = new Color(120, 120, 120);
    private static final Color COLOR_USUARIO = new Color(30, 130, 60);
    private static final Color COLOR_SO = new Color(190, 70, 30);

    private final Computador computador;
    private final Simulador simulador;

    private final JLabel lblProceso = new JLabel();
    private final JLabel lblRegistros = new JLabel();
    private final JLabel lblDeadline = new JLabel();
    private final JLabel lblModo = new JLabel();
    private final JProgressBar barraRam = new JProgressBar(0, 100);
    private final JComboBox<PoliticaPlanificacion> comboPolitica =
            new JComboBox<>(PoliticaPlanificacion.values());
    private final JLabel lblQuantum = new JLabel("q:");
    private final javax.swing.JSpinner spinnerQuantum =
            new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(2, 1, 100, 1));

    private final ListaVisual nuevos = new ListaVisual();
    private final ListaVisual listos = new ListaVisual();
    private final ListaVisual bloqueados = new ListaVisual();
    private final ListaVisual terminados = new ListaVisual();
    private final ListaVisual buffers = new ListaVisual();

    /** Evita que seleccionar la política por código dispare un cambio real. */
    private boolean ajustandoCombo;
    /** Evita que modificar el spinner por código dispare un cambio real. */
    private boolean ajustandoQuantum;

    /**
     * @param computador computador que se muestra.
     * @param simulador  simulador dueño del computador (para leer/cambiar estado de forma segura).
     */
    public PanelComputador(Computador computador, Simulador simulador) {
        this.computador = computador;
        this.simulador = simulador;
        construir();
        seleccionarPoliticaActual();
        comboPolitica.addActionListener(e -> cambiarPolitica());
        spinnerQuantum.addChangeListener(e -> cambiarQuantum());
        actualizar();
    }

    private void construir() {
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createTitledBorder(
                computador.getNombre() + "  (RAM " + computador.getMemoriaTotal() + " KB)"));

        // --- Arriba: CPU, política y RAM ---
        JPanel arriba = new JPanel(new GridLayout(0, 1, 2, 2));
        arriba.add(lblModo);
        arriba.add(lblProceso);
        arriba.add(lblRegistros);
        arriba.add(lblDeadline);

        JPanel filaPolitica = new JPanel(new BorderLayout(6, 0));
        filaPolitica.add(new JLabel("Política:"), BorderLayout.WEST);

        JPanel pnlCentroPolitica = new JPanel(new BorderLayout(4, 0));
        pnlCentroPolitica.add(comboPolitica, BorderLayout.CENTER);

        JPanel pnlQuantum = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 0));
        pnlQuantum.add(lblQuantum);
        spinnerQuantum.setPreferredSize(new java.awt.Dimension(54, 24));
        pnlQuantum.add(spinnerQuantum);
        pnlCentroPolitica.add(pnlQuantum, BorderLayout.EAST);

        filaPolitica.add(pnlCentroPolitica, BorderLayout.CENTER);
        arriba.add(filaPolitica);
        barraRam.setStringPainted(true);
        arriba.add(barraRam);
        add(arriba, BorderLayout.NORTH);

        // --- Centro: las cuatro colas + buffers ---
        JPanel colas = new JPanel(new GridLayout(2, 2, 6, 6));
        colas.add(envolver("Nuevos", nuevos));
        colas.add(envolver("Listos", listos));
        colas.add(envolver("Bloqueados (motivo)", bloqueados));
        colas.add(envolver("Terminados", terminados));
        JPanel centro = new JPanel(new BorderLayout(6, 6));
        centro.add(colas, BorderLayout.CENTER);
        centro.add(envolver("Buffers", buffers), BorderLayout.SOUTH);
        add(centro, BorderLayout.CENTER);
    }

    private JScrollPane envolver(String titulo, ListaVisual lista) {
        JScrollPane scroll = new JScrollPane(lista.componente);
        scroll.setBorder(BorderFactory.createTitledBorder(titulo));
        return scroll;
    }

    /**
     * Refresca todo lo que muestra el panel con el estado actual del computador.
     * Debe ejecutarse en el hilo de Swing y dentro de {@code leerEstado}.
     */
    public void actualizar() {
        CPU cpu = computador.getCpu();
        PCB actual = cpu.getProcesoActual();

        lblModo.setText("CPU: " + (actual == null ? "ociosa" : "ocupada")
                + "   |   Modo: " + cpu.getModo());
        lblModo.setForeground(actual == null ? COLOR_OCIOSA
                : (cpu.getModo() == enums.ModoCPU.KERNEL ? COLOR_SO : COLOR_USUARIO));

        if (actual == null) {
            lblProceso.setText("Proceso en CPU: —");
            lblRegistros.setText("PC: —   MAR: —   Prioridad: —");
            lblDeadline.setText("Deadline: —   Restante: —");
        } else {
            lblProceso.setText("Proceso en CPU: " + actual.getNombre() + " (id " + actual.getId() + ")");
            lblRegistros.setText("PC: " + actual.getPc() + "   MAR: " + actual.getMar()
                    + "   Prioridad: " + actual.getPrioridad());
            lblDeadline.setText("Deadline (ciclo): " + actual.getDeadlineAbsoluto()
                    + "   Restante: " + actual.getTiempoRestante());
        }

        long total = computador.getMemoriaTotal();
        long usada = total - computador.getMemoriaDisponible();
        barraRam.setValue((int) (usada * 100 / total));
        barraRam.setString("RAM: " + usada + " / " + total + " KB");

        nuevos.mostrar(lineas(computador.iteradorNuevos(), false, false));
        listos.mostrar(lineas(computador.getPlanificador().iteradorListos(), false, false));
        bloqueados.mostrar(lineas(computador.iteradorBloqueados(), true, false));
        terminados.mostrar(lineas(computador.iteradorTerminados(), false, true));
        actualizarBuffers();

        // Sincronizar estado del control de Quantum
        if (computador.getPlanificador().getPolitica() instanceof planificacion.RoundRobin rr) {
            lblQuantum.setEnabled(true);
            spinnerQuantum.setEnabled(true);
            int q = rr.getQuantum();
            if (!ajustandoQuantum && ((Integer) spinnerQuantum.getValue()) != q) {
                ajustandoQuantum = true;
                spinnerQuantum.setValue(q);
                ajustandoQuantum = false;
            }
        } else {
            lblQuantum.setEnabled(false);
            spinnerQuantum.setEnabled(false);
        }
    }

    private void actualizarBuffers() {
        estructuras.ListaEnlazada<Buffer> listaBuffers = computador.getBuffers();
        if (listaBuffers == null || listaBuffers.estaVacia()) {
            buffers.forzar(new String[0]);
            return;
        }
        String[] arr = new String[listaBuffers.getTamano()];
        for (int i = 0; i < listaBuffers.getTamano(); i++) {
            Buffer b = listaBuffers.obtener(i);
            if (b == null) {
                arr[i] = "-";
            } else {
                arr[i] = b.getId() + " [" + b.getCantidadElementos() + "/" + b.getCapacidad() + "] vacios=" + b.getValorVacios()
                        + " llenos=" + b.getValorLlenos() + " mutex=" + b.getValorMutex()
                        + " (esp vacios:" + b.getCantidadEsperandoVacios() + ")";
            }
        }
        buffers.forzar(arr);
    }

    /** Convierte una cola de procesos en líneas de texto, una por proceso. */
    private String[] lineas(Iterador<PCB> it, boolean conMotivo, boolean terminado) {
        String[] buffer = new String[64];
        int n = 0;
        while (it.haySiguiente()) {
            PCB p = it.siguiente();
            if (n == buffer.length) {
                String[] mayor = new String[buffer.length * 2];
                System.arraycopy(buffer, 0, mayor, 0, n);
                buffer = mayor;
            }
            buffer[n++] = describir(p, conMotivo, terminado);
        }
        String[] resultado = new String[n];
        System.arraycopy(buffer, 0, resultado, 0, n);
        return resultado;
    }

    private String describir(PCB p, boolean conMotivo, boolean terminado) {
        String base = p.getNombre() + " (id " + p.getId() + ")";
        if (conMotivo) {
            String motivo = p.getMotivoBloqueo().getEtiqueta();
            if (p.getCiclosBloqueoRestantes() > 0) {
                motivo += " (restan: " + p.getCiclosBloqueoRestantes() + " c)";
            }
            return base + " – " + motivo;
        }
        if (terminado) {
            return base + " – fin en ciclo " + p.getCicloFin()
                    + (p.isDeadlineCumplido() ? "" : " – deadline FALLIDO");
        }
        return base + " – prioridad " + p.getPrioridad() + ", restante " + p.getTiempoRestante();
    }

    /** Marca en el selector la política con la que arrancó el computador. */
    private void seleccionarPoliticaActual() {
        String actual = computador.getPlanificador().getPolitica().getNombre();
        ajustandoCombo = true;
        for (PoliticaPlanificacion p : PoliticaPlanificacion.values()) {
            if (FabricaPoliticas.crear(p).getNombre().equals(actual)) {
                comboPolitica.setSelectedItem(p);
            }
        }
        boolean esRR = (comboPolitica.getSelectedItem() == PoliticaPlanificacion.ROUND_ROBIN);
        lblQuantum.setEnabled(esRR);
        spinnerQuantum.setEnabled(esRR);
        if (esRR) {
            int q = computador.getPlanificador().getQuantum();
            if (q > 0) {
                ajustandoQuantum = true;
                spinnerQuantum.setValue(q);
                ajustandoQuantum = false;
            }
        }
        ajustandoCombo = false;
    }

    private void cambiarPolitica() {
        if (ajustandoCombo) {
            return;
        }
        PoliticaPlanificacion elegida = (PoliticaPlanificacion) comboPolitica.getSelectedItem();
        boolean esRR = (elegida == PoliticaPlanificacion.ROUND_ROBIN);
        lblQuantum.setEnabled(esRR);
        spinnerQuantum.setEnabled(esRR);

        int q = (Integer) spinnerQuantum.getValue();
        if (q <= 0) {
            q = 2;
        }
        final int quantumFinal = q;

        // Se cambia con el simulador "en pausa de lectura" para no hacerlo a mitad de un ciclo.
        simulador.leerEstado(() -> {
            if (esRR) {
                computador.getPlanificador().setPolitica(
                        FabricaPoliticas.crear(PoliticaPlanificacion.ROUND_ROBIN, quantumFinal));
            } else {
                computador.getPlanificador().setPolitica(FabricaPoliticas.crear(elegida));
            }
        });
    }

    private void cambiarQuantum() {
        if (ajustandoQuantum || comboPolitica.getSelectedItem() != PoliticaPlanificacion.ROUND_ROBIN) {
            return;
        }
        int nuevoQ = (Integer) spinnerQuantum.getValue();
        if (nuevoQ <= 0) {
            return;
        }
        simulador.leerEstado(() -> computador.getPlanificador().setQuantum(nuevoQ));
    }

    /** Lista de Swing que solo se redibuja cuando su contenido cambió. */
    private static final class ListaVisual {
        private final DefaultListModel<String> modelo = new DefaultListModel<>();
        private final JList<String> componente = new JList<>(modelo);
        private String[] ultimo = new String[0];

        private static boolean iguales(String[] a, String[] b) {
            if (a.length != b.length) {
                return false;
            }
            for (int i = 0; i < a.length; i++) {
                if (a[i] == null && b[i] == null) {
                    continue;
                }
                if (a[i] == null || b[i] == null) {
                    return false;
                }
                if (!a[i].equals(b[i])) {
                    return false;
                }
            }
            return true;
        }

        void mostrar(String[] nuevas) {
            if (nuevas == null) {
                nuevas = new String[0];
            }
            if (iguales(ultimo, nuevas)) {
                return;
            }
            ultimo = nuevas;
            modelo.clear();
            for (String s : nuevas) {
                if (s != null) {
                    modelo.addElement(s);
                }
            }
        }

        void forzar(String[] nuevas) {
            if (nuevas == null) {
                nuevas = new String[0];
            }
            ultimo = nuevas;
            modelo.clear();
            for (String s : nuevas) {
                if (s != null) {
                    modelo.addElement(s);
                }
            }
        }
    }
}
