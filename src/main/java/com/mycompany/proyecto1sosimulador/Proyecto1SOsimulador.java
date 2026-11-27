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

        if (modoConsola) {
            Simulador simulador = EscenarioDemo.crear();
            simulador.ejecutar();
            new ReporteSimulacion(simulador).imprimir();
            return;
        }

        // Proyecto 2: exige cargar ambos archivos JSON obligatorios antes de iniciar el reloj global
        Simulador simuladorInicial = crearSimuladorPendienteDeJson();

        SwingUtilities.invokeLater(() -> new VentanaEstado(simuladorInicial).setVisible(true));
    }

    /**
     * Crea un simulador base de Proyecto 2 (1 único computador) en espera de que
     * el usuario cargue ambos archivos JSON obligatorios antes de iniciar el reloj.
     */
    public static Simulador crearSimuladorPendienteDeJson() {
        Simulador simuladorInicial = new Simulador();
        simuladorInicial.registrarComputador(new nucleo.Computador(
                1, "PC-1", 1024, planificacion.FabricaPoliticas.crear(enums.PoliticaPlanificacion.FCFS)));
        simuladorInicial.setRequiereCargaJsonObligatoria(true);
        return simuladorInicial;
    }
}

