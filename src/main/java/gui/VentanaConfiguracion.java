package gui;

import archivos.SistemaArchivos;
import config.ConfigComputador;
import config.ConfigProcesosDisco;
import config.ConfigProceso;
import config.ConfigSistemaArchivos;
import config.ConfiguracionSimulacion;
import config.FormatoPersistencia;
import config.GestorPersistencia;
import enums.ModoUsuario;
import enums.PoliticaDisco;
import enums.PoliticaPlanificacion;
import enums.TipoOperacionES;
import enums.TipoProceso;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
 * Vista de Configuración y Persistencia Dual de ÁvilaOS (Proyecto 2).
 *
 * <p>Gestiona la carga obligatoria de los dos archivos JSON del enunciado
 * (Sistema de Archivos/Disco y Procesos/Configuración), la exportación del estado
 * actual del sistema de archivos a JSON, el reinicio preservando el disco y la
 * edición de parámetros del computador único, disco y procesos sin colecciones
 * de {@code java.util}.</p>
 */
public class VentanaConfiguracion extends JFrame {

    private static volatile boolean modoPrueba = false;

    public static void setModoPrueba(boolean activo) {
        modoPrueba = activo;
    }

    private ConfiguracionSimulacion config;
    private final JFrame ventanaPadre;
    private final Simulador simuladorExistente;

    // Estado de los 2 JSON obligatorios
    private ConfigSistemaArchivos fsCargado;
    private ConfigProcesosDisco procCargado;
    private final JLabel lblEstadoJson1 = new JLabel("Pendiente: no se ha cargado el JSON del Sistema de Archivos.");
    private final JLabel lblEstadoJson2 = new JLabel("Pendiente: no se ha cargado el JSON de Procesos y Configuración.");
    private final JButton btnAplicarDosJson = new JButton("Aplicar ambos JSON y Abrir Simulación ►");
    private final JButton btnPreservarDisco = new JButton("Reiniciar Procesos Preservando Disco Actual");

    // Campos de parámetros globales y de disco
    private final JTextField txtDuracionCiclo = new JTextField(8);
    private final JTextField txtBloquesDisco = new JTextField(8);
    private final JTextField txtTamanoSwap = new JTextField(8);
    private final JTextField txtCabezalInicial = new JTextField(8);
    private final JTextField txtVelocidadCabezal = new JTextField(8);
    private final JComboBox<PoliticaDisco> comboPoliticaDisco = new JComboBox<>(PoliticaDisco.values());

    // Modelos de tabla propios
    private final ComputadoresTableModel tablaComputadoresModel;
    private final ProcesosTableModel tablaProcesosModel;

    private final JTable tblComputadores;
    private final JTable tblProcesos;

    public VentanaConfiguracion() {
        this(null, null);
    }

    public VentanaConfiguracion(JFrame ventanaPadre) {
        this(ventanaPadre, null);
    }

    public VentanaConfiguracion(JFrame ventanaPadre, Simulador simuladorExistente) {
        super("ÁvilaOS – Configuración y Carga de JSONs (Proyecto 2)");
        this.ventanaPadre = ventanaPadre;
        this.simuladorExistente = simuladorExistente;

        if (simuladorExistente != null) {
            this.config = ConfiguracionSimulacion.desdeSimulador(simuladorExistente);
            if (simuladorExistente.getServicioArchivos() instanceof SistemaArchivos sa
                    && simuladorExistente.isSistemaArchivosJsonCargado()) {
                this.fsCargado = ConfigSistemaArchivos.desdeSistemaArchivos(sa);
            }
        } else {
            this.config = ConfiguracionSimulacion.crearPorDefecto();
        }

        tablaComputadoresModel = new ComputadoresTableModel(config.getComputadores());
        tablaProcesosModel = new ProcesosTableModel(config.getProcesos());

        tblComputadores = new JTable(tablaComputadoresModel);
        tblProcesos = new JTable(tablaProcesosModel);

        inicializarInterfaz();
        actualizarCamposGlobales();
        actualizarEstadoJsonLabels();

        setDefaultCloseOperation(ventanaPadre == null ? EXIT_ON_CLOSE : DISPOSE_ON_CLOSE);
        setSize(940, 680);
        setMinimumSize(new Dimension(820, 560));
        setLocationRelativeTo(ventanaPadre);
    }

