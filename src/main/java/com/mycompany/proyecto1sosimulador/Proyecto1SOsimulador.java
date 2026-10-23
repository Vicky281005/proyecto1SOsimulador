package com.mycompany.proyecto1sosimulador;

import config.EscenarioDemo;
import gui.VentanaEstado;
import javax.swing.SwingUtilities;
import simulacion.ReporteSimulacion;
import simulacion.Simulador;

/**
 * Punto de entrada de ÁvilaOS.
 *
 * <p>Por defecto abre la interfaz gráfica (Vista 2) con el escenario de
 * demostración. Si se pasa el argumento {@code --consola}, se ejecuta en modo
 * síncrono y se imprime el reporte por la salida estándar.</p>
 */
public class Proyecto1SOsimulador {

    public static void main(String[] args) {
        boolean modoConsola = false;
        if (args != null) {
            for (String arg : args) {
                if ("--consola".equalsIgnoreCase(arg) || "-consola".equalsIgnoreCase(arg)) {
                    modoConsola = true;
                    break;
                }
            }
        }

        Simulador simulador = EscenarioDemo.crear();

        if (modoConsola) {
            simulador.ejecutar();
            new ReporteSimulacion(simulador).imprimir();
            return;
        }

        SwingUtilities.invokeLater(() -> new VentanaEstado(simulador).setVisible(true));
    }
}

