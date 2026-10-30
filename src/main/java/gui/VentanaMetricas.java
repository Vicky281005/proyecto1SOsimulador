package gui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import simulacion.MetricasSimulacion;
import simulacion.ReporteSimulacion;
import simulacion.Simulador;

/**
 * Ventana de métricas de rendimiento: throughput, tiempo de respuesta promedio,
 * porcentaje de deadlines cumplidos, equidad, tiempo bloqueado y producidos/consumidos
 * por buffer, globales y por computador.
 *
 * <p>Solo observa: se refresca con un {@link javax.swing.Timer} y lee el estado dentro
 * de {@link Simulador#leerEstado(Runnable)}, igual que el resto de la Vista 2. El botón
 * "Guardar reporte…" escribe el {@link ReporteSimulacion} completo en un archivo de texto.</p>
 */
public class VentanaMetricas extends JDialog {

    private static final int REFRESCO_MS = 500;

    private final Simulador simulador;
    private final JTextArea area = new JTextArea(24, 100);
    private final JLabel lblEstado = new JLabel(" ");
    private final Timer temporizador;

    /**
     * @param dueno     ventana que la abre.
     * @param simulador simulador cuyas métricas se muestran.
     */
    public VentanaMetricas(JFrame dueno, Simulador simulador) {
        super(dueno, "ÁvilaOS – Métricas de rendimiento", false);
        this.simulador = simulador;

        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JButton btnGuardar = new JButton("Guardar reporte…");
        btnGuardar.addActionListener(e -> guardar());
        JPanel sur = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        sur.add(btnGuardar);
        sur.add(lblEstado);

        setLayout(new BorderLayout());
        add(new JScrollPane(area), BorderLayout.CENTER);
        add(sur, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(dueno);

        temporizador = new Timer(REFRESCO_MS, e -> refrescar());
        refrescar();
        temporizador.start();
    }

    /** Vuelve a calcular y muestra las métricas. No hace nada si la ventana está oculta. */
    void refrescar() {
        if (!isVisible() && area.getText().length() > 0) {
            return;
        }
        String[] texto = new String[1];
        simulador.leerEstado(() -> texto[0] = MetricasSimulacion.calcular(simulador).formatear());
        // Se conserva la posición de lectura si el usuario se desplazó.
        int caret = Math.min(area.getCaretPosition(), texto[0].length());
        area.setText(texto[0]);
        area.setCaretPosition(caret);
    }

    /** Texto mostrado ahora (para las pruebas de la ventana). */
    String getTexto() {
        return area.getText();
    }

    private void guardar() {
        JFileChooser selector = new JFileChooser();
        selector.setFileFilter(new FileNameExtensionFilter("Texto (*.txt)", "txt"));
        selector.setSelectedFile(new File(selector.getCurrentDirectory(), "reporte-avilaos.txt"));
        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File archivo = selector.getSelectedFile();
        if (!archivo.getName().toLowerCase().endsWith(".txt")) {
            archivo = new File(archivo.getParentFile(), archivo.getName() + ".txt");
        }
        guardarEn(archivo);
    }

    /** Escribe el reporte completo en {@code archivo}; avisa si no se pudo. */
    void guardarEn(File archivo) {
        String[] reporte = new String[1];
        simulador.leerEstado(() -> reporte[0] = new ReporteSimulacion(simulador).generar());
        try {
            Files.write(archivo.toPath(), reporte[0].getBytes(StandardCharsets.UTF_8));
            lblEstado.setText("Reporte guardado en " + archivo.getName());
        } catch (IOException ex) {
            lblEstado.setText("No se pudo guardar");
            JOptionPane.showMessageDialog(this, "No se pudo guardar el reporte:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    String getEstado() {
        return lblEstado.getText();
    }

    @Override
    public void dispose() {
        temporizador.stop();
        super.dispose();
    }
}
