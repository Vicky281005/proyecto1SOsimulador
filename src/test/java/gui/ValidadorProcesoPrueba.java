package gui;

import enums.PoliticaPlanificacion;
import enums.TipoInstruccion;
import enums.TipoProceso;
import modelo.PCB;
import nucleo.Computador;
import planificacion.FabricaPoliticas;
import simulacion.Simulador;

/**
 * Pruebas ejecutables de {@link ValidadorProceso} (sin Swing). Se corre con:
 * java -cp "target/classes;target/test-classes" gui.ValidadorProcesoPrueba
 */
public class ValidadorProcesoPrueba {

    private static int fallos = 0;

    private static void verificar(boolean condicion, String descripcion) {
        System.out.println((condicion ? "  OK    " : "  FALLA ") + descripcion);
        if (!condicion) {
            fallos++;
        }
    }

    /** Error esperado exactamente en un campo y en ningún otro. */
    private static boolean soloFalla(String[] errores, int campo) {
        for (int i = 0; i < errores.length; i++) {
            if ((errores[i] != null) != (i == campo)) {
                return false;
            }
        }
        return true;
    }

    private static String[] v(String nombre, String instr, String mem, String prio,
                              TipoProceso tipo, String dl, Computador pc, String periodo) {
        return ValidadorProceso.validar(nombre, instr, mem, prio, tipo, dl, pc, periodo);
    }

