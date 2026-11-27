package config;

import archivos.SistemaArchivos;
import disco.DiscoSimulado;
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
 * Escenario de demostración reutilizable para Proyecto 2.
 *
 * <p>Arma un simulador con un único computador ({@code PC-1}, FCFS), su disco
 * simulado, el {@link SistemaArchivos} conectado y procesos de ejemplo para
 * mostrar la interfaz gráfica o el reporte en modo consola.</p>
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
        simulador.registrarComputador(pc1);

        // Disco de demostración (200 bloques, 20 de swap) y sistema de archivos conectado.
        DiscoSimulado disco = new DiscoSimulado("Disco-SD", DiscoSimulado.TOTAL_BLOQUES_POR_DEFECTO,
                DiscoSimulado.POSICION_CABEZAL_POR_DEFECTO, 20);
        SistemaArchivos sistemaArchivos = new SistemaArchivos(disco);
        simulador.setDisco(disco);
        simulador.setServicioArchivos(sistemaArchivos);

        // Procesos de demostración en el único computador (PC-1).
        simulador.cargarProceso(proceso("P1", 1, 12, 500, 60, pc1.getId()));
        simulador.cargarProceso(proceso("P2", 1, 15, 500, 40, pc1.getId()));
        simulador.cargarProceso(proceso("P3", 1, 10, 500, 50, pc1.getId()));
        simulador.cargarProceso(proceso("P4", 2, 14, 400, 70, pc1.getId()));
        simulador.cargarProceso(proceso("P5", 3, 12, 400, 55, pc1.getId()));

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
