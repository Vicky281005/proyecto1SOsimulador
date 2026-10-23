package com.mycompany.proyecto1sosimulador;

import enums.PoliticaPlanificacion;
import enums.TipoInstruccion;
import enums.TipoProceso;
import estructuras.ListaEnlazada;
import modelo.Instruccion;
import modelo.PCB;
import nucleo.Computador;
import planificacion.FabricaPoliticas;
import simulacion.ReporteSimulacion;
import simulacion.Simulador;

/**
 * Punto de entrada de ÁvilaOS.
 *
 * <p>Arma un escenario de demostración con varios computadores (cada uno con
 * una política distinta), carga procesos, corre la simulación con el
 * {@link Simulador} y muestra el reporte con {@link ReporteSimulacion}.</p>
 */
public class Proyecto1SOsimulador {

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

        // PC-1 (FCFS): se ejecutan en orden de llegada.
        simulador.cargarProceso(proceso("P1", 1, 3, 200, 40, pc1.getId()));
        simulador.cargarProceso(proceso("P2", 1, 5, 200, 20, pc1.getId()));
        simulador.cargarProceso(proceso("P3", 1, 2, 200, 30, pc1.getId()));

        // PC-2 (EDF): se ejecutan por deadline más próximo, sin importar el orden de llegada.
        simulador.cargarProceso(proceso("P4", 1, 4, 300, 50, pc2.getId()));
        simulador.cargarProceso(proceso("P5", 1, 3, 300, 10, pc2.getId()));

        // PC-3 (Prioridades): menor número = mayor prioridad.
        simulador.cargarProceso(proceso("P6", 3, 2, 200, 40, pc3.getId()));
        simulador.cargarProceso(proceso("P7", 1, 4, 200, 30, pc3.getId()));
        simulador.cargarProceso(proceso("P8", 2, 3, 200, 30, pc3.getId()));

        simulador.ejecutar();
        new ReporteSimulacion(simulador).imprimir();
    }

    /**
     * Construye un proceso CPU-bound con un programa de instrucciones de CPU.
     *
     * @param nombre           nombre del proceso.
     * @param prioridad        prioridad (menor número = mayor prioridad).
     * @param instrucciones    cantidad de instrucciones del programa.
     * @param memoria          memoria solicitada, en KB.
     * @param deadlineRelativo deadline relativo al ciclo de creación.
     * @param computador       id del computador al que se asigna.
     * @return el proceso listo para ser cargado.
     */
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
