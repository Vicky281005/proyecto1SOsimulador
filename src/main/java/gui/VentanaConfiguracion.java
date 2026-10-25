package gui;

import config.ConfigBuffer;
import config.ConfigComputador;
import config.ConfigProceso;
import config.ConfiguracionSimulacion;
import config.FormatoPersistencia;
import config.GestorPersistencia;
import enums.PoliticaPlanificacion;
import enums.TipoProceso;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import simulacion.Simulador;

/**
 * Vista de Configuración de ÁvilaOS (feat/config).
 *
 * <p>Permite configurar los parámetros globales, computadores, buffers y procesos iniciales
 * de la simulación. Ofrece exportación e importación en formatos JSON y CSV, validaciones
 * en caliente sin interrumpir el flujo, y el lanzamiento de la simulación hacia la Vista 2.</p>
 */
public class VentanaConfiguracion extends JFrame {

    private ConfiguracionSimulacion config;
    private final JFrame ventanaPadre;

    // Campos de parámetros globales
    private final JTextField txtDuracionCiclo = new JTextField(8);
    private final JTextField txtLatenciaRed = new JTextField(8);

    // Modelos de tabla propios
    private final ComputadoresTableModel tablaComputadoresModel;
    private final BuffersTableModel tablaBuffersModel;
    private final ProcesosTableModel tablaProcesosModel;

    private final JTable tblComputadores;
    private final JTable tblBuffers;
    private final JTable tblProcesos;

    public VentanaConfiguracion() {
        this(null, null);
    }

    public VentanaConfiguracion(JFrame ventanaPadre) {
        this(ventanaPadre, null);
    }

    public VentanaConfiguracion(JFrame ventanaPadre, Simulador simuladorExistente) {
        super("ÁvilaOS – Configuración de Simulación y Clúster");
        this.ventanaPadre = ventanaPadre;

        if (simuladorExistente != null) {
            this.config = ConfiguracionSimulacion.desdeSimulador(simuladorExistente);
        } else {
            this.config = ConfiguracionSimulacion.crearPorDefecto();
        }

        tablaComputadoresModel = new ComputadoresTableModel(config.getComputadores());
        tablaBuffersModel = new BuffersTableModel(config.getBuffers());
        tablaProcesosModel = new ProcesosTableModel(config.getProcesos());

        tblComputadores = new JTable(tablaComputadoresModel);
        tblBuffers = new JTable(tablaBuffersModel);
        tblProcesos = new JTable(tablaProcesosModel);

        inicializarInterfaz();
        actualizarCamposGlobales();

        setDefaultCloseOperation(ventanaPadre == null ? EXIT_ON_CLOSE : DISPOSE_ON_CLOSE);
        setSize(920, 680);
        setMinimumSize(new Dimension(800, 560));
        setLocationRelativeTo(ventanaPadre);
    }

