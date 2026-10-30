package gui;

import config.EscenarioDemo;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.SwingUtilities;
import simulacion.Simulador;

/**
 * Prueba de la ventana de métricas: muestra los datos y guarda el reporte.
 * Necesita pantalla (en NetBeans funciona; sin pantalla se omite).
 *
 * <p>Ejecutar: {@code java -cp "target/classes;target/test-classes" gui.VentanaMetricasPrueba}</p>
 */
public class VentanaMetricasPrueba {

    private static int fallas = 0;

    private static void verificar(boolean ok, String nombre) {
        System.out.println((ok ? "  OK    " : "  FALLA ") + nombre);
        if (!ok) {
            fallas++;
        }
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Sin pantalla: prueba omitida.");
            return;
        }
        Simulador sim = EscenarioDemo.crear();
        sim.ejecutar();
        File salida = File.createTempFile("avilaos-reporte", ".txt");
        salida.deleteOnExit();

        SwingUtilities.invokeAndWait(() -> {
            VentanaMetricas v = new VentanaMetricas(null, sim);
            v.setVisible(true);
            v.refrescar();
            String t = v.getTexto();
            verificar(t.contains("Throughput") && t.contains("Tiempo de respuesta promedio")
                    && t.contains("Deadlines cumplidos") && t.contains("Equidad"), "la ventana muestra las métricas globales");
            verificar(t.contains("PC-1") && t.contains("Por buffer"), "muestra la tabla por computador y la de buffers");
            v.guardarEn(salida);
            verificar(v.getEstado().contains("guardado"), "avisa que guardó el reporte");
            try {
                String contenido = Files.readString(salida.toPath(), StandardCharsets.UTF_8);
                verificar(contenido.contains("Reporte de simulación") && contenido.contains("Métricas de rendimiento"),
                        "el archivo contiene el reporte completo con métricas");
            } catch (java.io.IOException e) {
                verificar(false, "leer el archivo guardado");
            }
            v.dispose();
        });
        sim.getReloj().detener();
        System.out.println(fallas == 0 ? "\nTODAS LAS PRUEBAS PASARON" : "\nHAY " + fallas + " PRUEBAS FALLIDAS");
        System.exit(fallas == 0 ? 0 : 1);
    }
}
