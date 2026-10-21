package gui;

import enums.TipoProceso;
import estructuras.Iterador;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.Border;
import modelo.PCB;
import nucleo.Computador;
import simulacion.Simulador;

/**
 * Vista 1 de ÁvilaOS: formulario para crear procesos mientras la simulación
 * corre (o antes de iniciarla).
 *
 * <p>Todas las entradas se validan con {@link ValidadorProceso}. Si algo es
 * inválido, el formulario marca en rojo los campos incorrectos y muestra el
 * aviso, <b>sin interrumpir la simulación</b>: no se lanza ninguna excepción hacia
 * el resto del sistema y no se abre ningún diálogo que bloquee.</p>
 */
public class VentanaCrearProceso extends JDialog {

    private final Simulador simulador;

    private final JTextField txtNombre = new JTextField(14);
    private final JTextField txtInstrucciones = new JTextField("10", 14);
    private final JTextField txtMemoria = new JTextField("100", 14);
    private final JTextField txtPrioridad = new JTextField("1", 14);
    private final JTextField txtDeadline = new JTextField("50", 14);
    private final JTextField txtPeriodoEs = new JTextField("3", 14);
    private final JComboBox<TipoProceso> comboTipo = new JComboBox<>(
            new TipoProceso[]{TipoProceso.CPU_BOUND, TipoProceso.IO_BOUND});
    private final JComboBox<Computador> comboComputador = new JComboBox<>();
    private final JLabel lblAviso = new JLabel(" ");

    /** Campos en el mismo orden que los índices de {@link ValidadorProceso}. */
    private final Component[] campos = new Component[ValidadorProceso.CANTIDAD_CAMPOS];
    private final Border bordeNormal = txtNombre.getBorder();
    private final Border bordeError = BorderFactory.createLineBorder(Color.RED, 2);

