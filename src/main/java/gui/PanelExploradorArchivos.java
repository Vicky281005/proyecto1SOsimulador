package gui;

import archivos.NodoArchivo;
import archivos.SistemaArchivos;
import disco.DiscoSimulado;
import enums.ModoUsuario;
import enums.PermisoArchivo;
import enums.TipoOperacionES;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.text.NumberFormatter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import modelo.PCB;
import nucleo.Computador;
import simulacion.Simulador;

/**
 * Panel integrado de Gestión de Archivos y Permisos de ÁvilaOS (Vista 2):
 * <ul>
 *   <li>Selector de modo {@link ModoUsuario#ADMINISTRADOR} / {@link ModoUsuario#USUARIO} y usuario activo.</li>
 *   <li>Árbol jerárquico ({@link JTree}) conectado en tiempo real al {@link SistemaArchivos}.</li>
 *   <li>Panel de detalles del archivo o directorio seleccionado (incluyendo cadena de bloques).</li>
 *   <li>Tabla de asignación de archivos ({@link JTable}) en tiempo real con el color propio en disco.</li>
 *   <li>Acciones CRUD con validaciones de permisos que encolan procesos de E/S en el {@link Simulador}.</li>
 * </ul>
 */
public class PanelExploradorArchivos extends JPanel {

    private static final Color COLOR_ADMIN = new Color(25, 110, 65);
    private static final Color COLOR_USUARIO = new Color(175, 95, 10);

    private final Simulador simulador;

    private final JComboBox<ModoUsuario> comboModo = new JComboBox<>(ModoUsuario.values());
    private final JTextField txtUsuario = new JTextField("admin", 10);
    private final JLabel lblModoInsignia = new JLabel();

    private final JButton btnCrear = new JButton("Crear…");
    private final JButton btnLeer = new JButton("Leer");
    private final JButton btnRenombrar = new JButton("Actualizar…");
    private final JButton btnEliminar = new JButton("Eliminar");

    private final DefaultMutableTreeNode nodoRaizVacio =
            new DefaultMutableTreeNode("Sin sistema de archivos cargado");
    private final DefaultTreeModel modeloArbol = new DefaultTreeModel(nodoRaizVacio);
    private final JTree arbolArchivos = new JTree(modeloArbol);

    private final ModeloTablaArchivos modeloTabla = new ModeloTablaArchivos();
    private final JTable tablaArchivos = new JTable(modeloTabla);

    // Campos de detalle del elemento seleccionado
    private final JLabel lblDetNombre = new JLabel("-");
    private final JLabel lblDetRuta = new JLabel("-");
    private final JLabel lblDetTipo = new JLabel("-");
    private final JLabel lblDetDueno = new JLabel("-");
    private final JLabel lblDetPermiso = new JLabel("-");
    private final JLabel lblDetCreador = new JLabel("-");
    private final JLabel lblDetInicio = new JLabel("-");
    private final JLabel lblDetTamano = new JLabel("-");
    private final JLabel lblDetColor = new JLabel("-");
    private final JLabel lblDetCadena = new JLabel("-");

    private final JLabel lblEstadoBarra = new JLabel("Listo.");

    private String firmaUltimaVista = "";
    private String rutaSeleccionadaActual = "/";
    private boolean sincronizandoSeleccion = false;
    private boolean operacionesHabilitadas = true;
    private String mensajeUltimaAccion = "";
    private Runnable escuchaCambioModo;

    public PanelExploradorArchivos(Simulador simulador) {
        this.simulador = simulador;
        construirInterfaz();
        configurarEventos();
        actualizarEstadoModoUsuario();
        actualizar();
    }

    private void construirInterfaz() {
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createTitledBorder("Sistema de Archivos y Asignación en Disco"));

        // --- Barra superior: Selector de Modo + CRUD ---
        JPanel barraSuperior = new JPanel(new GridLayout(2, 1, 0, 4));

        JPanel filaModo = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        filaModo.add(new JLabel("Modo:"));
        filaModo.add(comboModo);
        filaModo.add(new JLabel("Usuario activo:"));
        filaModo.add(txtUsuario);
        lblModoInsignia.setFont(lblModoInsignia.getFont().deriveFont(Font.BOLD, 11f));
        filaModo.add(lblModoInsignia);

        JPanel filaCrud = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        btnCrear.setToolTipText("Crear un nuevo archivo o directorio mediante proceso de E/S");
        btnLeer.setToolTipText("Leer el archivo seleccionado mediante proceso de E/S");
        btnRenombrar.setToolTipText("Actualizar (renombrar) el archivo o directorio seleccionado mediante proceso de E/S");
        btnEliminar.setToolTipText("Eliminar archivo o directorio (recursivo para directorios)");
        filaCrud.add(btnCrear);
        filaCrud.add(btnLeer);
        filaCrud.add(btnRenombrar);
        filaCrud.add(btnEliminar);

        barraSuperior.add(filaModo);
        barraSuperior.add(filaCrud);

        // --- JTree jerárquico ---
        arbolArchivos.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        arbolArchivos.setCellRenderer(new RenderizadorArbolArchivos());
        arbolArchivos.setShowsRootHandles(true);
        JScrollPane scrollArbol = new JScrollPane(arbolArchivos);
        scrollArbol.setBorder(BorderFactory.createTitledBorder("Jerarquía de archivos (JTree)"));
        scrollArbol.setPreferredSize(new Dimension(250, 220));

