package com.mycompany.proyecto1sosimulador;

import enums.PoliticaPlanificacion;
import enums.TipoInstruccion;
import enums.TipoProceso;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import modelo.Instruccion;
import modelo.PCB;
import nucleo.Computador;
import planificacion.FabricaPoliticas;
import simulacion.Simulador;

/**
 * Punto de entrada de ÁvilaOS.
 *
 * <p>Arma un escenario de demostración con varios computadores (cada uno con
 * una política distinta), carga procesos y corre la simulación con el
 * {@link Simulador}, mostrando por consola el resultado de cada computador.</p>
 */
public class Proyecto1SOsimulador {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println(" ÁvilaOS - Demostración de simulación");
        System.out.println("=================================================");

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

        long ciclos = simulador.ejecutar();

        Iterador<Computador> computadores = simulador.iteradorComputadores();
        int terminados = 0;
        while (computadores.haySiguiente()) {
            Computador computador = computadores.siguiente();
            imprimirComputador(computador);
            terminados += computador.getCantidadTerminados();
        }

        System.out.println();
        System.out.println("-------------------------------------------------");
        System.out.println(" Ciclos totales de simulación : " + ciclos);
        System.out.println(" Procesos terminados          : " + terminados);
        System.out.println(" Simulación terminada         : " + (simulador.isTerminada() ? "sí" : "no"));
        System.out.println(" Límite de seguridad alcanzado: " + (simulador.isLimiteAlcanzado() ? "SÍ" : "no"));
        System.out.println("-------------------------------------------------");
    }

    private static void imprimirComputador(Computador computador) {
        System.out.println();
        System.out.printf("%s  [%s]  RAM %d KB%n",
                computador.getNombre(),
                computador.getPlanificador().getPolitica().getNombre(),
                computador.getMemoriaTotal());

        Iterador<PCB> terminados = computador.iteradorTerminados();
        if (!terminados.haySiguiente()) {
            System.out.println("   (sin procesos terminados)");
            return;
        }
        while (terminados.haySiguiente()) {
            PCB proceso = terminados.siguiente();
            System.out.printf("   %-4s ciclos %d-%d   CPU=%d   deadline: %s%n",
                    proceso.getNombre(),
                    proceso.getCicloInicio(),
                    proceso.getCicloFin(),
                    proceso.getCiclosCpuConsumidos(),
                    proceso.isDeadlineCumplido() ? "cumplido" : "INCUMPLIDO");
        }
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