    /**
     * @param dueno     ventana que abre este formulario.
     * @param simulador simulador al que se envían los procesos creados.
     */
    public VentanaCrearProceso(JFrame dueno, Simulador simulador) {
        super(dueno, "ÁvilaOS – Vista 1: crear proceso", false);
        this.simulador = simulador;

        Iterador<Computador> it = simulador.iteradorComputadores();
        while (it.haySiguiente()) {
            comboComputador.addItem(it.siguiente());
        }
        comboComputador.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> lista, Object valor, int i,
                                                          boolean sel, boolean foco) {
                Object texto = valor instanceof Computador c
                        ? c.getNombre() + "  (RAM " + c.getMemoriaTotal() + " KB)" : valor;
                return super.getListCellRendererComponent(lista, texto, i, sel, foco);
            }
        });

        campos[ValidadorProceso.CAMPO_NOMBRE] = txtNombre;
        campos[ValidadorProceso.CAMPO_INSTRUCCIONES] = txtInstrucciones;
        campos[ValidadorProceso.CAMPO_MEMORIA] = txtMemoria;
        campos[ValidadorProceso.CAMPO_PRIORIDAD] = txtPrioridad;
        campos[ValidadorProceso.CAMPO_TIPO] = comboTipo;
        campos[ValidadorProceso.CAMPO_DEADLINE] = txtDeadline;
        campos[ValidadorProceso.CAMPO_COMPUTADOR] = comboComputador;
        campos[ValidadorProceso.CAMPO_PERIODO_ES] = txtPeriodoEs;

        construir();
        comboTipo.addActionListener(e -> actualizarCampoPeriodo());
        actualizarCampoPeriodo();
        pack();
        setResizable(false);
        setLocationRelativeTo(dueno);
    }

    private void construir() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        fila(form, g, 0, "Nombre:", txtNombre);
        fila(form, g, 1, "Tipo:", comboTipo);
        fila(form, g, 2, "Instrucciones:", txtInstrucciones);
        fila(form, g, 3, "Memoria (KB):", txtMemoria);
        fila(form, g, 4, "Prioridad (1 = más alta):", txtPrioridad);
        fila(form, g, 5, "Deadline (ciclos):", txtDeadline);
        fila(form, g, 6, "Computador destino:", comboComputador);
        fila(form, g, 7, "E/S cada N instrucciones:", txtPeriodoEs);

        lblAviso.setVerticalAlignment(JLabel.TOP);
        g.gridx = 0;
        g.gridy = 8;
        g.gridwidth = 2;
        form.add(lblAviso, g);

        JButton btnCrear = new JButton("Crear proceso");
        JButton btnLimpiar = new JButton("Limpiar");
        btnCrear.addActionListener(e -> crear());
        btnLimpiar.addActionListener(e -> limpiar());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(btnLimpiar);
        botones.add(btnCrear);
        g.gridy = 9;
        form.add(botones, g);

        setContentPane(form);
        getRootPane().setDefaultButton(btnCrear);
    }

    private void fila(JPanel p, GridBagConstraints g, int fila, String etiqueta, Component campo) {
        g.gridwidth = 1;
        g.gridy = fila;
        g.gridx = 0;
        g.weightx = 0;
        p.add(new JLabel(etiqueta), g);
        g.gridx = 1;
        g.weightx = 1;
        p.add(campo, g);
    }

    /** El periodo de E/S solo tiene sentido para procesos I/O Bound. */
    private void actualizarCampoPeriodo() {
        txtPeriodoEs.setEnabled(comboTipo.getSelectedItem() == TipoProceso.IO_BOUND);
    }

    /** Valida el formulario y, si todo es correcto, envía el proceso al simulador. */
    private void crear() {
        TipoProceso tipo = (TipoProceso) comboTipo.getSelectedItem();
        Computador destino = (Computador) comboComputador.getSelectedItem();
        String[] errores = ValidadorProceso.validar(txtNombre.getText(), txtInstrucciones.getText(),
                txtMemoria.getText(), txtPrioridad.getText(), tipo, txtDeadline.getText(),
                destino, txtPeriodoEs.getText());

        if (!ValidadorProceso.esValido(errores)) {
            marcarErrores(errores);
            return;
        }
        try {
            PCB proceso = ValidadorProceso.construir(txtNombre.getText(), txtInstrucciones.getText(),
                    txtMemoria.getText(), txtPrioridad.getText(), tipo, txtDeadline.getText(),
                    destino, txtPeriodoEs.getText(), simulador.getCicloActual());
            simulador.cargarProceso(proceso);
            limpiarMarcas();
            mostrar("Proceso " + proceso.getNombre() + " (id " + proceso.getId()
                    + ") creado en " + destino.getNombre() + ".", new Color(30, 130, 60));
            txtNombre.setText("");
            txtNombre.requestFocusInWindow();
        } catch (RuntimeException ex) {
            // Cualquier rechazo del sistema se muestra como aviso; la simulación sigue corriendo.
            mostrar("No se pudo crear el proceso: " + ex.getMessage(), Color.RED);
        }
    }

    private void marcarErrores(String[] errores) {
        StringBuilder html = new StringBuilder("<html><b>Revisa estos campos:</b><br>");
        for (int i = 0; i < errores.length; i++) {
            JComponentBorde.poner(campos[i], errores[i] != null ? bordeError : bordeNormal);
            if (errores[i] != null) {
                html.append("• ").append(errores[i]).append("<br>");
            }
        }
        mostrar(html.append("</html>").toString(), Color.RED);
        pack();
    }

    private void limpiarMarcas() {
        for (Component c : campos) {
            JComponentBorde.poner(c, bordeNormal);
        }
    }

    private void limpiar() {
        txtNombre.setText("");
        txtInstrucciones.setText("10");
        txtMemoria.setText("100");
        txtPrioridad.setText("1");
        txtDeadline.setText("50");
        txtPeriodoEs.setText("3");
        comboTipo.setSelectedIndex(0);
        limpiarMarcas();
        mostrar(" ", Color.BLACK);
        pack();
    }

    private void mostrar(String texto, Color color) {
        lblAviso.setForeground(color);
        lblAviso.setText(texto);
    }

    /** Pequeña ayuda para poner un borde en cualquier componente Swing. */
    private static final class JComponentBorde {
        static void poner(Component c, Border b) {
            if (c instanceof javax.swing.JComponent jc) {
                jc.setBorder(b);
            }
        }
    }
}