    public static void main(String[] args) {
        Computador pc = new Computador(1, "PC-1", 1024,
                FabricaPoliticas.crear(PoliticaPlanificacion.FCFS));
        TipoProceso cpu = TipoProceso.CPU_BOUND;

        System.out.println("1) un formulario correcto no tiene errores");
        verificar(ValidadorProceso.esValido(v("P1", "10", "100", "1", cpu, "50", pc, "")),
                "CPU Bound válido (el periodo de E/S se ignora)");
        verificar(ValidadorProceso.esValido(v("  P2  ", " 20 ", " 1024 ", "10", TipoProceso.IO_BOUND, "1", pc, "4")),
                "I/O Bound válido con espacios y valores en los límites");

        System.out.println("2) cada campo inválido se detecta, y solo él");
        verificar(soloFalla(v("", "10", "100", "1", cpu, "50", pc, ""), ValidadorProceso.CAMPO_NOMBRE), "nombre vacío");
        verificar(soloFalla(v("   ", "10", "100", "1", cpu, "50", pc, ""), ValidadorProceso.CAMPO_NOMBRE), "nombre solo espacios");
        verificar(soloFalla(v("x".repeat(31), "10", "100", "1", cpu, "50", pc, ""), ValidadorProceso.CAMPO_NOMBRE), "nombre de 31 caracteres");
        verificar(soloFalla(v(null, "10", "100", "1", cpu, "50", pc, ""), ValidadorProceso.CAMPO_NOMBRE), "nombre nulo");
        verificar(soloFalla(v("P", "abc", "100", "1", cpu, "50", pc, ""), ValidadorProceso.CAMPO_INSTRUCCIONES), "instrucciones con letras");
        verificar(soloFalla(v("P", "", "100", "1", cpu, "50", pc, ""), ValidadorProceso.CAMPO_INSTRUCCIONES), "instrucciones vacías");
        verificar(soloFalla(v("P", "0", "100", "1", cpu, "50", pc, ""), ValidadorProceso.CAMPO_INSTRUCCIONES), "0 instrucciones");
        verificar(soloFalla(v("P", "-5", "100", "1", cpu, "50", pc, ""), ValidadorProceso.CAMPO_INSTRUCCIONES), "instrucciones negativas");
        verificar(soloFalla(v("P", "1001", "100", "1", cpu, "50", pc, ""), ValidadorProceso.CAMPO_INSTRUCCIONES), "1001 instrucciones");
        verificar(soloFalla(v("P", "3.5", "100", "1", cpu, "50", pc, ""), ValidadorProceso.CAMPO_INSTRUCCIONES), "instrucciones decimales");
        verificar(soloFalla(v("P", "99999999999999999999", "100", "1", cpu, "50", pc, ""), ValidadorProceso.CAMPO_INSTRUCCIONES), "número gigante (desborda)");
        verificar(soloFalla(v("P", "10", "0", "1", cpu, "50", pc, ""), ValidadorProceso.CAMPO_MEMORIA), "memoria 0");
        verificar(soloFalla(v("P", "10", "1025", "1", cpu, "50", pc, ""), ValidadorProceso.CAMPO_MEMORIA), "memoria mayor que la RAM del computador");
        verificar(soloFalla(v("P", "10", "mucha", "1", cpu, "50", pc, ""), ValidadorProceso.CAMPO_MEMORIA), "memoria con letras");
        verificar(soloFalla(v("P", "10", "100", "0", cpu, "50", pc, ""), ValidadorProceso.CAMPO_PRIORIDAD), "prioridad 0");
        verificar(soloFalla(v("P", "10", "100", "11", cpu, "50", pc, ""), ValidadorProceso.CAMPO_PRIORIDAD), "prioridad 11");
        verificar(soloFalla(v("P", "10", "100", "alta", cpu, "50", pc, ""), ValidadorProceso.CAMPO_PRIORIDAD), "prioridad con letras");
        verificar(soloFalla(v("P", "10", "100", "1", cpu, "0", pc, ""), ValidadorProceso.CAMPO_DEADLINE), "deadline 0");
        verificar(soloFalla(v("P", "10", "100", "1", cpu, "100001", pc, ""), ValidadorProceso.CAMPO_DEADLINE), "deadline 100001");
        verificar(soloFalla(v("P", "10", "100", "1", cpu, "", pc, ""), ValidadorProceso.CAMPO_DEADLINE), "deadline vacío");
        verificar(soloFalla(v("P", "10", "100", "1", null, "50", pc, ""), ValidadorProceso.CAMPO_TIPO), "sin tipo");
        verificar(soloFalla(v("P", "10", "100", "1", TipoProceso.PRODUCTOR, "50", pc, ""), ValidadorProceso.CAMPO_TIPO), "tipo todavía no soportado (Productor)");
        verificar(soloFalla(v("P", "10", "100", "1", cpu, "50", null, ""), ValidadorProceso.CAMPO_COMPUTADOR), "sin computador destino");

        System.out.println("3) el periodo de E/S solo se valida en I/O Bound");
        TipoProceso io = TipoProceso.IO_BOUND;
        verificar(soloFalla(v("P", "10", "100", "1", io, "50", pc, ""), ValidadorProceso.CAMPO_PERIODO_ES), "I/O Bound sin periodo");
        verificar(soloFalla(v("P", "10", "100", "1", io, "50", pc, "0"), ValidadorProceso.CAMPO_PERIODO_ES), "periodo 0");
        verificar(soloFalla(v("P", "10", "100", "1", io, "50", pc, "11"), ValidadorProceso.CAMPO_PERIODO_ES), "periodo mayor que las instrucciones");
        verificar(ValidadorProceso.esValido(v("P", "10", "100", "1", io, "50", pc, "10")), "periodo igual a las instrucciones es válido");
        verificar(ValidadorProceso.esValido(v("P", "10", "100", "1", cpu, "50", pc, "basura")), "en CPU Bound el periodo se ignora aunque sea basura");

        System.out.println("4) varios errores a la vez se reportan todos");
        String[] varios = v("", "x", "-1", "99", cpu, "", pc, "");
        int cuantos = 0;
        for (String e : varios) {
            if (e != null) {
                cuantos++;
            }
        }
        verificar(cuantos == 5, "5 campos inválidos -> 5 errores (hay " + cuantos + ")");

        System.out.println("5) construir() arma el proceso correcto");
        PCB p = ValidadorProceso.construir(" P9 ", "6", "300", "2", cpu, "40", pc, "", 7);
        verificar(p.getNombre().equals("P9"), "nombre sin espacios sobrantes");
        verificar(p.getComputadorAsignado() == 1 && p.getTipo() == cpu, "computador y tipo");
        verificar(p.getTiempoRestante() == 6, "6 instrucciones");
        verificar(p.getMemoriaAsignada() == 300 && p.getPrioridad() == 2, "memoria y prioridad");
        verificar(p.getDeadlineAbsoluto() == 47, "deadline absoluto = ciclo 7 + 40 = 47 (es " + p.getDeadlineAbsoluto() + ")");
        PCB q = ValidadorProceso.construir("Q", "6", "100", "1", io, "40", pc, "3", 0);
        int es = 0;
        for (int i = 0; i < q.getPrograma().getTamano(); i++) {
            if (q.getPrograma().obtener(i).getTipo() == TipoInstruccion.READ_IO) {
                es++;
            }
        }
        verificar(es == 2, "I/O Bound de 6 instrucciones con periodo 3 -> 2 de E/S (hay " + es + ")");
        verificar(q.getPrograma().obtener(2).getTipo() == TipoInstruccion.READ_IO
                        && q.getPrograma().obtener(0).getTipo() == TipoInstruccion.CPU,
                "la E/S cae en la 3.ª y 6.ª instrucción");
        boolean lanzo = false;
        try {
            ValidadorProceso.construir("", "6", "100", "1", cpu, "40", pc, "", 0);
        } catch (IllegalArgumentException e) {
            lanzo = true;
        }
        verificar(lanzo, "construir() con datos inválidos lanza IllegalArgumentException");

        System.out.println("6) un proceso creado así corre de verdad en el simulador");
        Simulador sim = new Simulador();
        sim.registrarComputador(pc);
        sim.cargarProceso(ValidadorProceso.construir("A", "5", "100", "1", cpu, "50", pc, "", sim.getCicloActual()));
        sim.cargarProceso(ValidadorProceso.construir("B", "4", "100", "1", io, "50", pc, "2", sim.getCicloActual()));
        sim.ejecutar();
        verificar(pc.getCantidadTerminados() == 2, "los 2 procesos terminaron (" + pc.getCantidadTerminados() + ")");
        verificar(sim.getCicloActual() == 9, "9 ciclos = 5 + 4 (" + sim.getCicloActual() + ")");
        sim.detener();

        System.out.println();
        System.out.println(fallos == 0 ? "TODAS LAS PRUEBAS PASARON" : "HAY " + fallos + " PRUEBAS FALLIDAS");
        if (fallos > 0) {
            System.exit(1);
        }
    }
}
