package gui;

import javax.swing.SwingUtilities;
import simulacion.Simulador;
import config.EscenarioDemo;

/**
 * Lanzador de la Vista 2 con el escenario de demostración.
 *
 * <p>Se mantiene como alias para ejecutar {@code Run File} sobre esta clase.
 * Delegado a {@link config.EscenarioDemo} para reutilizar el mismo escenario
 * que el punto de entrada principal.</p>
 */
public class LanzadorVista2 {

    public static void main(String[] args) {
        Simulador simulador = EscenarioDemo.crear();
        SwingUtilities.invokeLater(() -> new VentanaEstado(simulador).setVisible(true));
    }
}

