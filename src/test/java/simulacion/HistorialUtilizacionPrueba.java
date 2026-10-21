package simulacion;

import enums.PoliticaPlanificacion;
import enums.TipoInstruccion;
import enums.TipoProceso;
import estructuras.ListaEnlazada;
import modelo.Instruccion;
import modelo.PCB;
import nucleo.Computador;
import planificacion.FabricaPoliticas;

/**
 * Pruebas ejecutables del historial de utilización de CPU. Se corren con:
 * java -cp "target/classes;target/test-classes" simulacion.HistorialUtilizacionPrueba
 */
public class HistorialUtilizacionPrueba {

    private static int fallos = 0;

    private static void verificar(boolean condicion, String descripcion) {
        System.out.println((condicion ? "  OK    " : "  FALLA ") + descripcion);
        if (!condicion) {
            fallos++;
        }
    }

    private static boolean casi(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    private static Computador pc(int id) {
        return new Computador(id, "PC-" + id, 100_000,
                FabricaPoliticas.crear(PoliticaPlanificacion.FCFS));
    }

    private static PCB proceso(String nombre, int instrucciones, int computador) {
        ListaEnlazada<Instruccion> programa = new ListaEnlazada<>();
        for (int i = 0; i < instrucciones; i++) {
            programa.agregar(new Instruccion(TipoInstruccion.CPU));
        }
        return new PCB(nombre, computador, TipoProceso.CPU_BOUND, 1, 10, 100_000, "", 0, 0, programa, 0);
    }

    public static void main(String[] args) {
        System.out.println("1) la utilización total coincide con la de la demo (100 / 70 / 90 %)");
        Simulador sim = new Simulador();
        Computador pc1 = pc(1), pc2 = pc(2), pc3 = pc(3);
        sim.registrarComputador(pc1);
        sim.registrarComputador(pc2);
        sim.registrarComputador(pc3);
        int[] cargas = {3, 5, 2, 4, 3, 2, 4, 3};
        int[] destino = {1, 1, 1, 2, 2, 3, 3, 3};
        for (int i = 0; i < cargas.length; i++) {
            sim.cargarProceso(proceso("P" + (i + 1), cargas[i], destino[i]));
        }
        sim.ejecutar();
        HistorialUtilizacion h = sim.getHistorialUtilizacion();
        verificar(h.getCantidadSeries() == 3, "3 series");
        verificar(h.getCantidadCiclos(0) == 10 && h.getCantidadCiclos(1) == 10 && h.getCantidadCiclos(2) == 10,
                "10 ciclos registrados en cada una");
        verificar(casi(h.getUtilizacionTotal(0), 100.0), "PC-1 100 % (" + h.getUtilizacionTotal(0) + ")");
        verificar(casi(h.getUtilizacionTotal(1), 70.0), "PC-2 70 % (" + h.getUtilizacionTotal(1) + ")");
        verificar(casi(h.getUtilizacionTotal(2), 90.0), "PC-3 90 % (" + h.getUtilizacionTotal(2) + ")");
        verificar(h.getNombre(1).equals("PC-2"), "los nombres se conservan");
        verificar(casi(h.getUtilizacionTotal(1), 100.0 * pc2.getCpu().getCiclosEjecutados() / sim.getCicloActual()),
                "coincide con los ciclos ejecutados reales de la CPU");
        sim.detener();

        System.out.println("2) valor instantáneo y promedio móvil");
        Simulador s2 = new Simulador();
        Computador a = pc(1), b = pc(2);
        s2.registrarComputador(a);
        s2.registrarComputador(b);
        s2.cargarProceso(proceso("A", 4, 1));   // A ocupada los ciclos 1-4, ociosa del 5 al 10
        s2.cargarProceso(proceso("B", 10, 2));  // B mantiene viva la simulación 10 ciclos
        s2.ejecutar();
        double[][] inst = s2.getHistorialUtilizacion().utilizacion(0, 1, 100);
        verificar(inst[0].length == 10 && inst[1].length == 10, "10 puntos");
        verificar(casi(inst[0][0], 1) && casi(inst[0][9], 10), "el eje X va del ciclo 1 al 10");
        verificar(casi(inst[1][3], 100) && casi(inst[1][4], 0), "instantáneo: 100 % en el ciclo 4 y 0 % en el 5");
        double[][] mov = s2.getHistorialUtilizacion().utilizacion(0, 4, 100);
        verificar(casi(mov[1][0], 100), "ventana 4, ciclo 1: 100 % (solo hay 1 dato)");
        verificar(casi(mov[1][5], 50), "ventana 4, ciclo 6: ciclos 3-6 = 100,100,0,0 -> 50 % (" + mov[1][5] + ")");
        verificar(casi(mov[1][9], 0), "ventana 4, ciclo 10: 0 %");
        s2.detener();

        System.out.println("3) con muchos ciclos se reduce la cantidad de puntos");
        Simulador s3 = new Simulador();
        Computador c = pc(1);
        s3.registrarComputador(c);
        s3.cargarProceso(proceso("Largo", 1000, 1));
        s3.ejecutar();
        double[][] largo = s3.getHistorialUtilizacion().utilizacion(0, 10, 100);
        verificar(largo[0].length <= 101, "máximo ~100 puntos (hay " + largo[0].length + ")");
        verificar(casi(largo[0][0], 1), "empieza en el ciclo 1");
        verificar(casi(largo[0][largo[0].length - 1], 1000), "el último punto es el ciclo 1000");
        verificar(casi(largo[1][largo[1].length - 1], 100), "CPU siempre ocupada -> 100 %");
        boolean creciente = true;
        for (int i = 1; i < largo[0].length; i++) {
            if (largo[0][i] <= largo[0][i - 1]) {
                creciente = false;
            }
        }
        verificar(creciente, "el eje X es estrictamente creciente");
        s3.detener();

        System.out.println("4) un computador registrado a mitad de la simulación empieza su serie después");
        Simulador s4 = new Simulador();
        Computador p1 = pc(1);
        s4.registrarComputador(p1);
        s4.cargarProceso(proceso("X", 5, 1));
        s4.ejecutar();
        Computador p2 = pc(2);
        s4.registrarComputador(p2);
        s4.cargarProceso(proceso("Y", 3, 2));
        s4.cargarProceso(proceso("Z", 3, 1));
        s4.ejecutar();
        HistorialUtilizacion h4 = s4.getHistorialUtilizacion();
        double[][] tardia = h4.utilizacion(1, 1, 100);
        verificar(h4.getCantidadSeries() == 2, "2 series");
        verificar(casi(tardia[0][0], 6), "la serie de PC-2 empieza en el ciclo 6 (empieza en " + tardia[0][0] + ")");
        verificar(h4.getCantidadCiclos(0) == 8 && h4.getCantidadCiclos(1) == 3,
                "PC-1 tiene 8 ciclos y PC-2 tiene 3 (" + h4.getCantidadCiclos(0) + " y " + h4.getCantidadCiclos(1) + ")");
        s4.detener();

        System.out.println("5) sin datos y validaciones");
        HistorialUtilizacion vacio = new HistorialUtilizacion();
        verificar(vacio.getCantidadSeries() == 0, "historial vacío");
        boolean fuera = false;
        try {
            vacio.getNombre(0);
        } catch (IndexOutOfBoundsException e) {
            fuera = true;
        }
        verificar(fuera, "índice inexistente lanza IndexOutOfBoundsException");
        boolean invalido = false;
        try {
            h.utilizacion(0, 0, 100);
        } catch (IllegalArgumentException e) {
            invalido = true;
        }
        verificar(invalido, "ventana 0 lanza IllegalArgumentException");

        System.out.println();
        System.out.println(fallos == 0 ? "TODAS LAS PRUEBAS PASARON" : "HAY " + fallos + " PRUEBAS FALLIDAS");
        if (fallos > 0) {
            System.exit(1);
        }
    }
}