    private void inicializarInterfaz() {
        setLayout(new BorderLayout(8, 8));

        // 1. Panel Superior: Parámetros Globales
        JPanel pnlGlobal = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        pnlGlobal.setBorder(BorderFactory.createTitledBorder("Parámetros Globales de la Simulación"));
        pnlGlobal.add(new JLabel("Duración del ciclo (ms):"));
        pnlGlobal.add(txtDuracionCiclo);
        pnlGlobal.add(Box.createHorizontalStrut(15));
        pnlGlobal.add(new JLabel("Latencia de red (ciclos):"));
        pnlGlobal.add(txtLatenciaRed);

        // 2. Panel Central: Pestañas de Computadores, Buffers y Procesos
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Computadores", crearPanelComputadores());
        tabbedPane.addTab("Buffers Acotados", crearPanelBuffers());
        tabbedPane.addTab("Carga Inicial de Procesos", crearPanelProcesos());
        tabbedPane.addChangeListener(e -> {
            if (tabbedPane.getSelectedIndex() == 2) {
                tablaProcesosModel.fireTableDataChanged();
            }
        });

        // 3. Panel Inferior: Botones de Persistencia y Ejecución
        JPanel pnlBotones = new JPanel(new BorderLayout(10, 10));
        pnlBotones.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 10));

        JPanel pnlPersistencia = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JButton btnCargar = new JButton("Cargar Configuración…");
        JButton btnGuardar = new JButton("Guardar Configuración…");
        JButton btnDefecto = new JButton("Restablecer por Defecto");

        btnCargar.addActionListener(e -> cargarArchivo());
        btnGuardar.addActionListener(e -> guardarArchivo());
        btnDefecto.addActionListener(e -> restablecerPorDefecto());

        pnlPersistencia.add(btnCargar);
        pnlPersistencia.add(btnGuardar);
        pnlPersistencia.add(btnDefecto);

        JPanel pnlIniciar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton btnIniciar = new JButton("Iniciar Simulación ►");
        btnIniciar.setFont(btnIniciar.getFont().deriveFont(java.awt.Font.BOLD, 13f));
        btnIniciar.addActionListener(e -> iniciarSimulacion());
        pnlIniciar.add(btnIniciar);

        pnlBotones.add(pnlPersistencia, BorderLayout.WEST);
        pnlBotones.add(pnlIniciar, BorderLayout.EAST);

        add(pnlGlobal, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);
        add(pnlBotones, BorderLayout.SOUTH);
    }

    private JPanel crearPanelComputadores() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        tblComputadores.setFillsViewportHeight(true);
        tblComputadores.setRowHeight(26);
        configurarEditoresTablaComputadores();

        panel.add(new JScrollPane(tblComputadores), BorderLayout.CENTER);

        JPanel pnlAcciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        JButton btnAgregar = new JButton("Agregar Computador");
        JButton btnEliminar = new JButton("Eliminar Computador");

        btnAgregar.addActionListener(e -> agregarComputador());
        btnEliminar.addActionListener(e -> eliminarComputador());

        pnlAcciones.add(btnAgregar);
        pnlAcciones.add(btnEliminar);
        panel.add(pnlAcciones, BorderLayout.SOUTH);
        return panel;
    }

    private void configurarEditoresTablaComputadores() {
        // Ajuste de anchos preferidos
        tblComputadores.getColumnModel().getColumn(0).setPreferredWidth(50);
        tblComputadores.getColumnModel().getColumn(0).setMaxWidth(70);
        tblComputadores.getColumnModel().getColumn(1).setPreferredWidth(140);
        tblComputadores.getColumnModel().getColumn(2).setPreferredWidth(110);
        tblComputadores.getColumnModel().getColumn(3).setPreferredWidth(230);
        tblComputadores.getColumnModel().getColumn(4).setPreferredWidth(110);

        // Columna 3: Desplegable (JComboBox) para Política de Planificación
        JComboBox<PoliticaPlanificacion> cbPoliticaEditor = new JComboBox<>(PoliticaPlanificacion.values());
        javax.swing.DefaultCellEditor editorPolitica = new javax.swing.DefaultCellEditor(cbPoliticaEditor);
        editorPolitica.setClickCountToStart(1); // 1 clic despliega las opciones
        tblComputadores.getColumnModel().getColumn(3).setCellEditor(editorPolitica);

        // Columna 4: Editor de texto numérico mayor a 0 con doble clic
        JTextField txtQuantumEditor = new JTextField();
        txtQuantumEditor.setHorizontalAlignment(JTextField.CENTER);
        javax.swing.DefaultCellEditor editorQuantum = new javax.swing.DefaultCellEditor(txtQuantumEditor);
        editorQuantum.setClickCountToStart(2); // Doble clic para editar
        tblComputadores.getColumnModel().getColumn(4).setCellEditor(editorQuantum);

        // Renderer para Columna 4 (Quantum): solo relevante y resaltado si la política es Round Robin
        tblComputadores.getColumnModel().getColumn(4).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                java.awt.Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(CENTER);
                ConfigComputador pc = (row >= 0 && row < config.getComputadores().getTamano())
                        ? config.getComputadores().obtener(row) : null;
                if (pc != null && pc.getPolitica() != PoliticaPlanificacion.ROUND_ROBIN) {
                    setText("- (solo RR)");
                    if (!isSelected) {
                        comp.setForeground(java.awt.Color.GRAY);
                    }
                } else {
                    if (!isSelected) {
                        comp.setForeground(table.getForeground());
                    }
                }
                return comp;
            }
        });
    }

    private JPanel crearPanelBuffers() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        tblBuffers.setFillsViewportHeight(true);
        tblBuffers.setRowHeight(24);
        panel.add(new JScrollPane(tblBuffers), BorderLayout.CENTER);

        JPanel pnlAcciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        JButton btnAgregar = new JButton("Agregar Buffer");
        JButton btnEliminar = new JButton("Eliminar Buffer");

        btnAgregar.addActionListener(e -> agregarBuffer());
        btnEliminar.addActionListener(e -> eliminarBuffer());

        pnlAcciones.add(btnAgregar);
        pnlAcciones.add(btnEliminar);
        panel.add(pnlAcciones, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel crearPanelProcesos() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        tblProcesos.setFillsViewportHeight(true);
        tblProcesos.setRowHeight(24);

        // Renderer para Columna 4 (Quantum RR): resalta el valor numérico o muestra '-' si no aplica
        tblProcesos.getColumnModel().getColumn(4).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                java.awt.Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(CENTER);
                if ("-".equals(value)) {
                    if (!isSelected) {
                        comp.setForeground(java.awt.Color.GRAY);
                    }
                } else {
                    if (!isSelected) {
                        comp.setForeground(table.getForeground());
                    }
                }
                return comp;
            }
        });

        panel.add(new JScrollPane(tblProcesos), BorderLayout.CENTER);

        JPanel pnlAcciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        JButton btnAgregar = new JButton("Agregar Proceso");
        JButton btnEliminar = new JButton("Eliminar Proceso");

        btnAgregar.addActionListener(e -> agregarProceso());
        btnEliminar.addActionListener(e -> eliminarProceso());

        pnlAcciones.add(btnAgregar);
        pnlAcciones.add(btnEliminar);
        panel.add(pnlAcciones, BorderLayout.SOUTH);
        return panel;
    }

    // =========================================================================
    // ACCIONES DE TABLAS
    // =========================================================================

    private void agregarComputador() {
        sincronizarCamposGlobales();
        int nuevoId = 1;
        Iterador<ConfigComputador> it = config.getComputadores().iterador();
        while (it.haySiguiente()) {
            nuevoId = Math.max(nuevoId, it.siguiente().getId() + 1);
        }

        JTextField txtNombre = new JTextField("PC-" + nuevoId, 10);
        JTextField txtRam = new JTextField("1024", 8);
        JComboBox<PoliticaPlanificacion> cbPolitica = new JComboBox<>(PoliticaPlanificacion.values());
        JTextField txtQuantum = new JTextField("2", 5);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; form.add(new JLabel("Nombre:"), gbc);
        gbc.gridx = 1; form.add(txtNombre, gbc);
        gbc.gridx = 0; gbc.gridy = 1; form.add(new JLabel("RAM (KB):"), gbc);
        gbc.gridx = 1; form.add(txtRam, gbc);
        gbc.gridx = 0; gbc.gridy = 2; form.add(new JLabel("Política inicial:"), gbc);
        gbc.gridx = 1; form.add(cbPolitica, gbc);
        gbc.gridx = 0; gbc.gridy = 3; form.add(new JLabel("Quantum (RR):"), gbc);
        gbc.gridx = 1; form.add(txtQuantum, gbc);

        int res = JOptionPane.showConfirmDialog(this, form, "Nuevo Computador",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            String nom = txtNombre.getText().trim();
            if (nom.isEmpty()) {
                JOptionPane.showMessageDialog(this, "El nombre del computador no puede estar vacío.",
                        "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                long ram = Long.parseLong(txtRam.getText().trim());
                int q = Integer.parseInt(txtQuantum.getText().trim());
                if (ram <= 0 || q <= 0) {
                    JOptionPane.showMessageDialog(this, "La RAM y el Quantum deben ser números enteros positivos.",
                            "Validación", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                ConfigComputador nuevoPc = new ConfigComputador(nuevoId, nom, ram,
                        (PoliticaPlanificacion) cbPolitica.getSelectedItem(), q);
                config.getComputadores().agregar(nuevoPc);
                tablaComputadoresModel.fireTableDataChanged();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Por favor ingrese valores numéricos válidos.",
                        "Validación", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void eliminarComputador() {
        int fila = tblComputadores.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un computador de la tabla para eliminar.",
                    "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (config.getComputadores().getTamano() <= 2) {
            JOptionPane.showMessageDialog(this, "ÁvilaOS requiere un mínimo de 2 computadores para la simulación distribuida.",
                    "Restricción de Simulación", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ConfigComputador pc = config.getComputadores().obtener(fila);
        int conf = JOptionPane.showConfirmDialog(this,
                "¿Está seguro de eliminar '" + pc.getNombre() + "'? También se podrían invalidar buffers y procesos vinculados.",
                "Confirmar Eliminación", JOptionPane.YES_NO_OPTION);
        if (conf == JOptionPane.YES_OPTION) {
            config.getComputadores().eliminarEn(fila);
            tablaComputadoresModel.fireTableDataChanged();
        }
    }

    private void agregarBuffer() {
        if (config.getComputadores().estaVacia()) {
            JOptionPane.showMessageDialog(this, "Primero debe configurar al menos un computador.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int proximoId = config.getBuffers().getTamano() + 1;
        JTextField txtId = new JTextField("BUF-" + proximoId, 10);
        JTextField txtCap = new JTextField("5", 6);

        JComboBox<ConfigComputador> cbAnfitrion = new JComboBox<>();
        Iterador<ConfigComputador> it = config.getComputadores().iterador();
        while (it.haySiguiente()) {
            cbAnfitrion.addItem(it.siguiente());
        }

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; form.add(new JLabel("ID Buffer:"), gbc);
        gbc.gridx = 1; form.add(txtId, gbc);
        gbc.gridx = 0; gbc.gridy = 1; form.add(new JLabel("Capacidad (elementos):"), gbc);
        gbc.gridx = 1; form.add(txtCap, gbc);
        gbc.gridx = 0; gbc.gridy = 2; form.add(new JLabel("Computador Anfitrión:"), gbc);
        gbc.gridx = 1; form.add(cbAnfitrion, gbc);

        int res = JOptionPane.showConfirmDialog(this, form, "Nuevo Buffer Acotado",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            String idBuf = txtId.getText().trim();
            if (idBuf.isEmpty()) {
                JOptionPane.showMessageDialog(this, "El ID del buffer no puede estar vacío.",
                        "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (config.buscarBufferPorId(idBuf) != null) {
                JOptionPane.showMessageDialog(this, "Ya existe un buffer con el ID '" + idBuf + "'.",
                        "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                int cap = Integer.parseInt(txtCap.getText().trim());
                if (cap <= 0) {
                    JOptionPane.showMessageDialog(this, "La capacidad debe ser mayor que 0.",
                            "Validación", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                ConfigComputador anf = (ConfigComputador) cbAnfitrion.getSelectedItem();
                config.getBuffers().agregar(new ConfigBuffer(idBuf, cap, anf.getId(), 4));
                tablaBuffersModel.fireTableDataChanged();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "La capacidad debe ser un número entero válido.",
                        "Validación", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void eliminarBuffer() {
        int fila = tblBuffers.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un buffer de la tabla para eliminar.",
                    "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        config.getBuffers().eliminarEn(fila);
        tablaBuffersModel.fireTableDataChanged();
    }

    private void agregarProceso() {
        if (config.getComputadores().estaVacia()) {
            JOptionPane.showMessageDialog(this, "Primero debe configurar al menos un computador.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int nuevoId = config.getProcesos().getTamano() + 1;
        JTextField txtNombre = new JTextField("P" + nuevoId, 10);
        JComboBox<TipoProceso> cbTipo = new JComboBox<>(TipoProceso.values());
        JComboBox<ConfigComputador> cbPc = new JComboBox<>();
        Iterador<ConfigComputador> itPc = config.getComputadores().iterador();
        while (itPc.haySiguiente()) {
            cbPc.addItem(itPc.siguiente());
        }

        JLabel lblInfoPoliticaPc = new JLabel();
        lblInfoPoliticaPc.setFont(lblInfoPoliticaPc.getFont().deriveFont(java.awt.Font.ITALIC, 11f));
        Runnable actualizarInfoPc = () -> {
            ConfigComputador pcSel = (ConfigComputador) cbPc.getSelectedItem();
            if (pcSel != null) {
                if (pcSel.getPolitica() == PoliticaPlanificacion.ROUND_ROBIN) {
                    lblInfoPoliticaPc.setText("Planificación: Round Robin (Quantum: " + pcSel.getQuantum() + " ciclos)");
                    lblInfoPoliticaPc.setForeground(new java.awt.Color(0, 120, 40));
                } else {
                    lblInfoPoliticaPc.setText("Planificación: " + pcSel.getPolitica().getEtiqueta() + " (sin quantum)");
                    lblInfoPoliticaPc.setForeground(java.awt.Color.GRAY);
                }
            }
        };
        cbPc.addActionListener(e -> actualizarInfoPc.run());
        actualizarInfoPc.run();

        JTextField txtInstr = new JTextField("10", 6);
        JTextField txtRam = new JTextField("200", 6);
        JTextField txtPrio = new JTextField("1", 4);
        JTextField txtDl = new JTextField("100", 6);
        JTextField txtPeriodoES = new JTextField("0", 4);

        JComboBox<String> cbBuffer = new JComboBox<>();
        cbBuffer.addItem("(Ninguno)");
        Iterador<ConfigBuffer> itB = config.getBuffers().iterador();
        while (itB.haySiguiente()) {
            cbBuffer.addItem(itB.siguiente().getId());
        }

        JTextField txtCiclosAcceso = new JTextField("1", 4);
        JTextField txtElemReq = new JTextField("1", 4);

        // Habilitar / deshabilitar según tipo
        Runnable refrescarCamposTipo = () -> {
            TipoProceso tp = (TipoProceso) cbTipo.getSelectedItem();
            boolean esIO = tp == TipoProceso.IO_BOUND;
            boolean esProdCons = tp == TipoProceso.PRODUCTOR || tp == TipoProceso.CONSUMIDOR;
            txtPeriodoES.setEnabled(esIO);
            cbBuffer.setEnabled(esProdCons);
            txtCiclosAcceso.setEnabled(esProdCons);
            txtElemReq.setEnabled(esProdCons);
        };
        cbTipo.addActionListener(e -> refrescarCamposTipo.run());
        refrescarCamposTipo.run();

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 4, 3, 4);
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridx = 0; g.gridy = 0; form.add(new JLabel("Nombre:"), g);
        g.gridx = 1; form.add(txtNombre, g);
        g.gridx = 0; g.gridy = 1; form.add(new JLabel("Tipo:"), g);
        g.gridx = 1; form.add(cbTipo, g);
        g.gridx = 0; g.gridy = 2; form.add(new JLabel("Computador Destino:"), g);
        g.gridx = 1; form.add(cbPc, g);
        g.gridx = 0; g.gridy = 3; form.add(new JLabel(""), g);
        g.gridx = 1; form.add(lblInfoPoliticaPc, g);
        g.gridx = 0; g.gridy = 4; form.add(new JLabel("Instrucciones:"), g);
        g.gridx = 1; form.add(txtInstr, g);
        g.gridx = 0; g.gridy = 5; form.add(new JLabel("Memoria RAM (KB):"), g);
        g.gridx = 1; form.add(txtRam, g);
        g.gridx = 0; g.gridy = 6; form.add(new JLabel("Prioridad (1-10):"), g);
        g.gridx = 1; form.add(txtPrio, g);
        g.gridx = 0; g.gridy = 7; form.add(new JLabel("Deadline (ciclos):"), g);
        g.gridx = 1; form.add(txtDl, g);
        g.gridx = 0; g.gridy = 8; form.add(new JLabel("Periodo E/S (I/O):"), g);
        g.gridx = 1; form.add(txtPeriodoES, g);
        g.gridx = 0; g.gridy = 9; form.add(new JLabel("Buffer Target:"), g);
        g.gridx = 1; form.add(cbBuffer, g);
        g.gridx = 0; g.gridy = 10; form.add(new JLabel("Ciclos por acceso:"), g);
        g.gridx = 1; form.add(txtCiclosAcceso, g);
        g.gridx = 0; g.gridy = 11; form.add(new JLabel("Elementos requeridos:"), g);
        g.gridx = 1; form.add(txtElemReq, g);

        int res = JOptionPane.showConfirmDialog(this, form, "Nuevo Proceso",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            String nom = txtNombre.getText().trim();
            if (nom.isEmpty()) {
                JOptionPane.showMessageDialog(this, "El nombre del proceso es obligatorio.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                TipoProceso tipo = (TipoProceso) cbTipo.getSelectedItem();
                ConfigComputador pcSel = (ConfigComputador) cbPc.getSelectedItem();
                int instr = Integer.parseInt(txtInstr.getText().trim());
                long ram = Long.parseLong(txtRam.getText().trim());
                int prio = Integer.parseInt(txtPrio.getText().trim());
                long dl = Long.parseLong(txtDl.getText().trim());
                int perES = Integer.parseInt(txtPeriodoES.getText().trim());
                int ciclosAcc = Integer.parseInt(txtCiclosAcceso.getText().trim());
                int elemReq = Integer.parseInt(txtElemReq.getText().trim());
                String bufId = (String) cbBuffer.getSelectedItem();
                if ("(Ninguno)".equals(bufId)) {
                    bufId = "";
                }

                if (tipo == TipoProceso.PRODUCTOR || tipo == TipoProceso.CONSUMIDOR) {
                    if (bufId.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "Los procesos Productor/Consumidor requieren seleccionar un buffer.",
                                "Validación", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                }

                ConfigProceso cp = new ConfigProceso(nuevoId, nom, tipo, pcSel.getId(),
                        instr, ram, prio, dl, perES, bufId, ciclosAcc, elemReq);
                config.getProcesos().agregar(cp);
                tablaProcesosModel.fireTableDataChanged();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Por favor ingrese valores numéricos enteros válidos en todos los campos.",
                        "Validación", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void eliminarProceso() {
        int fila = tblProcesos.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un proceso de la tabla para eliminar.",
                    "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        config.getProcesos().eliminarEn(fila);
        tablaProcesosModel.fireTableDataChanged();
    }

    // =========================================================================
    // PERSISTENCIA Y EJECUCIÓN
    // =========================================================================

    private void sincronizarCamposGlobales() {
        try {
            long dur = Long.parseLong(txtDuracionCiclo.getText().trim());
            int lat = Integer.parseInt(txtLatenciaRed.getText().trim());
            config.setDuracionCicloMs(dur);
            config.setLatenciaRedCiclos(lat);
        } catch (NumberFormatException ignored) {
        }
    }

    private void actualizarCamposGlobales() {
        txtDuracionCiclo.setText(String.valueOf(config.getDuracionCicloMs()));
        txtLatenciaRed.setText(String.valueOf(config.getLatenciaRedCiclos()));
        tablaComputadoresModel.setLista(config.getComputadores());
        tablaBuffersModel.setLista(config.getBuffers());
        tablaProcesosModel.setLista(config.getProcesos());
    }

    private void guardarArchivo() {
        sincronizarCamposGlobales();
        ListaEnlazada<String> errs = config.validar();
        if (!errs.estaVacia()) {
            StringBuilder sb = new StringBuilder("No se puede guardar porque existen inconsistencias:\n\n");
            Iterador<String> it = errs.iterador();
            while (it.haySiguiente()) {
                sb.append(" • ").append(it.siguiente()).append("\n");
            }
            JOptionPane.showMessageDialog(this, sb.toString(), "Inconsistencias de Configuración", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Guardar Configuración de ÁvilaOS");
        FileNameExtensionFilter filtroJson = new FileNameExtensionFilter("Archivos JSON (*.json)", "json");
        FileNameExtensionFilter filtroCsv = new FileNameExtensionFilter("Archivos CSV (*.csv)", "csv");
        chooser.addChoosableFileFilter(filtroJson);
        chooser.addChoosableFileFilter(filtroCsv);
        chooser.setFileFilter(filtroJson);

        int opt = chooser.showSaveDialog(this);
        if (opt == JFileChooser.APPROVE_OPTION) {
            File archivo = chooser.getSelectedFile();
            FormatoPersistencia formato = (chooser.getFileFilter() == filtroCsv
                    || archivo.getName().toLowerCase().endsWith(".csv"))
                    ? FormatoPersistencia.CSV : FormatoPersistencia.JSON;

            if (!archivo.getName().toLowerCase().endsWith("." + formato.getExtension())) {
                archivo = new File(archivo.getParentFile(), archivo.getName() + "." + formato.getExtension());
            }

            try {
                GestorPersistencia.guardarConfiguracion(config, archivo, formato);
                JOptionPane.showMessageDialog(this, "Configuración guardada exitosamente en:\n" + archivo.getAbsolutePath(),
                        "Guardar Configuración", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al guardar el archivo:\n" + ex.getMessage(),
                        "Error de Persistencia", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void cargarArchivo() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Cargar Configuración de ÁvilaOS");
        FileNameExtensionFilter filtroJson = new FileNameExtensionFilter("Archivos JSON (*.json)", "json");
        FileNameExtensionFilter filtroCsv = new FileNameExtensionFilter("Archivos CSV (*.csv)", "csv");
        chooser.addChoosableFileFilter(filtroJson);
        chooser.addChoosableFileFilter(filtroCsv);
        chooser.setFileFilter(filtroJson);

        int opt = chooser.showOpenDialog(this);
        if (opt == JFileChooser.APPROVE_OPTION) {
            File archivo = chooser.getSelectedFile();
            FormatoPersistencia formato = (chooser.getFileFilter() == filtroCsv
                    || archivo.getName().toLowerCase().endsWith(".csv"))
                    ? FormatoPersistencia.CSV : FormatoPersistencia.JSON;

            try {
                ConfiguracionSimulacion cargada = GestorPersistencia.cargarConfiguracion(archivo, formato);
                this.config = cargada;
                actualizarCamposGlobales();
                JOptionPane.showMessageDialog(this, "Configuración cargada exitosamente desde:\n" + archivo.getName(),
                        "Cargar Configuración", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al cargar la configuración:\n" + ex.getMessage(),
                        "Error de Lectura", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void restablecerPorDefecto() {
        int conf = JOptionPane.showConfirmDialog(this,
                "¿Desea restablecer todos los valores a la configuración por defecto de ÁvilaOS?",
                "Restablecer", JOptionPane.YES_NO_OPTION);
        if (conf == JOptionPane.YES_OPTION) {
            this.config = ConfiguracionSimulacion.crearPorDefecto();
            actualizarCamposGlobales();
        }
    }

    private void iniciarSimulacion() {
        sincronizarCamposGlobales();

        ListaEnlazada<String> errores = config.validar();
        if (!errores.estaVacia()) {
            StringBuilder sb = new StringBuilder("Corrija los siguientes errores antes de iniciar la simulación:\n\n");
            Iterador<String> it = errores.iterador();
            while (it.haySiguiente()) {
                sb.append(" • ").append(it.siguiente()).append("\n");
            }
            JOptionPane.showMessageDialog(this, sb.toString(), "Configuración Inválida", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            Simulador sim = config.aSimulador();
            if (ventanaPadre != null) {
                ventanaPadre.dispose();
            }
            this.dispose();

            SwingUtilities.invokeLater(() -> {
                VentanaEstado vista2 = new VentanaEstado(sim);
                vista2.setVisible(true);
            });
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo inicializar la simulación:\n" + ex.getMessage(),
                    "Error al Iniciar", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================================
    // MODELOS DE TABLA PROPIOS (SIN java.util)
    // =========================================================================

    private static boolean modoPrueba = false;

    public static void setModoPrueba(boolean prueba) {
        modoPrueba = prueba;
    }

    public ComputadoresTableModel getTablaComputadoresModel() {
        return tablaComputadoresModel;
    }

    public JTable getTblComputadores() {
        return tblComputadores;
    }

    public class ComputadoresTableModel extends AbstractTableModel {
        private final String[] columnas = {"ID", "Nombre", "RAM (KB)", "Política Planificación", "Quantum"};
        private ListaEnlazada<ConfigComputador> lista;

        public ComputadoresTableModel(ListaEnlazada<ConfigComputador> lista) {
            this.lista = lista;
        }

        public void setLista(ListaEnlazada<ConfigComputador> lista) {
            this.lista = lista;
            fireTableDataChanged();
        }

        public ListaEnlazada<ConfigComputador> getLista() {
            return lista;
        }

        @Override public int getRowCount() { return lista == null ? 0 : lista.getTamano(); }
        @Override public int getColumnCount() { return columnas.length; }
        @Override public String getColumnName(int c) { return columnas[c]; }

        @Override
        public boolean isCellEditable(int r, int c) {
            if (c == 1 || c == 2 || c == 3) {
                return true;
            }
            if (c == 4) {
                ConfigComputador pc = lista.obtener(r);
                return pc != null && pc.getPolitica() == PoliticaPlanificacion.ROUND_ROBIN;
            }
            return false; // Columna 0 (ID) no editable directamente
        }

        @Override
        public Object getValueAt(int r, int c) {
            ConfigComputador pc = lista.obtener(r);
            if (pc == null) return null;
            return switch (c) {
                case 0 -> pc.getId();
                case 1 -> pc.getNombre();
                case 2 -> pc.getMemoriaTotal();
                case 3 -> pc.getPolitica();
                case 4 -> (pc.getPolitica() == PoliticaPlanificacion.ROUND_ROBIN) ? pc.getQuantum() : "-";
                default -> null;
            };
        }

        private void mostrarAviso(String mensaje) {
            if (!modoPrueba && !java.awt.GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(VentanaConfiguracion.this, mensaje,
                        "Validación", JOptionPane.WARNING_MESSAGE);
            } else {
                System.err.println("[Validación GUI] " + mensaje);
            }
        }

        @Override
        public void setValueAt(Object aValue, int r, int c) {
            ConfigComputador pc = lista.obtener(r);
            if (pc == null || aValue == null) return;
            switch (c) {
                case 1 -> {
                    String nom = aValue.toString().trim();
                    if (!nom.isEmpty()) {
                        pc.setNombre(nom);
                        fireTableCellUpdated(r, c);
                    } else {
                        mostrarAviso("El nombre del computador no puede estar vacío.");
                    }
                }
                case 2 -> {
                    try {
                        long ram = Long.parseLong(aValue.toString().trim());
                        if (ram > 0) {
                            pc.setMemoriaTotal(ram);
                            fireTableCellUpdated(r, c);
                        } else {
                            mostrarAviso("La memoria RAM debe ser mayor a 0 KB.");
                        }
                    } catch (NumberFormatException e) {
                        mostrarAviso("La memoria RAM debe ser un número entero válido mayor a 0.");
                    }
                }
                case 3 -> {
                    PoliticaPlanificacion nuevaPol;
                    if (aValue instanceof PoliticaPlanificacion pol) {
                        nuevaPol = pol;
                    } else {
                        nuevaPol = GestorPersistencia.parsearPolitica(aValue.toString());
                    }
                    pc.setPolitica(nuevaPol);
                    if (nuevaPol == PoliticaPlanificacion.ROUND_ROBIN && pc.getQuantum() <= 0) {
                        pc.setQuantum(2);
                    }
                    // Notificar actualización de la fila completa para refrescar la columna Quantum
                    fireTableRowsUpdated(r, r);
                }
                case 4 -> {
                    if (pc.getPolitica() == PoliticaPlanificacion.ROUND_ROBIN) {
                        try {
                            int q = Integer.parseInt(aValue.toString().trim());
                            if (q > 0) {
                                pc.setQuantum(q);
                                fireTableCellUpdated(r, c);
                            } else {
                                mostrarAviso("El Quantum debe ser un número entero mayor a 0.");
                                fireTableCellUpdated(r, c);
                            }
                        } catch (NumberFormatException e) {
                            mostrarAviso("El Quantum debe ser un número entero mayor a 0.");
                            fireTableCellUpdated(r, c);
                        }
                    }
                }
            }
        }
    }

    private static class BuffersTableModel extends AbstractTableModel {
        private final String[] columnas = {"ID Buffer", "Capacidad", "PC Anfitrión (ID)", "Tamaño Elemento (B)"};
        private ListaEnlazada<ConfigBuffer> lista;

        public BuffersTableModel(ListaEnlazada<ConfigBuffer> lista) {
            this.lista = lista;
        }

        public void setLista(ListaEnlazada<ConfigBuffer> lista) {
            this.lista = lista;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return lista == null ? 0 : lista.getTamano(); }
        @Override public int getColumnCount() { return columnas.length; }
        @Override public String getColumnName(int c) { return columnas[c]; }

        @Override
        public Object getValueAt(int r, int c) {
            ConfigBuffer buf = lista.obtener(r);
            if (buf == null) return null;
            return switch (c) {
                case 0 -> buf.getId();
                case 1 -> buf.getCapacidad();
                case 2 -> buf.getIdComputadorAnfitrion();
                case 3 -> buf.getTamanoElemento();
                default -> null;
            };
        }
    }

    public ProcesosTableModel getTablaProcesosModel() {
        return tablaProcesosModel;
    }

    public JTable getTblProcesos() {
        return tblProcesos;
    }

    public class ProcesosTableModel extends AbstractTableModel {
        private final String[] columnas = {
            "ID", "Nombre", "Tipo", "PC Destino", "Quantum (RR)", "Instrucciones",
            "RAM (KB)", "Prioridad", "Deadline", "Periodo E/S", "Buffer Target",
            "Ciclos/Acceso", "Elem. Req."
        };
        private ListaEnlazada<ConfigProceso> lista;

        public ProcesosTableModel(ListaEnlazada<ConfigProceso> lista) {
            this.lista = lista;
        }

        public void setLista(ListaEnlazada<ConfigProceso> lista) {
            this.lista = lista;
            fireTableDataChanged();
        }

        public ListaEnlazada<ConfigProceso> getLista() {
            return lista;
        }

        @Override public int getRowCount() { return lista == null ? 0 : lista.getTamano(); }
        @Override public int getColumnCount() { return columnas.length; }
        @Override public String getColumnName(int c) { return columnas[c]; }

        @Override
        public Object getValueAt(int r, int c) {
            ConfigProceso p = lista.obtener(r);
            if (p == null) return null;
            return switch (c) {
                case 0 -> p.getId();
                case 1 -> p.getNombre();
                case 2 -> p.getTipo();
                case 3 -> {
                    ConfigComputador pc = config.buscarComputadorPorId(p.getComputadorAsignado());
                    yield (pc != null) ? (p.getComputadorAsignado() + " (" + pc.getNombre() + ")")
                            : String.valueOf(p.getComputadorAsignado());
                }
                case 4 -> {
                    ConfigComputador pc = config.buscarComputadorPorId(p.getComputadorAsignado());
                    if (pc != null && pc.getPolitica() == PoliticaPlanificacion.ROUND_ROBIN) {
                        yield pc.getQuantum();
                    } else {
                        yield "-";
                    }
                }
                case 5 -> p.getInstrucciones();
                case 6 -> p.getMemoria();
                case 7 -> p.getPrioridad();
                case 8 -> p.getDeadline();
                case 9 -> (p.getPeriodoES() > 0) ? p.getPeriodoES() : "-";
                case 10 -> p.getIdBufferTarget().isEmpty() ? "-" : p.getIdBufferTarget();
                case 11 -> (p.getTipo() == TipoProceso.PRODUCTOR || p.getTipo() == TipoProceso.CONSUMIDOR) ? p.getCiclosPorAcceso() : "-";
                case 12 -> (p.getTipo() == TipoProceso.PRODUCTOR || p.getTipo() == TipoProceso.CONSUMIDOR) ? p.getElementosRequeridos() : "-";
                default -> null;
            };
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VentanaConfiguracion().setVisible(true));
    }
}