    private void inicializarInterfaz() {
        setLayout(new BorderLayout(8, 8));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("1. Carga de JSONs (Obligatoria)", crearPanelCargaDualJson());
        tabbedPane.addTab("2. Computador y Disco", crearPanelComputadorYDisco());
        tabbedPane.addTab("3. Procesos y Persistencia General", crearPanelProcesos());

        JPanel pnlInferior = new JPanel(new BorderLayout(10, 10));
        pnlInferior.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 10));

        JPanel pnlIzq = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JButton btnCargarCfg = new JButton("Importar Config JSON/CSV…");
        JButton btnGuardarCfg = new JButton("Exportar Config JSON/CSV…");
        JButton btnDefecto = new JButton("Restablecer por Defecto");
        btnCargarCfg.addActionListener(e -> cargarArchivoGeneral());
        btnGuardarCfg.addActionListener(e -> guardarArchivoGeneral());
        btnDefecto.addActionListener(e -> restablecerPorDefecto());
        pnlIzq.add(btnCargarCfg);
        pnlIzq.add(btnGuardarCfg);
        pnlIzq.add(btnDefecto);

        JPanel pnlDer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton btnIniciarManual = new JButton("Aplicar Config Manual ►");
        btnIniciarManual.setFont(btnIniciarManual.getFont().deriveFont(Font.BOLD, 12.5f));
        btnIniciarManual.addActionListener(e -> iniciarSimulacionManual());
        pnlDer.add(btnIniciarManual);

        pnlInferior.add(pnlIzq, BorderLayout.WEST);
        pnlInferior.add(pnlDer, BorderLayout.EAST);

        // Ocultar la barra manual en la Pestaña 1 para evitar confusiones con la carga dual obligatoria
        pnlInferior.setVisible(tabbedPane.getSelectedIndex() != 0);
        tabbedPane.addChangeListener(e -> pnlInferior.setVisible(tabbedPane.getSelectedIndex() != 0));

        add(tabbedPane, BorderLayout.CENTER);
        add(pnlInferior, BorderLayout.SOUTH);
    }

    // =========================================================================
    // PESTAÑA 1: CARGA OBLIGATORIA DE LOS 2 JSON DEL ENUNCIADO
    // =========================================================================

    private JPanel crearPanelCargaDualJson() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 8, 8, 8);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1.0;

        // Tarjeta JSON 1: Sistema de Archivos y Disco
        JPanel pJson1 = new JPanel(new BorderLayout(8, 8));
        pJson1.setBorder(BorderFactory.createTitledBorder("Paso 1: JSON del Sistema de Archivos y Disco"));
        JPanel bJson1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        JButton btnCargarFs = new JButton("1. Cargar JSON Sistema de Archivos…");
        JButton btnGuardarFs = new JButton("Guardar Sistema de Archivos actual (JSON)…");
        btnCargarFs.addActionListener(e -> cargarJsonSistemaArchivos());
        btnGuardarFs.addActionListener(e -> guardarJsonSistemaArchivos());
        bJson1.add(btnCargarFs);
        bJson1.add(btnGuardarFs);
        lblEstadoJson1.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        pJson1.add(bJson1, BorderLayout.NORTH);
        pJson1.add(lblEstadoJson1, BorderLayout.CENTER);

        g.gridx = 0;
        g.gridy = 0;
        panel.add(pJson1, g);

        // Tarjeta JSON 2: Procesos y Configuración Global
        JPanel pJson2 = new JPanel(new BorderLayout(8, 8));
        pJson2.setBorder(BorderFactory.createTitledBorder("Paso 2: JSON de Procesos y Configuración"));
        JPanel bJson2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        JButton btnCargarProc = new JButton("2. Cargar JSON Procesos y Config…");
        btnCargarProc.addActionListener(e -> cargarJsonProcesos());
        bJson2.add(btnCargarProc);
        lblEstadoJson2.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        pJson2.add(bJson2, BorderLayout.NORTH);
        pJson2.add(lblEstadoJson2, BorderLayout.CENTER);

        g.gridy = 1;
        panel.add(pJson2, g);

        // Acciones de lanzamiento
        JPanel pAcciones = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 10));
        btnAplicarDosJson.setFont(btnAplicarDosJson.getFont().deriveFont(Font.BOLD, 13.5f));
        btnAplicarDosJson.addActionListener(e -> aplicarAmbosJsonYContinuar());
        btnPreservarDisco.addActionListener(e -> reiniciarPreservandoDisco());
        pAcciones.add(btnAplicarDosJson);
        pAcciones.add(btnPreservarDisco);

        g.gridy = 2;
        panel.add(pAcciones, g);

        g.gridy = 3;
        g.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), g);

        return panel;
    }

    // =========================================================================
    // PESTAÑA 2: PARÁMETROS DE DISCO Y COMPUTADOR ÚNICO
    // =========================================================================

    private JPanel crearPanelComputadorYDisco() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JPanel pnlParams = new JPanel(new GridBagLayout());
        pnlParams.setBorder(BorderFactory.createTitledBorder("Parámetros Globales y del Disco Simulado (SD)"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 8, 5, 8);
        g.anchor = GridBagConstraints.WEST;

        g.gridx = 0; g.gridy = 0; pnlParams.add(new JLabel("Duración del ciclo (ms):"), g);
        g.gridx = 1; pnlParams.add(txtDuracionCiclo, g);
        g.gridx = 2; pnlParams.add(new JLabel("Total bloques disco:"), g);
        g.gridx = 3; pnlParams.add(txtBloquesDisco, g);

        g.gridx = 0; g.gridy = 1; pnlParams.add(new JLabel("Área Swap (bloques):"), g);
        g.gridx = 1; pnlParams.add(txtTamanoSwap, g);
        g.gridx = 2; pnlParams.add(new JLabel("Cabezal inicial:"), g);
        g.gridx = 3; pnlParams.add(txtCabezalInicial, g);

        g.gridx = 0; g.gridy = 2; pnlParams.add(new JLabel("Velocidad cabezal (blq/ciclo):"), g);
        g.gridx = 1; pnlParams.add(txtVelocidadCabezal, g);
        g.gridx = 2; pnlParams.add(new JLabel("Política disco inicial:"), g);
        g.gridx = 3; pnlParams.add(comboPoliticaDisco, g);

        panel.add(pnlParams, BorderLayout.NORTH);

        tblComputadores.setFillsViewportHeight(true);
        tblComputadores.setRowHeight(26);
        configurarEditoresTablaComputadores();
        JScrollPane scroll = new JScrollPane(tblComputadores);
        scroll.setBorder(BorderFactory.createTitledBorder("Computador Único del Sistema (1 unidad RAM = 1 bloque)"));
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private void configurarEditoresTablaComputadores() {
        JComboBox<PoliticaPlanificacion> comboPol = new JComboBox<>(PoliticaPlanificacion.values());
        tblComputadores.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(comboPol));
    }

    // =========================================================================
    // PESTAÑA 3: PROCESOS INICIALES
    // =========================================================================

    private JPanel crearPanelProcesos() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        tblProcesos.setFillsViewportHeight(true);
        tblProcesos.setRowHeight(24);
        panel.add(new JScrollPane(tblProcesos), BorderLayout.CENTER);

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnAgregar = new JButton("Agregar Proceso…");
        JButton btnEliminar = new JButton("Eliminar Proceso");
        btnAgregar.addActionListener(e -> agregarProceso());
        btnEliminar.addActionListener(e -> eliminarProceso());
        acciones.add(btnAgregar);
        acciones.add(btnEliminar);
        panel.add(acciones, BorderLayout.SOUTH);

        return panel;
    }

    // =========================================================================
    // ACCIONES DE CARGA / GUARDADO DUAL JSON
    // =========================================================================

    private void cargarJsonSistemaArchivos() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Seleccionar JSON 1: Sistema de Archivos y Disco");
        fc.setFileFilter(new FileNameExtensionFilter("Archivos JSON (*.json)", "json"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            ConfigSistemaArchivos cargado = GestorPersistencia.cargarSistemaArchivosJSON(fc.getSelectedFile());
            this.fsCargado = cargado;
            this.config.setTotalBloquesDisco(cargado.getDiskConfig().getTotalBlocks());
            this.config.setCabezalInicial(cargado.getDiskConfig().getInitialHead());
            actualizarCamposGlobales();
            actualizarEstadoJsonLabels();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al cargar JSON del Sistema de Archivos:\n" + ex.getMessage(),
                    "Error de JSON 1", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarJsonProcesos() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Seleccionar JSON 2: Procesos y Configuración");
        fc.setFileFilter(new FileNameExtensionFilter("Archivos JSON (*.json)", "json"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            ConfigProcesosDisco cargado = GestorPersistencia.cargarProcesosJSON(fc.getSelectedFile());
            this.procCargado = cargado;
            sincronizarCamposConJson2(cargado);
            actualizarEstadoJsonLabels();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al cargar JSON de Procesos:\n" + ex.getMessage(),
                    "Error de JSON 2", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void guardarJsonSistemaArchivos() {
        SistemaArchivos saVivo = (simuladorExistente != null
                && simuladorExistente.getServicioArchivos() instanceof SistemaArchivos sa) ? sa : null;
        if (saVivo == null && fsCargado == null) {
            JOptionPane.showMessageDialog(this,
                    "No hay ningún Sistema de Archivos activo o cargado para guardar.",
                    "Sin datos", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Guardar estado del Sistema de Archivos (JSON)");
        fc.setFileFilter(new FileNameExtensionFilter("Archivos JSON (*.json)", "json"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File destino = fc.getSelectedFile();
        if (!destino.getName().toLowerCase().endsWith(".json")) {
            destino = new File(destino.getAbsolutePath() + ".json");
        }
        try {
            if (saVivo != null) {
                GestorPersistencia.guardarSistemaArchivosJSON(saVivo, destino);
            } else {
                GestorPersistencia.guardarSistemaArchivosJSON(fsCargado, destino);
            }
            JOptionPane.showMessageDialog(this,
                    "Estado del sistema de archivos guardado en:\n" + destino.getAbsolutePath(),
                    "Guardado exitoso", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al guardar JSON del sistema de archivos:\n" + ex.getMessage(),
                    "Error de guardado", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void aplicarAmbosJsonYContinuar() {
        if (fsCargado == null || procCargado == null) {
            JOptionPane.showMessageDialog(this,
                    "Debe cargar obligatoriamente ambos archivos JSON (Sistema de Archivos y Procesos) antes de continuar.",
                    "Carga incompleta", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Simulador nuevoSim = GestorPersistencia.construirSimuladorDesdeDosJSON(fsCargado, procCargado);
            lanzarVistaEstado(nuevoSim);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo inicializar la simulación con los JSON cargados:\n" + ex.getMessage(),
                    "Error de inicialización", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void reiniciarPreservandoDisco() {
        SistemaArchivos saVivo = (simuladorExistente != null
                && simuladorExistente.getServicioArchivos() instanceof SistemaArchivos sa) ? sa : null;
        if (saVivo == null) {
            JOptionPane.showMessageDialog(this,
                    "No hay una simulación previa con sistema de archivos activo para preservar.",
                    "Sin disco previo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (procCargado == null) {
            JOptionPane.showMessageDialog(this,
                    "Cargue primero el JSON 2 de nuevos procesos que desea ejecutar sobre el disco preservado.",
                    "Falta JSON de Procesos", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            procCargado.validarEstricto();
            if (simuladorExistente != null) {
                simuladorExistente.detener();
            }
            Simulador nuevoSim = GestorPersistencia.construirSimuladorPreservandoDisco(saVivo, procCargado);
            lanzarVistaEstado(nuevoSim);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al reiniciar preservando el disco:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void lanzarVistaEstado(Simulador nuevoSim) {
        SwingUtilities.invokeLater(() -> {
            if (ventanaPadre instanceof VentanaEstado ve) {
                ve.cerrarYReemplazar(nuevoSim);
            } else {
                new VentanaEstado(nuevoSim).setVisible(true);
            }
            dispose();
        });
    }

    private void sincronizarCamposConJson2(ConfigProcesosDisco j2) {
        config.setDuracionCicloMs(j2.getGlobalConfig().getCycleDurationMs());
        config.setTamanoSwap(j2.getGlobalConfig().getSwapSizeBlocks());
        config.setPoliticaDiscoInicial(j2.getPoliticaDiscoEnum());

        if (!config.getComputadores().estaVacia()) {
            ConfigComputador pc = config.getComputadores().obtener(0);
            pc.setMemoriaTotal(j2.getComputer().getRamCapacity());
            pc.setPolitica(j2.getPoliticaCpuEnum());
            if (j2.getComputer().getQuantum() > 0) {
                pc.setQuantum(j2.getComputer().getQuantum());
            }
            tablaComputadoresModel.fireTableDataChanged();
        }
        actualizarCamposGlobales();
    }

    private void actualizarEstadoJsonLabels() {
        if (fsCargado != null) {
            int dirs = 0;
            int archs = 0;
            Iterador<ConfigSistemaArchivos.EntradaArchivo> it = fsCargado.getFileSystem().iterador();
            while (it.haySiguiente()) {
                if ("DIRECTORY".equalsIgnoreCase(it.siguiente().getType())) {
                    dirs++;
                } else {
                    archs++;
                }
            }
            lblEstadoJson1.setText("[✓] Cargado: " + fsCargado.getDiskConfig().getTotalBlocks()
                    + " bloques | Cabezal inicial: " + fsCargado.getDiskConfig().getInitialHead()
                    + " | " + dirs + " directorios, " + archs + " archivos.");
            lblEstadoJson1.setForeground(new Color(20, 130, 50));
        } else {
            lblEstadoJson1.setText("[ ] Pendiente: cargue el JSON 1 (Sistema de Archivos y Disco).");
            lblEstadoJson1.setForeground(new Color(180, 60, 30));
        }

        if (procCargado != null) {
            lblEstadoJson2.setText("[✓] Cargado: RAM " + procCargado.getComputer().getRamCapacity()
                    + " blq | CPU: " + procCargado.getPoliticaCpuEnum()
                    + " | Disco: " + procCargado.getPoliticaDiscoEnum()
                    + " | Swap: " + procCargado.getGlobalConfig().getSwapSizeBlocks()
                    + " blq | " + procCargado.getProcesses().getTamano() + " procesos.");
            lblEstadoJson2.setForeground(new Color(20, 130, 50));
        } else {
            lblEstadoJson2.setText("[ ] Pendiente: cargue el JSON 2 (Procesos y Configuración).");
            lblEstadoJson2.setForeground(new Color(180, 60, 30));
        }

        btnAplicarDosJson.setEnabled(fsCargado != null && procCargado != null);
        boolean tieneDiscoVivo = simuladorExistente != null
                && simuladorExistente.getServicioArchivos() instanceof SistemaArchivos;
        btnPreservarDisco.setEnabled(tieneDiscoVivo && procCargado != null);
    }

    // =========================================================================
    // ACCIONES DE CONFIGURACIÓN MANUAL Y PERSISTENCIA GENERAL
    // =========================================================================

    private void agregarProceso() {
        JTextField txtNombre = new JTextField("Proc-" + (config.getProcesos().getTamano() + 1));
        JComboBox<TipoProceso> cbTipo = new JComboBox<>(new TipoProceso[]{TipoProceso.CPU_BOUND, TipoProceso.IO_BOUND});
        JTextField txtInstr = new JTextField("4");
        JTextField txtMem = new JTextField("16");
        JTextField txtPrio = new JTextField("1");
        JTextField txtDeadline = new JTextField("500");
        JComboBox<String> cbOp = new JComboBox<>(new String[]{"(Ninguna - CPU/IO)", "CREATE", "READ", "UPDATE", "DELETE"});
        JTextField txtRuta = new JTextField("/sistema/config.sys");
        JTextField txtTamArchivo = new JTextField("4");

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        g.gridx = 0; g.gridy = row; form.add(new JLabel("Nombre:"), g);
        g.gridx = 1; form.add(txtNombre, g); row++;
        g.gridx = 0; g.gridy = row; form.add(new JLabel("Tipo:"), g);
        g.gridx = 1; form.add(cbTipo, g); row++;
        g.gridx = 0; g.gridy = row; form.add(new JLabel("Instrucciones:"), g);
        g.gridx = 1; form.add(txtInstr, g); row++;
        g.gridx = 0; g.gridy = row; form.add(new JLabel("Memoria (bloques):"), g);
        g.gridx = 1; form.add(txtMem, g); row++;
        g.gridx = 0; g.gridy = row; form.add(new JLabel("Prioridad (>=1):"), g);
        g.gridx = 1; form.add(txtPrio, g); row++;
        g.gridx = 0; g.gridy = row; form.add(new JLabel("Deadline:"), g);
        g.gridx = 1; form.add(txtDeadline, g); row++;
        g.gridx = 0; g.gridy = row; form.add(new JLabel("Operación E/S:"), g);
        g.gridx = 1; form.add(cbOp, g); row++;
        g.gridx = 0; g.gridy = row; form.add(new JLabel("Ruta objetivo:"), g);
        g.gridx = 1; form.add(txtRuta, g); row++;
        g.gridx = 0; g.gridy = row; form.add(new JLabel("Bloques archivo (CREATE):"), g);
        g.gridx = 1; form.add(txtTamArchivo, g);

        int res = JOptionPane.showConfirmDialog(this, form, "Agregar Proceso",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            String nombre = txtNombre.getText().trim();
            if (nombre.isEmpty()) {
                throw new IllegalArgumentException("El nombre del proceso no puede estar vacío.");
            }
            int instr = Integer.parseInt(txtInstr.getText().trim());
            long mem = Long.parseLong(txtMem.getText().trim());
            int prio = Integer.parseInt(txtPrio.getText().trim());
            long dl = Long.parseLong(txtDeadline.getText().trim());
            if (instr <= 0 || mem <= 0 || prio <= 0 || dl <= 0) {
                throw new IllegalArgumentException("Instrucciones, memoria, prioridad y deadline deben ser > 0.");
            }
            String opSel = (String) cbOp.getSelectedItem();
            TipoOperacionES op = (opSel != null && !opSel.startsWith("(")) ? TipoOperacionES.desdeTexto(opSel) : null;
            String ruta = (op != null) ? txtRuta.getText().trim() : "";
            int tamArch = (op == TipoOperacionES.CREAR) ? Integer.parseInt(txtTamArchivo.getText().trim()) : 0;
            if (op == TipoOperacionES.CREAR && tamArch < 0) {
                throw new IllegalArgumentException("El tamaño en bloques del archivo debe ser >= 0.");
            }
            if (op != null && ruta.isEmpty()) {
                throw new IllegalArgumentException("Debe indicar la ruta objetivo para la operación E/S.");
            }

            int nuevoId = config.getProcesos().getTamano() + 1;
            int idPc = config.getComputadores().estaVacia() ? 1 : config.getComputadores().obtener(0).getId();
            config.getProcesos().agregar(new ConfigProceso(
                    nuevoId, nombre, (TipoProceso) cbTipo.getSelectedItem(), idPc,
                    instr, mem, prio, dl, 1, 1, op, ruta, tamArch, "",
                    ModoUsuario.ADMINISTRADOR, "admin"));
            tablaProcesosModel.fireTableDataChanged();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Ingrese valores numéricos enteros válidos.",
                    "Error de Validación", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Error de Validación", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void eliminarProceso() {
        int fila = tblProcesos.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un proceso de la tabla para eliminar.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        config.getProcesos().eliminarEn(fila);
        tablaProcesosModel.fireTableDataChanged();
    }

    private void cargarArchivoGeneral() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Importar Configuración General (JSON o CSV)");
        fc.setFileFilter(new FileNameExtensionFilter("Archivos JSON y CSV (*.json, *.csv)", "json", "csv"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            ConfiguracionSimulacion cargada = GestorPersistencia.cargarConfiguracion(fc.getSelectedFile(), null);
            this.config = cargada;
            tablaComputadoresModel.setLista(config.getComputadores());
            tablaProcesosModel.setLista(config.getProcesos());
            actualizarCamposGlobales();
            JOptionPane.showMessageDialog(this, "Configuración importada correctamente.",
                    "Éxito", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al importar:\n" + ex.getMessage(),
                    "Error de Carga", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void guardarArchivoGeneral() {
        if (!aplicarCamposGlobalesAConfig()) {
            return;
        }
        ListaEnlazada<String> errs = config.validar();
        if (!errs.estaVacia()) {
            mostrarListaErrores("Corrija los errores antes de guardar:", errs);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Exportar Configuración (JSON o CSV)");
        FileNameExtensionFilter fJson = new FileNameExtensionFilter("Formato JSON (*.json)", "json");
        FileNameExtensionFilter fCsv = new FileNameExtensionFilter("Formato CSV (*.csv)", "csv");
        fc.addChoosableFileFilter(fJson);
        fc.addChoosableFileFilter(fCsv);
        fc.setFileFilter(fJson);
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File archivo = fc.getSelectedFile();
        FormatoPersistencia fmt = (fc.getFileFilter() == fCsv || archivo.getName().toLowerCase().endsWith(".csv"))
                ? FormatoPersistencia.CSV : FormatoPersistencia.JSON;
        if (fmt == FormatoPersistencia.JSON && !archivo.getName().toLowerCase().endsWith(".json")) {
            archivo = new File(archivo.getAbsolutePath() + ".json");
        } else if (fmt == FormatoPersistencia.CSV && !archivo.getName().toLowerCase().endsWith(".csv")) {
            archivo = new File(archivo.getAbsolutePath() + ".csv");
        }
        try {
            GestorPersistencia.guardarConfiguracion(config, archivo, fmt);
            JOptionPane.showMessageDialog(this, "Configuración guardada en:\n" + archivo.getAbsolutePath(),
                    "Guardado", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al guardar:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void restablecerPorDefecto() {
        this.config = ConfiguracionSimulacion.crearPorDefecto();
        tablaComputadoresModel.setLista(config.getComputadores());
        tablaProcesosModel.setLista(config.getProcesos());
        actualizarCamposGlobales();
    }

    private void iniciarSimulacionManual() {
        if (!aplicarCamposGlobalesAConfig()) {
            return;
        }
        ListaEnlazada<String> errs = config.validar();
        if (!errs.estaVacia()) {
            mostrarListaErrores("Errores en la configuración:", errs);
            return;
        }
        try {
            Simulador nuevoSim = config.aSimulador();
            lanzarVistaEstado(nuevoSim);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al construir la simulación:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void actualizarCamposGlobales() {
        txtDuracionCiclo.setText(String.valueOf(config.getDuracionCicloMs()));
        txtBloquesDisco.setText(String.valueOf(config.getTotalBloquesDisco()));
        txtTamanoSwap.setText(String.valueOf(config.getTamanoSwap()));
        txtCabezalInicial.setText(String.valueOf(config.getCabezalInicial()));
        txtVelocidadCabezal.setText(String.valueOf(config.getVelocidadCabezal()));
        comboPoliticaDisco.setSelectedItem(config.getPoliticaDiscoInicial());
    }

    private boolean aplicarCamposGlobalesAConfig() {
        try {
            long dur = Long.parseLong(txtDuracionCiclo.getText().trim());
            int blq = Integer.parseInt(txtBloquesDisco.getText().trim());
            int swap = Integer.parseInt(txtTamanoSwap.getText().trim());
            int cab = Integer.parseInt(txtCabezalInicial.getText().trim());
            int vel = Integer.parseInt(txtVelocidadCabezal.getText().trim());
            if (dur <= 0 || blq <= 0 || swap < 0 || cab < 0 || cab >= blq || vel <= 0) {
                throw new IllegalArgumentException("Verifique que los parámetros numéricos estén en rangos válidos.");
            }
            config.setDuracionCicloMs(dur);
            config.setTotalBloquesDisco(blq);
            config.setTamanoSwap(swap);
            config.setCabezalInicial(cab);
            config.setVelocidadCabezal(vel);
            config.setPoliticaDiscoInicial((PoliticaDisco) comboPoliticaDisco.getSelectedItem());
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Parámetros globales/disco inválidos: " + ex.getMessage(),
                    "Validación", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void mostrarListaErrores(String titulo, ListaEnlazada<String> errs) {
        StringBuilder sb = new StringBuilder(titulo).append("\n\n");
        Iterador<String> it = errs.iterador();
        while (it.haySiguiente()) {
            sb.append(" • ").append(it.siguiente()).append("\n");
        }
        JOptionPane.showMessageDialog(this, sb.toString(), "Validación de Configuración", JOptionPane.ERROR_MESSAGE);
    }

    public ComputadoresTableModel getTablaComputadoresModel() {
        return tablaComputadoresModel;
    }

    public JTable getTblComputadores() {
        return tblComputadores;
    }

    // =========================================================================
    // MODELOS DE TABLA PROPIOS (SIN java.util)
    // =========================================================================

    public class ComputadoresTableModel extends AbstractTableModel {
        private final String[] columnas = {"ID", "Nombre", "RAM (bloques)", "Política Planificación", "Quantum"};
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
            if (c == 1 || c == 2 || c == 3) return true;
            if (c == 4) {
                ConfigComputador pc = lista.obtener(r);
                return pc != null && pc.getPolitica() == PoliticaPlanificacion.ROUND_ROBIN;
            }
            return false;
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
                            mostrarAviso("La memoria RAM debe ser mayor a 0 bloques.");
                        }
                    } catch (NumberFormatException e) {
                        mostrarAviso("La memoria RAM debe ser un número entero válido.");
                    }
                }
                case 3 -> {
                    PoliticaPlanificacion nuevaPol = (aValue instanceof PoliticaPlanificacion p)
                            ? p : GestorPersistencia.parsearPolitica(aValue.toString());
                    pc.setPolitica(nuevaPol);
                    if (nuevaPol == PoliticaPlanificacion.ROUND_ROBIN && pc.getQuantum() <= 0) {
                        pc.setQuantum(2);
                    }
                    fireTableRowsUpdated(r, r);
                }
                case 4 -> {
                    try {
                        int q = Integer.parseInt(aValue.toString().trim());
                        if (q > 0) {
                            pc.setQuantum(q);
                            fireTableCellUpdated(r, c);
                        } else {
                            mostrarAviso("El quantum debe ser mayor que 0.");
                        }
                    } catch (NumberFormatException e) {
                        mostrarAviso("El quantum debe ser un número entero.");
                    }
                }
            }
        }
    }

    private static class ProcesosTableModel extends AbstractTableModel {
        private final String[] columnas = {
            "ID", "Nombre", "Tipo", "Operación E/S", "Ruta Objetivo",
            "Tam. Archivo (blq)", "Memoria (blq)", "Prioridad", "Deadline", "Instrucciones"
        };
        private ListaEnlazada<ConfigProceso> lista;

        public ProcesosTableModel(ListaEnlazada<ConfigProceso> lista) {
            this.lista = lista;
        }

        public void setLista(ListaEnlazada<ConfigProceso> lista) {
            this.lista = lista;
            fireTableDataChanged();
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
                case 3 -> (p.getOperacion() != null) ? p.getOperacion().getCodigoJson() : "-";
                case 4 -> (p.getRutaObjetivo().isEmpty()) ? "-" : p.getRutaObjetivo();
                case 5 -> p.getTamanoArchivoBloques();
                case 6 -> p.getMemoria();
                case 7 -> p.getPrioridad();
                case 8 -> p.getDeadline();
                case 9 -> p.getInstrucciones();
                default -> null;
            };
        }
    }
}