        // --- Panel de detalles ---
        JPanel panelDetalles = crearPanelDetalles();
        panelDetalles.setPreferredSize(new Dimension(250, 220));

        JSplitPane splitSuperior = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollArbol, panelDetalles);
        splitSuperior.setResizeWeight(0.52);

        // --- JTable de asignación de archivos ---
        tablaArchivos.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablaArchivos.setRowHeight(22);
        tablaArchivos.setAutoCreateRowSorter(false);
        tablaArchivos.getTableHeader().setReorderingAllowed(false);
        tablaArchivos.getColumnModel().getColumn(0).setPreferredWidth(52);
        tablaArchivos.getColumnModel().getColumn(0).setMaxWidth(65);
        tablaArchivos.getColumnModel().getColumn(0).setCellRenderer(new ModeloTablaArchivos.RenderizadorColor());
        tablaArchivos.getColumnModel().getColumn(1).setPreferredWidth(190);
        tablaArchivos.getColumnModel().getColumn(2).setPreferredWidth(68);
        tablaArchivos.getColumnModel().getColumn(3).setPreferredWidth(92);
        tablaArchivos.getColumnModel().getColumn(4).setPreferredWidth(70);
        tablaArchivos.getColumnModel().getColumn(5).setPreferredWidth(72);
        tablaArchivos.getColumnModel().getColumn(6).setPreferredWidth(115);

        JScrollPane scrollTabla = new JScrollPane(tablaArchivos);
        scrollTabla.setBorder(BorderFactory.createTitledBorder("Tabla de asignación de archivos en tiempo real"));
        scrollTabla.setPreferredSize(new Dimension(480, 190));

        JSplitPane splitPrincipal = new JSplitPane(JSplitPane.VERTICAL_SPLIT, splitSuperior, scrollTabla);
        splitPrincipal.setResizeWeight(0.54);

        lblEstadoBarra.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        lblEstadoBarra.setFont(lblEstadoBarra.getFont().deriveFont(Font.PLAIN, 11.5f));

        add(barraSuperior, BorderLayout.NORTH);
        add(splitPrincipal, BorderLayout.CENTER);
        add(lblEstadoBarra, BorderLayout.SOUTH);
    }

    private JPanel crearPanelDetalles() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Detalles de la selección"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 6, 2, 6);
        gbc.anchor = GridBagConstraints.WEST;

        String[] etiquetas = {
            "Nombre:", "Ruta:", "Tipo:", "Dueño:", "Permisos:",
            "Creador:", "Primer bloque:", "Tamaño:", "Color:", "Bloques:"
        };
        JLabel[] valores = {
            lblDetNombre, lblDetRuta, lblDetTipo, lblDetDueno, lblDetPermiso,
            lblDetCreador, lblDetInicio, lblDetTamano, lblDetColor, lblDetCadena
        };

        Font baseFont = lblDetNombre.getFont();
        Font fuenteNegrita = baseFont.deriveFont(Font.BOLD, 11.5f);
        Font fuenteValor = baseFont.deriveFont(Font.PLAIN, 11.5f);

        for (int i = 0; i < etiquetas.length; i++) {
            gbc.gridx = 0;
            gbc.gridy = i;
            gbc.weightx = 0.0;
            gbc.fill = GridBagConstraints.NONE;
            JLabel lblEtiq = new JLabel(etiquetas[i]);
            lblEtiq.setFont(fuenteNegrita);
            panel.add(lblEtiq, gbc);

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            valores[i].setFont(fuenteValor);
            panel.add(valores[i], gbc);
        }

        // Relleno inferior para alinear arriba
        gbc.gridx = 0;
        gbc.gridy = etiquetas.length;
        gbc.weighty = 1.0;
        panel.add(new JLabel(), gbc);

        return wrapDetallesScroll(panel);
    }

    private static JPanel wrapDetallesScroll(JPanel interno) {
        JPanel contenedor = new JPanel(new BorderLayout());
        JScrollPane sp = new JScrollPane(interno);
        sp.setBorder(null);
        contenedor.add(sp, BorderLayout.CENTER);
        return contenedor;
    }

    private void configurarEventos() {
        comboModo.addActionListener(e -> actualizarEstadoModoUsuario());

        arbolArchivos.addTreeSelectionListener(e -> {
            if (sincronizandoSeleccion) {
                return;
            }
            NodoArchivo seleccionado = obtenerNodoSeleccionadoArbol();
            if (seleccionado != null) {
                rutaSeleccionadaActual = seleccionado.getRuta();
                mostrarDetalles(seleccionado);
                sincronizarFilaTablaConRuta(seleccionado.getRuta());
            } else {
                limpiarDetalles();
            }
        });

        tablaArchivos.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || sincronizandoSeleccion) {
                return;
            }
            int fila = tablaArchivos.getSelectedRow();
            if (fila >= 0) {
                NodoArchivo nodo = modeloTabla.obtenerEnFila(fila);
                if (nodo != null) {
                    rutaSeleccionadaActual = nodo.getRuta();
                    seleccionarRutaEnArbol(nodo.getRuta());
                    mostrarDetalles(nodo);
                }
            }
        });

        btnCrear.addActionListener(e -> accionCrear());
        btnLeer.addActionListener(e -> accionLeer());
        btnRenombrar.addActionListener(e -> accionRenombrar());
        btnEliminar.addActionListener(e -> accionEliminar());
    }

    private void actualizarEstadoModoUsuario() {
        ModoUsuario modo = getModoSeleccionado();
        if (modo.esAdministrador()) {
            txtUsuario.setText("admin");
            txtUsuario.setEditable(false);
            lblModoInsignia.setText("Acceso completo (archivos, procesos y políticas)");
            lblModoInsignia.setForeground(COLOR_ADMIN);
        } else {
            if ("admin".equalsIgnoreCase(txtUsuario.getText().trim()) || txtUsuario.getText().trim().isEmpty()) {
                txtUsuario.setText("user1");
            }
            txtUsuario.setEditable(true);
            lblModoInsignia.setText("Solo lectura pública/propia y E/S propios (sin cambiar políticas)");
            lblModoInsignia.setForeground(COLOR_USUARIO);
        }
        if (escuchaCambioModo != null) {
            escuchaCambioModo.run();
        }
    }

    /** Registra un oyente que se ejecuta cada vez que cambia el modo Administrador/Usuario. */
    public void setEscuchaCambioModo(Runnable escucha) {
        this.escuchaCambioModo = escucha;
    }

    /** Habilita o deshabilita las acciones CRUD (ej. al detener la simulación). */
    public void setOperacionesHabilitadas(boolean habilitado) {
        this.operacionesHabilitadas = habilitado;
    }

    /** Snapshot inmutable del estado del sistema de archivos capturado dentro de {@code leerEstado}. */
    static final class Snapshot {
        final boolean haySistemaArchivos;
        final boolean limiteAlcanzado;
        final int cantidadComputadores;
        final NodoArchivo copiaRaiz;
        final ListaEnlazada<NodoArchivo> archivos;
        final int bloquesLibres;
        final int cantidadDirectorios;
        final String firma;
        final NodoArchivo nodoSeleccionado;
        final ListaEnlazada<Integer> cadenaBloquesSeleccionado;

        Snapshot(boolean haySistemaArchivos, boolean limiteAlcanzado, int cantidadComputadores,
                 NodoArchivo copiaRaiz, ListaEnlazada<NodoArchivo> archivos,
                 int bloquesLibres, int cantidadDirectorios, String firma,
                 NodoArchivo nodoSeleccionado, ListaEnlazada<Integer> cadenaBloquesSeleccionado) {
            this.haySistemaArchivos = haySistemaArchivos;
            this.limiteAlcanzado = limiteAlcanzado;
            this.cantidadComputadores = cantidadComputadores;
            this.copiaRaiz = copiaRaiz;
            this.archivos = archivos;
            this.bloquesLibres = bloquesLibres;
            this.cantidadDirectorios = cantidadDirectorios;
            this.firma = firma;
            this.nodoSeleccionado = nodoSeleccionado;
            this.cadenaBloquesSeleccionado = cadenaBloquesSeleccionado;
        }
    }

    /**
     * Captura un snapshot de solo lectura del sistema de archivos y disco (sin mutar Swing).
     * Debe llamarse dentro de {@link Simulador#leerEstado(Runnable)}.
     */
    Snapshot capturarSnapshot() {
        SistemaArchivos sa = (simulador != null && simulador.getServicioArchivos() instanceof SistemaArchivos s)
                ? s : null;
        DiscoSimulado disco = (simulador != null) ? simulador.getDisco() : null;
        boolean limiteAlcanzado = (simulador != null && simulador.isLimiteAlcanzado());
        int cantPcs = (simulador != null) ? simulador.getCantidadComputadores() : 0;

        if (sa == null) {
            return new Snapshot(false, limiteAlcanzado, cantPcs, null, null, 0, 0, "SIN_FS", null, null);
        }

        NodoArchivo copiaRaiz = sa.obtenerCopiaArbol();
        ListaEnlazada<NodoArchivo> archivos = sa.listarTodosLosArchivos();
        int libres = (disco != null) ? disco.getBloquesLibresDatos() : 0;
        int dirs = sa.getCantidadDirectorios();
        String firmaNueva = construirFirma(copiaRaiz, archivos, libres);

        NodoArchivo seleccionado = buscarEnCopiaArbol(copiaRaiz, rutaSeleccionadaActual);
        ListaEnlazada<Integer> cadenaSel = null;
        if (seleccionado != null && seleccionado.esArchivo()
                && disco != null && seleccionado.getPrimerBloque() >= 0) {
            cadenaSel = disco.obtenerCadenaBloques(seleccionado.getPrimerBloque());
        }
        return new Snapshot(true, limiteAlcanzado, cantPcs, copiaRaiz, archivos,
                libres, dirs, firmaNueva, seleccionado, cadenaSel);
    }

    private static NodoArchivo buscarEnCopiaArbol(NodoArchivo nodo, String rutaBuscada) {
        if (nodo == null || rutaBuscada == null) {
            return null;
        }
        if (rutaBuscada.equals(nodo.getRuta())) {
            return nodo;
        }
        Iterador<NodoArchivo> it = nodo.iteradorHijos();
        while (it.haySiguiente()) {
            NodoArchivo encontrado = buscarEnCopiaArbol(it.siguiente(), rutaBuscada);
            if (encontrado != null) {
                return encontrado;
            }
        }
        return null;
    }

    /**
     * Aplica sobre el árbol, tabla y etiquetas de Swing un snapshot ya capturado (fuera de {@code mutexEstado}).
     */
    void aplicarSnapshot(Snapshot snap) {
        if (snap == null) {
            return;
        }
        boolean listoParaOperar = (snap.haySistemaArchivos && operacionesHabilitadas
                && !snap.limiteAlcanzado && snap.cantidadComputadores > 0);

        btnCrear.setEnabled(listoParaOperar);
        btnLeer.setEnabled(listoParaOperar);
        btnRenombrar.setEnabled(listoParaOperar);
        btnEliminar.setEnabled(listoParaOperar);

        if (!snap.haySistemaArchivos) {
            if (!"SIN_FS".equals(firmaUltimaVista)) {
                firmaUltimaVista = "SIN_FS";
                modeloArbol.setRoot(nodoRaizVacio);
                modeloTabla.actualizarFilas(new ListaEnlazada<>());
                limpiarDetalles();
            }
            lblEstadoBarra.setText("Sin sistema de archivos cargado: carga ambos JSON en 'Cargar JSON / Config…'.");
            return;
        }

        if (!snap.firma.equals(firmaUltimaVista)) {
            firmaUltimaVista = snap.firma;
            reconstruirArbolPreservandoEstado(snap.copiaRaiz);
            modeloTabla.actualizarFilas(snap.archivos);
            sincronizarFilaTablaConRuta(rutaSeleccionadaActual);
            if (snap.nodoSeleccionado != null) {
                mostrarDetalles(snap.nodoSeleccionado, snap.cadenaBloquesSeleccionado);
            } else {
                rutaSeleccionadaActual = "/";
                seleccionarRutaEnArbol("/");
                mostrarDetalles(snap.copiaRaiz, null);
            }
        }

        String resumen = String.format("Directorios: %d  |  Archivos: %d  |  Bloques libres: %d",
                snap.cantidadDirectorios, snap.archivos.getTamano(), snap.bloquesLibres);
        if (!mensajeUltimaAccion.isEmpty()) {
            resumen += "  |  " + mensajeUltimaAccion;
        }
        lblEstadoBarra.setText(resumen);
    }

    /**
     * Lee el estado actual del {@link SistemaArchivos} y refresca el árbol, la tabla,
     * los detalles y el estado de botones cuando ocurren cambios.
     */
    public final void actualizar() {
        aplicarSnapshot(capturarSnapshot());
    }

    private String construirFirma(NodoArchivo raiz, ListaEnlazada<NodoArchivo> archivos, int libres) {
        StringBuilder sb = new StringBuilder();
        sb.append("L=").append(libres).append(';');
        anexarFirmaNodo(raiz, sb);
        sb.append("|F=").append(archivos.getTamano());
        return sb.toString();
    }

    private void anexarFirmaNodo(NodoArchivo nodo, StringBuilder sb) {
        if (nodo == null) {
            return;
        }
        sb.append(nodo.getRuta()).append('#').append(nodo.getTipo().name())
                .append('#').append(nodo.getTamanoBloques())
                .append('#').append(nodo.getPrimerBloque())
                .append('#').append(nodo.getDueno())
                .append('#').append(nodo.getPermiso() != null ? nodo.getPermiso().name() : "")
                .append('#').append(nodo.getColorHex())
                .append(';');
        Iterador<NodoArchivo> it = nodo.iteradorHijos();
        while (it.haySiguiente()) {
            anexarFirmaNodo(it.siguiente(), sb);
        }
    }

    private void reconstruirArbolPreservandoEstado(NodoArchivo raizModelo) {
        ListaEnlazada<String> rutasExpandidas = recolectarRutasExpandidas();
        sincronizandoSeleccion = true;
        try {
            DefaultMutableTreeNode nuevoRoot = construirNodoSwing(raizModelo);
            modeloArbol.setRoot(nuevoRoot);
            expandirPorDefectoYGuardadas(nuevoRoot, rutasExpandidas);
            seleccionarRutaEnArbol(rutaSeleccionadaActual);
        } finally {
            sincronizandoSeleccion = false;
        }
    }

    private DefaultMutableTreeNode construirNodoSwing(NodoArchivo nodoArchivo) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(nodoArchivo);
        Iterador<NodoArchivo> it = nodoArchivo.iteradorHijos();
        while (it.haySiguiente()) {
            treeNode.add(construirNodoSwing(it.siguiente()));
        }
        return treeNode;
    }

    private ListaEnlazada<String> recolectarRutasExpandidas() {
        ListaEnlazada<String> rutas = new ListaEnlazada<>();
        Object rootObj = modeloArbol.getRoot();
        if (rootObj instanceof DefaultMutableTreeNode root) {
            recolectarExpandidasRecursivo(root, rutas);
        }
        return rutas;
    }

    private void recolectarExpandidasRecursivo(DefaultMutableTreeNode nodo, ListaEnlazada<String> rutas) {
        TreePath path = new TreePath(nodo.getPath());
        if (arbolArchivos.isExpanded(path) && nodo.getUserObject() instanceof NodoArchivo na) {
            rutas.agregar(na.getRuta());
        }
        for (int i = 0; i < nodo.getChildCount(); i++) {
            if (nodo.getChildAt(i) instanceof DefaultMutableTreeNode hijo) {
                recolectarExpandidasRecursivo(hijo, rutas);
            }
        }
    }

    private void expandirPorDefectoYGuardadas(DefaultMutableTreeNode root, ListaEnlazada<String> guardadas) {
        arbolArchivos.expandPath(new TreePath(root.getPath()));
        if (guardadas.estaVacia()) {
            // Al inicio expande los directorios de primer nivel para facilitar la exploración
            for (int i = 0; i < root.getChildCount(); i++) {
                DefaultMutableTreeNode hijo = (DefaultMutableTreeNode) root.getChildAt(i);
                arbolArchivos.expandPath(new TreePath(hijo.getPath()));
            }
            return;
        }
        expandirRecursivoSiEsta(root, guardadas);
    }

    private void expandirRecursivoSiEsta(DefaultMutableTreeNode nodo, ListaEnlazada<String> rutas) {
        if (nodo.getUserObject() instanceof NodoArchivo na && contieneRuta(rutas, na.getRuta())) {
            arbolArchivos.expandPath(new TreePath(nodo.getPath()));
        }
        for (int i = 0; i < nodo.getChildCount(); i++) {
            expandirRecursivoSiEsta((DefaultMutableTreeNode) nodo.getChildAt(i), rutas);
        }
    }

    private static boolean contieneRuta(ListaEnlazada<String> rutas, String buscada) {
        Iterador<String> it = rutas.iterador();
        while (it.haySiguiente()) {
            if (buscada.equals(it.siguiente())) {
                return true;
            }
        }
        return false;
    }

    private void seleccionarRutaEnArbol(String rutaObjetivo) {
        if (rutaObjetivo == null || !(modeloArbol.getRoot() instanceof DefaultMutableTreeNode root)) {
            return;
        }
        DefaultMutableTreeNode encontrado = buscarEnArbolSwing(root, rutaObjetivo);
        if (encontrado != null) {
            sincronizandoSeleccion = true;
            try {
                TreePath tp = new TreePath(encontrado.getPath());
                arbolArchivos.setSelectionPath(tp);
                arbolArchivos.scrollPathToVisible(tp);
            } finally {
                sincronizandoSeleccion = false;
            }
        }
    }

    private DefaultMutableTreeNode buscarEnArbolSwing(DefaultMutableTreeNode actual, String rutaObjetivo) {
        if (actual.getUserObject() instanceof NodoArchivo na && rutaObjetivo.equals(na.getRuta())) {
            return actual;
        }
        for (int i = 0; i < actual.getChildCount(); i++) {
            DefaultMutableTreeNode res = buscarEnArbolSwing((DefaultMutableTreeNode) actual.getChildAt(i), rutaObjetivo);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    private void sincronizarFilaTablaConRuta(String ruta) {
        int fila = modeloTabla.buscarFilaPorRuta(ruta);
        sincronizandoSeleccion = true;
        try {
            if (fila >= 0) {
                tablaArchivos.setRowSelectionInterval(fila, fila);
            } else {
                tablaArchivos.clearSelection();
            }
        } finally {
            sincronizandoSeleccion = false;
        }
    }

    private NodoArchivo obtenerNodoSeleccionadoArbol() {
        Object comp = arbolArchivos.getLastSelectedPathComponent();
        if (comp instanceof DefaultMutableTreeNode dm && dm.getUserObject() instanceof NodoArchivo na) {
            return na;
        }
        return null;
    }

    private void mostrarDetalles(NodoArchivo nodo) {
        ListaEnlazada<Integer> cadena = null;
        if (nodo != null && !nodo.esDirectorio() && nodo.getPrimerBloque() >= 0) {
            DiscoSimulado disco = (simulador != null) ? simulador.getDisco() : null;
            if (disco != null) {
                cadena = disco.obtenerCadenaBloques(nodo.getPrimerBloque());
            }
        }
        mostrarDetalles(nodo, cadena);
    }

    private void mostrarDetalles(NodoArchivo nodo, ListaEnlazada<Integer> cadena) {
        if (nodo == null) {
            limpiarDetalles();
            return;
        }
        lblDetNombre.setText(nodo.getNombre());
        lblDetRuta.setText(nodo.getRuta());
        lblDetTipo.setText(nodo.esDirectorio() ? "Directorio" : "Archivo regular");
        lblDetDueno.setText(nodo.getDueno());
        lblDetPermiso.setText(nodo.getPermiso() != null ? nodo.getPermiso().getEtiqueta() : "-");
        lblDetCreador.setText(nodo.getProcesoCreador());

        if (nodo.esDirectorio()) {
            lblDetInicio.setText("N/A");
            lblDetTamano.setText(nodo.getCantidadHijos() + " entrada(s)");
            lblDetColor.setIcon(null);
            lblDetColor.setText("N/A (directorio)");
            lblDetCadena.setText("N/A");
            lblDetCadena.setToolTipText(null);
            return;
        }

        lblDetInicio.setText(nodo.getPrimerBloque() >= 0 ? String.valueOf(nodo.getPrimerBloque()) : "-");
        lblDetTamano.setText(nodo.getTamanoBloques() + " bloque(s)");
        Color c = ModeloTablaArchivos.decodificarColor(nodo.getColorHex());
        lblDetColor.setIcon(ModeloTablaArchivos.crearIconoColor(c, 14, 12));
        lblDetColor.setText(nodo.getColorHex().isEmpty() ? "Por defecto" : nodo.getColorHex());

        if (cadena != null && !cadena.estaVacia()) {
            String textoCompleto = formatearCadena(cadena, false);
            lblDetCadena.setText(formatearCadena(cadena, true));
            lblDetCadena.setToolTipText(textoCompleto);
        } else {
            lblDetCadena.setText("-");
            lblDetCadena.setToolTipText(null);
        }
    }

    private void limpiarDetalles() {
        JLabel[] todos = {
            lblDetNombre, lblDetRuta, lblDetTipo, lblDetDueno, lblDetPermiso,
            lblDetCreador, lblDetInicio, lblDetTamano, lblDetColor, lblDetCadena
        };
        for (int i = 0; i < todos.length; i++) {
            todos[i].setText("-");
            todos[i].setIcon(null);
            todos[i].setToolTipText(null);
        }
    }

    private static String formatearCadena(ListaEnlazada<Integer> cadena, boolean resumida) {
        if (cadena == null || cadena.estaVacia()) {
            return "-";
        }
        int total = cadena.getTamano();
        StringBuilder sb = new StringBuilder();
        if (!resumida || total <= 8) {
            for (int i = 0; i < total; i++) {
                if (i > 0) {
                    sb.append(" → ");
                }
                sb.append(cadena.obtener(i));
            }
            return sb.toString();
        }
        for (int i = 0; i < 4; i++) {
            sb.append(cadena.obtener(i)).append(" → ");
        }
        sb.append("… → ").append(cadena.obtener(total - 1));
        return sb.toString();
    }

    // =========================================================================
    // ACCIONES CRUD Y VALIDACIONES DE MODO ADMINISTRADOR / USUARIO
    // =========================================================================

    private void accionCrear() {
        SistemaArchivos sa = obtenerSistemaArchivosActivo();
        if (sa == null) {
            return;
        }
        ModoUsuario modo = getModoSeleccionado();
        String usuario = getUsuarioActivo();

        NodoArchivo sel = obtenerNodoSeleccionadoArbol();
        String carpetaBase = "/";
        if (sel != null) {
            carpetaBase = sel.esDirectorio() ? sel.getRuta() : obtenerPadreVisual(sel.getRuta());
        }
        String rutaSugerida = "/".equals(carpetaBase) ? "/nuevo.txt" : carpetaBase + "/nuevo.txt";

        JTextField campoRuta = new JTextField(rutaSugerida, 22);
        JRadioButton rbArchivo = new JRadioButton("Archivo", true);
        JRadioButton rbDirectorio = new JRadioButton("Directorio", false);
        ButtonGroup bg = new ButtonGroup();
        bg.add(rbArchivo);
        bg.add(rbDirectorio);
        if (!modo.esAdministrador()) {
            rbDirectorio.setToolTipText("En modo Usuario solo el Administrador puede crear directorios");
        }

        JSpinner spinBloques = new JSpinner(new SpinnerNumberModel(4, 1, 10000, 1));
        if (spinBloques.getEditor() instanceof JSpinner.NumberEditor ne
                && ne.getTextField().getFormatter() instanceof NumberFormatter nf) {
            nf.setAllowsInvalid(false);
            nf.setCommitsOnValidEdit(true);
        }
        rbArchivo.addActionListener(e -> spinBloques.setEnabled(true));
        rbDirectorio.addActionListener(e -> spinBloques.setEnabled(false));

        JTextField campoDueno = new JTextField(usuario, 14);
        campoDueno.setEditable(modo.esAdministrador());
        campoDueno.setToolTipText(modo.esAdministrador()
                ? "Puedes asignar la carpeta o archivo a un usuario (ej. user1)"
                : "En modo Usuario el dueño es el usuario activo");
        JComboBox<PermisoArchivo> comboPermiso = new JComboBox<>(PermisoArchivo.values());
        comboPermiso.setSelectedItem(modo.esAdministrador() ? PermisoArchivo.PUBLICO : PermisoArchivo.PRIVADO);
        comboPermiso.setEnabled(modo.esAdministrador());

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Modo actual:"));
        form.add(new JLabel(modo.getEtiqueta()));
        form.add(new JLabel("Asignar dueño:"));
        form.add(campoDueno);
        form.add(new JLabel("Permisos:"));
        form.add(comboPermiso);
        form.add(new JLabel("Ruta completa:"));
        form.add(campoRuta);
        form.add(new JLabel("Tipo de entrada:"));
        JPanel pTipo = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        pTipo.add(rbArchivo);
        pTipo.add(rbDirectorio);
        form.add(pTipo);
        form.add(new JLabel("Tamaño (bloques):"));
        form.add(spinBloques);

        int res = VentanaEstado.mostrarConfirmacionAlFrente(this, form, "Crear archivo o directorio (E/S)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) {
            return;
        }

        boolean esDir = rbDirectorio.isSelected();
        int tam = esDir ? 0 : (Integer) spinBloques.getValue();
        String ruta = campoRuta.getText().trim();
        String duenoDestino = modo.esAdministrador() ? campoDueno.getText().trim() : usuario;
        PermisoArchivo permisoElegido = modo.esAdministrador()
                ? (PermisoArchivo) comboPermiso.getSelectedItem()
                : PermisoArchivo.PRIVADO;

        String error = validarOperacionGUI(sa, TipoOperacionES.CREAR, ruta, "", tam, modo, duenoDestino, esDir);
        if (error != null) {
            mostrarErrorValidacion(error);
            return;
        }
        encolarOperacion(TipoOperacionES.CREAR, ruta, "", tam, modo, duenoDestino, permisoElegido);
    }

    private void accionLeer() {
        SistemaArchivos sa = obtenerSistemaArchivosActivo();
        NodoArchivo sel = obtenerObjetivoSeleccionado();
        if (sa == null || sel == null) {
            return;
        }
        ModoUsuario modo = getModoSeleccionado();
        String usuario = getUsuarioActivo();

        String error = validarOperacionGUI(sa, TipoOperacionES.LEER, sel.getRuta(), "", 0, modo, usuario, false);
        if (error != null) {
            mostrarErrorValidacion(error);
            return;
        }
        encolarOperacion(TipoOperacionES.LEER, sel.getRuta(), "", 0, modo, usuario);
    }

    private void accionRenombrar() {
        SistemaArchivos sa = obtenerSistemaArchivosActivo();
        NodoArchivo sel = obtenerObjetivoSeleccionado();
        if (sa == null || sel == null) {
            return;
        }
        ModoUsuario modo = getModoSeleccionado();
        String usuario = getUsuarioActivo();

        // Validar permisos y restricciones del nodo ANTES de solicitar el nuevo nombre.
        String errorPrevio = validarOperacionGUI(sa, TipoOperacionES.ACTUALIZAR, sel.getRuta(),
                sel.getNombre(), 0, modo, usuario, sel.esDirectorio());
        if (errorPrevio != null) {
            mostrarErrorValidacion(errorPrevio);
            return;
        }

        String nuevoNombre = VentanaEstado.mostrarEntradaAlFrente(this,
                "Nuevo nombre para '" + sel.getRuta() + "':", sel.getNombre());
        if (nuevoNombre == null) {
            return;
        }
        String limpio = nuevoNombre.trim();
        if (limpio.equals(sel.getNombre())) {
            mostrarErrorValidacion("El nuevo nombre es igual al nombre actual ('" + sel.getNombre() + "').");
            return;
        }

        String error = validarOperacionGUI(sa, TipoOperacionES.ACTUALIZAR, sel.getRuta(),
                limpio, 0, modo, usuario, sel.esDirectorio());
        if (error != null) {
            mostrarErrorValidacion(error);
            return;
        }
        encolarOperacion(TipoOperacionES.ACTUALIZAR, sel.getRuta(), limpio, 0, modo, usuario);
    }

    private void accionEliminar() {
        SistemaArchivos sa = obtenerSistemaArchivosActivo();
        NodoArchivo sel = obtenerObjetivoSeleccionado();
        if (sa == null || sel == null) {
            return;
        }
        ModoUsuario modo = getModoSeleccionado();
        String usuario = getUsuarioActivo();

        String error = validarOperacionGUI(sa, TipoOperacionES.ELIMINAR, sel.getRuta(),
                "", 0, modo, usuario, sel.esDirectorio());
        if (error != null) {
            mostrarErrorValidacion(error);
            return;
        }

        String tipoDesc = sel.esDirectorio()
                ? "el directorio '" + sel.getRuta() + "' y todo su contenido recursivamente"
                : "el archivo '" + sel.getRuta() + "'";
        int confirm = VentanaEstado.mostrarConfirmacionAlFrente(this,
                "¿Encolar proceso de E/S para eliminar " + tipoDesc + "?",
                "Confirmar eliminación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        encolarOperacion(TipoOperacionES.ELIMINAR, sel.getRuta(), "", 0, modo, usuario);
    }

    private void encolarOperacion(TipoOperacionES op, String ruta, String nuevoNombre, int tamBloques,
                                  ModoUsuario modo, String usuario) {
        encolarOperacion(op, ruta, nuevoNombre, tamBloques, modo, usuario, null);
    }

    private void encolarOperacion(TipoOperacionES op, String ruta, String nuevoNombre, int tamBloques,
                                  ModoUsuario modo, String usuario, PermisoArchivo permisoSolicitado) {
        int idPc = obtenerIdComputadorPrincipal();
        if (idPc < 0) {
            mostrarErrorValidacion("No hay ningún computador registrado para ejecutar el proceso de E/S.");
            return;
        }
        try {
            String nombreProc = "GUI_" + op.getCodigoJson() + "_" + obtenerNombreBaseVisual(ruta);
            PCB creado = simulador.solicitarOperacionES(
                    idPc, nombreProc, op, ruta, nuevoNombre, tamBloques, modo, usuario, permisoSolicitado);
            mensajeUltimaAccion = "Encolado " + creado.getNombre()
                    + (simulador.estaEnMarcha() ? "" : " (pulsa Iniciar)");
            actualizar();
        } catch (RuntimeException ex) {
            mostrarErrorValidacion(ex.getMessage());
        }
    }

    /**
     * Valida sintaxis, existencia, permisos de {@link ModoUsuario#ADMINISTRADOR} vs.
     * {@link ModoUsuario#USUARIO} y espacio libre delegando en {@link SistemaArchivos#validarOperacion}.
     *
     * @return mensaje descriptivo del error si no es válido, o {@code null} si procede.
     */
    public static String validarOperacionGUI(SistemaArchivos sa, TipoOperacionES op, String ruta,
                                             String nuevoNombre, int tamanoBloques,
                                             ModoUsuario modo, String usuario, boolean esDirectorioCrear) {
        if (sa == null) {
            return "No hay un sistema de archivos activo.";
        }
        return sa.validarOperacion(op, ruta, nuevoNombre, tamanoBloques, modo, usuario, esDirectorioCrear);
    }

    private SistemaArchivos obtenerSistemaArchivosActivo() {
        if (simulador != null && simulador.getServicioArchivos() instanceof SistemaArchivos sa) {
            return sa;
        }
        mostrarErrorValidacion("Carga primero ambos archivos JSON en 'Cargar JSON / Config…'.");
        return null;
    }

    private NodoArchivo obtenerObjetivoSeleccionado() {
        NodoArchivo sel = obtenerNodoSeleccionadoArbol();
        if (sel == null && tablaArchivos.getSelectedRow() >= 0) {
            sel = modeloTabla.obtenerEnFila(tablaArchivos.getSelectedRow());
        }
        if (sel == null) {
            mostrarErrorValidacion("Selecciona primero un archivo o directorio en el árbol o en la tabla.");
            return null;
        }
        return sel;
    }

    private int obtenerIdComputadorPrincipal() {
        if (simulador == null) {
            return -1;
        }
        Iterador<Computador> it = simulador.iteradorComputadores();
        return it.haySiguiente() ? it.siguiente().getId() : -1;
    }

    public ModoUsuario getModoSeleccionado() {
        Object sel = comboModo.getSelectedItem();
        return (sel instanceof ModoUsuario m) ? m : ModoUsuario.ADMINISTRADOR;
    }

    public String getUsuarioActivo() {
        String u = txtUsuario.getText().trim();
        return u.isEmpty() ? (getModoSeleccionado().esAdministrador() ? "admin" : "") : u;
    }

    public JTree getArbolArchivos() {
        return arbolArchivos;
    }

    public JTable getTablaArchivos() {
        return tablaArchivos;
    }

    public JComboBox<ModoUsuario> getComboModo() {
        return comboModo;
    }

    public JTextField getTxtUsuario() {
        return txtUsuario;
    }

    private void mostrarErrorValidacion(String mensaje) {
        mensajeUltimaAccion = "Rechazado: " + mensaje;
        lblEstadoBarra.setText(mensajeUltimaAccion);
        if (simulador != null && simulador.getLogEventos() != null) {
            simulador.getLogEventos().registrar(
                    simulacion.LogEventos.TipoEvento.RECHAZO_ES,
                    "GUI [" + getModoSeleccionado().getEtiqueta() + "/" + getUsuarioActivo() + "]: " + mensaje);
        }
        VentanaEstado.mostrarMensajeAlFrente(this, mensaje, "Validación de archivos y permisos", JOptionPane.WARNING_MESSAGE);
    }

    private static String obtenerPadreVisual(String rutaNorm) {
        int pos = rutaNorm.lastIndexOf('/');
        return pos <= 0 ? "/" : rutaNorm.substring(0, pos);
    }

    private static String obtenerNombreBaseVisual(String rutaNorm) {
        int pos = rutaNorm.lastIndexOf('/');
        return pos < 0 ? rutaNorm : rutaNorm.substring(pos + 1);
    }

    /** Renderizador del árbol con iconos de carpeta y swatch de color por archivo. */
    private static final class RenderizadorArbolArchivos extends DefaultTreeCellRenderer {
        private final Icon iconoDir = UIManager.getIcon("FileView.directoryIcon");

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode dm && dm.getUserObject() instanceof NodoArchivo na) {
                if (na.esDirectorio()) {
                    if (iconoDir != null) {
                        setIcon(iconoDir);
                    }
                    String sufijo = "/".equals(na.getRuta()) ? " (" + na.getDueno() + ")"
                            : "  [" + na.getCantidadHijos() + ", " + na.getDueno() + "]";
                    setText(na.getNombre() + sufijo);
                } else {
                    Color c = ModeloTablaArchivos.decodificarColor(na.getColorHex());
                    setIcon(ModeloTablaArchivos.crearIconoColor(c, 12, 12));
                    String candado = na.esPublico() ? "" : " [Privado]";
                    setText(na.getNombre() + " [" + na.getTamanoBloques() + " blq]" + candado);
                }
            }
            return this;
        }
    }
}
