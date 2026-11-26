package gui;

import enums.ModoUsuario;
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
    private final ListaVisual listosSuspendidos = new ListaVisual();
    private final ListaVisual bloqueadosSuspendidos = new ListaVisual();
    private final ListaVisual terminados = new ListaVisual();

    /** Evita que seleccionar la política por código dispare un cambio real. */
    private boolean ajustandoCombo;
    /** Evita que modificar el spinner por código dispare un cambio real. */
    private boolean ajustandoQuantum;
    /** Controla si el usuario activo tiene permiso para cambiar políticas de CPU. */
    private ModoUsuario modoUsuario = ModoUsuario.ADMINISTRADOR;

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
                computador.getNombre() + "  (RAM " + computador.getMemoriaTotal() + " bloques)"));

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

        // --- Centro: las seis colas del modelo de 7 estados ---
        JPanel colas = new JPanel(new GridLayout(2, 3, 6, 6));
        colas.add(envolver("Nuevos", nuevos));
        colas.add(envolver("Listos", listos));
        colas.add(envolver("Bloqueados (motivo)", bloqueados));
        colas.add(envolver("Listo/Suspendido", listosSuspendidos));
        colas.add(envolver("Bloqueado/Suspendido", bloqueadosSuspendidos));
        colas.add(envolver("Terminados", terminados));
        add(colas, BorderLayout.CENTER);
    }

    private JScrollPane envolver(String titulo, ListaVisual lista) {
        JScrollPane scroll = new JScrollPane(lista.componente);
        scroll.setBorder(BorderFactory.createTitledBorder(titulo));
        return scroll;
    }

    /** Snapshot inmutable del estado del computador capturado dentro de {@code leerEstado}. */
    static final class Snapshot {
        final boolean cpuOciosa;
        final enums.ModoCPU modoCpu;
        final String textoProceso;
        final String textoRegistros;
        final String textoDeadline;
        final long ramTotal;
        final long ramUsada;
        final String[] nuevos;
        final String[] listos;
        final String[] bloqueados;
        final String[] listosSuspendidos;
        final String[] bloqueadosSuspendidos;
        final String[] terminados;
        final boolean esRoundRobin;
        final int quantumRoundRobin;

        Snapshot(boolean cpuOciosa, enums.ModoCPU modoCpu,
                 String textoProceso, String textoRegistros, String textoDeadline,
                 long ramTotal, long ramUsada,
                 String[] nuevos, String[] listos, String[] bloqueados,
                 String[] listosSuspendidos, String[] bloqueadosSuspendidos, String[] terminados,
                 boolean esRoundRobin, int quantumRoundRobin) {
            this.cpuOciosa = cpuOciosa;
            this.modoCpu = modoCpu;
            this.textoProceso = textoProceso;
            this.textoRegistros = textoRegistros;
            this.textoDeadline = textoDeadline;
            this.ramTotal = ramTotal;
            this.ramUsada = ramUsada;
            this.nuevos = nuevos;
            this.listos = listos;
            this.bloqueados = bloqueados;
            this.listosSuspendidos = listosSuspendidos;
            this.bloqueadosSuspendidos = bloqueadosSuspendidos;
            this.terminados = terminados;
            this.esRoundRobin = esRoundRobin;
            this.quantumRoundRobin = quantumRoundRobin;
        }
    }

    /**
     * Captura un snapshot de solo lectura del computador (sin tocar componentes Swing).
     * Debe llamarse dentro de {@link Simulador#leerEstado(Runnable)}.
     */
    Snapshot capturarSnapshot() {
        CPU cpu = computador.getCpu();
        PCB actual = cpu.getProcesoActual();
        boolean ociosa = (actual == null);
        enums.ModoCPU modo = cpu.getModo();

        String txtProceso = ociosa
                ? "Proceso en CPU: —"
                : "Proceso en CPU: " + actual.getNombre() + " (id " + actual.getId() + ")";
        String txtRegistros = ociosa
                ? "PC: —   MAR: —   Prioridad: —"
                : "PC: " + actual.getPc() + "   MAR: " + actual.getMar()
                        + "   Prioridad: " + actual.getPrioridad();
        String txtDeadline = ociosa
                ? "Deadline: —   Restante: —"
                : "Deadline (ciclo): " + actual.getDeadlineAbsoluto()
                        + "   Restante: " + actual.getTiempoRestante();

        long total = computador.getMemoriaTotal();
        long usada = total - computador.getMemoriaDisponible();

        String[] lNuevos = lineas(computador.iteradorNuevos(), false, false);
        String[] lListos = lineas(computador.getPlanificador().iteradorListos(), false, false);
        String[] lBloqueados = lineas(computador.iteradorBloqueados(), true, false);
        String[] lListosSusp = lineas(computador.iteradorListosSuspendidos(), false, false);
        String[] lBloqSusp = lineas(computador.iteradorBloqueadosSuspendidos(), true, false);
        String[] lTerminados = lineas(computador.iteradorTerminados(), false, true);

        boolean esRR = computador.getPlanificador().getPolitica() instanceof planificacion.RoundRobin;
        int qRR = esRR ? ((planificacion.RoundRobin) computador.getPlanificador().getPolitica()).getQuantum() : 0;

        return new Snapshot(ociosa, modo, txtProceso, txtRegistros, txtDeadline,
                total, usada, lNuevos, lListos, lBloqueados, lListosSusp, lBloqSusp, lTerminados,
                esRR, qRR);
    }

    /**
     * Aplica un snapshot previamente capturado sobre los componentes Swing (fuera de {@code mutexEstado}).
     */
    void aplicarSnapshot(Snapshot snap) {
        if (snap == null) {
            return;
        }
        lblModo.setText("CPU: " + (snap.cpuOciosa ? "ociosa" : "ocupada")
                + "   |   Modo: " + snap.modoCpu);
        lblModo.setForeground(snap.cpuOciosa ? COLOR_OCIOSA
                : (snap.modoCpu == enums.ModoCPU.KERNEL ? COLOR_SO : COLOR_USUARIO));
        lblProceso.setText(snap.textoProceso);
        lblRegistros.setText(snap.textoRegistros);
        lblDeadline.setText(snap.textoDeadline);

        int pctRam = (snap.ramTotal > 0) ? (int) (snap.ramUsada * 100 / snap.ramTotal) : 0;
        barraRam.setValue(pctRam);
        barraRam.setString("RAM: " + snap.ramUsada + " / " + snap.ramTotal + " bloques");

        nuevos.mostrar(snap.nuevos);
        listos.mostrar(snap.listos);
        bloqueados.mostrar(snap.bloqueados);
        listosSuspendidos.mostrar(snap.listosSuspendidos);
        bloqueadosSuspendidos.mostrar(snap.bloqueadosSuspendidos);
        terminados.mostrar(snap.terminados);

        if (snap.esRoundRobin && snap.quantumRoundRobin > 0) {
            if (!ajustandoQuantum && ((Integer) spinnerQuantum.getValue()) != snap.quantumRoundRobin) {
                ajustandoQuantum = true;
                spinnerQuantum.setValue(snap.quantumRoundRobin);
                ajustandoQuantum = false;
            }
        }
        aplicarPermisosModo(snap.esRoundRobin);
    }

    /**
     * Refresca todo lo que muestra el panel con el estado actual del computador.
     */
    public void actualizar() {
        aplicarSnapshot(capturarSnapshot());
    }

    /** Habilita o restringe el cambio de política/quantum según el modo Administrador o Usuario. */
    public void setModoUsuario(ModoUsuario modo) {
        this.modoUsuario = (modo != null) ? modo : ModoUsuario.ADMINISTRADOR;
        aplicarPermisosModo();
    }

    private void aplicarPermisosModo() {
        boolean esRR = computador.getPlanificador().getPolitica() instanceof planificacion.RoundRobin;
        aplicarPermisosModo(esRR);
    }

    private void aplicarPermisosModo(boolean esRR) {
        boolean esAdmin = modoUsuario.esAdministrador();
        comboPolitica.setEnabled(esAdmin);
        comboPolitica.setToolTipText(esAdmin
                ? "Cambiar política de planificación de CPU"
                : "Solo permitido en Modo Administrador");
        lblQuantum.setEnabled(esAdmin && esRR);
        spinnerQuantum.setEnabled(esAdmin && esRR);
        spinnerQuantum.setToolTipText(esAdmin
                ? "Ajustar quantum de Round Robin"
                : "Solo permitido en Modo Administrador");
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
        aplicarPermisosModo();
    }

    private void cambiarPolitica() {
        if (ajustandoCombo || !modoUsuario.esAdministrador()) {
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
        if (ajustandoQuantum || !modoUsuario.esAdministrador()
                || comboPolitica.getSelectedItem() != PoliticaPlanificacion.ROUND_ROBIN) {
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
