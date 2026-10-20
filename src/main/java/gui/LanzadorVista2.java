package gui;

import enums.PoliticaPlanificacion;
import enums.TipoInstruccion;
import enums.TipoProceso;
import estructuras.ListaEnlazada;
import javax.swing.SwingUtilities;
import modelo.Instruccion;
import modelo.PCB;
import nucleo.Computador;
import planificacion.FabricaPoliticas;
import simulacion.Simulador;

/**
 * Lanzador provisional de la Vista 2 con un escenario de demostración.
 * Cuando exista la Vista 1 (configuración), esta será quien arme el simulador
 * y abra {@link VentanaEstado}.
 */
public class LanzadorVista2 {

    public static void main(String[] args) {
        Simulador simulador = new Simulador();

        Computador pc1 = new Computador(1, "PC-1", 1024,
                FabricaPoliticas.crear(PoliticaPlanificacion.FCFS));
        Computador pc2 = new Computador(2, "PC-2", 1024,
                FabricaPoliticas.crear(PoliticaPlanificacion.EDF));
        Computador pc3 = new Computador(3, "PC-3", 1024,
                FabricaPoliticas.crear(PoliticaPlanificacion.PRIORIDAD));
        simulador.registrarComputador(pc1);
        simulador.registrarComputador(pc2);
        simulador.registrarComputador(pc3);

        // Memoria grande por proceso: no caben todos a la vez y se ve la cola de nuevos.
        simulador.cargarProceso(proceso("P1", 1, 12, 500, 60, pc1.getId()));
        simulador.cargarProceso(proceso("P2", 1, 15, 500, 40, pc1.getId()));
        simulador.cargarProceso(proceso("P3", 1, 10, 500, 50, pc1.getId()));
        simulador.cargarProceso(proceso("P4", 1, 14, 600, 70, pc2.getId()));
        simulador.cargarProceso(proceso("P5", 1, 12, 600, 25, pc2.getId()));
        simulador.cargarProceso(proceso("P6", 3, 10, 500, 60, pc3.getId()));
        simulador.cargarProceso(proceso("P7", 1, 14, 500, 50, pc3.getId()));
        simulador.cargarProceso(proceso("P8", 2, 12, 500, 55, pc3.getId()));

        SwingUtilities.invokeLater(() -> new VentanaEstado(simulador).setVisible(true));
    }

    private static PCB proceso(String nombre, int prioridad, int instrucciones, long memoria,
                               long deadlineRelativo, int computador) {
        ListaEnlazada<Instruccion> programa = new ListaEnlazada<>();
        for (int i = 0; i < instrucciones; i++) {
            programa.agregar(new Instruccion(TipoInstruccion.CPU));
        }
        return new PCB(nombre, computador, TipoProceso.CPU_BOUND, prioridad, memoria,
                deadlineRelativo, "", 0, 0, programa, 0);
    }
}
