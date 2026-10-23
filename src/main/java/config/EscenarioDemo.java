package config;

import enums.PoliticaPlanificacion;
import enums.TipoInstruccion;
import enums.TipoProceso;
import estructuras.ListaEnlazada;
import modelo.Instruccion;
import modelo.PCB;
import nucleo.Computador;
import planificacion.FabricaPoliticas;
import simulacion.Simulador;

/**
 * Escenario de demostración reutilizable.
 *
 * <p>Arma un simulador con tres computadores (FCFS, EDF, Prioridades) y ocho
 * procesos para mostrar la interfaz gráfica o el reporte en modo consola. El
 * objetivo es que haya un único punto donde se define el escenario, evitando
 * duplicación entre los distintos lanzadores.</p>
 */
public final class EscenarioDemo {

    private EscenarioDemo() {
        // Clase de utilidad.
    }

    /**
     * Crea y configura un simulador con el escenario de demostración.
     *
     * @return simulador listo para ejecutar o para abrir en la GUI.
     */
    public static Simulador crear() {
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

        // Memoria grande por proceso para visualizar la cola de nuevos.
        simulador.cargarProceso(proceso("P1", 1, 12, 500, 60, pc1.getId()));
        simulador.cargarProceso(proceso("P2", 1, 15, 500, 40, pc1.getId()));
        simulador.cargarProceso(proceso("P3", 1, 10, 500, 50, pc1.getId()));
        simulador.cargarProceso(proceso("P4", 1, 14, 600, 70, pc2.getId()));
        simulador.cargarProceso(proceso("P5", 1, 12, 600, 25, pc2.getId()));
        simulador.cargarProceso(proceso("P6", 3, 10, 500, 60, pc3.getId()));
        simulador.cargarProceso(proceso("P7", 1, 14, 500, 50, pc3.getId()));
        simulador.cargarProceso(proceso("P8", 2, 12, 500, 55, pc3.getId()));

        return simulador;
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
