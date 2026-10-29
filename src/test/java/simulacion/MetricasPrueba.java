package simulacion;

import enums.EstadoProceso;
import enums.PoliticaPlanificacion;
import enums.TipoInstruccion;
import enums.TipoProceso;
import estructuras.ListaEnlazada;
import modelo.Instruccion;
import modelo.PCB;
import nucleo.Computador;
import planificacion.FabricaPoliticas;
import sincronizacion.Buffer;

/**
 * Prueba de las métricas de rendimiento ({@link MetricasSimulacion}) con escenarios
 * pequeños cuyos resultados se calculan a mano.
 *
 * <p>Ejecutar: {@code java -cp "target/classes;target/test-classes" simulacion.MetricasPrueba}</p>
 */
public class MetricasPrueba {

    private static int fallas = 0;

    private static void verificar(boolean ok, String nombre) {
        System.out.println((ok ? "  OK    " : "  FALLA ") + nombre);
        if (!ok) {
            fallas++;
        }
    }

    private static boolean cerca(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    private static ListaEnlazada<Instruccion> programa(TipoInstruccion tipo, int n) {
        ListaEnlazada<Instruccion> p = new ListaEnlazada<>();
        for (int i = 0; i < n; i++) {
            p.agregar(new Instruccion(tipo));
        }
        return p;
    }

    private static PCB cpu(String nombre, int pc, int instrucciones, long deadline) {
        return new PCB(nombre, pc, TipoProceso.CPU_BOUND, 1, 100, deadline, "", 0, 0,
                programa(TipoInstruccion.CPU, instrucciones), 0);
    }

    private static PCB productor(String nombre, int pc, Buffer b, int n) {
        return new PCB(nombre, pc, TipoProceso.PRODUCTOR, 1, 100, 1000, b.getId(), n, 1,
                programa(TipoInstruccion.PRODUCIR, n), 0);
    }

    private static PCB consumidor(String nombre, int pc, Buffer b, int n) {
        return new PCB(nombre, pc, TipoProceso.CONSUMIDOR, 1, 100, 1000, b.getId(), n, 1,
                programa(TipoInstruccion.CONSUMIR, n), 0);
    }

    private static Simulador unPc(PoliticaPlanificacion pol, long ram) {
        Simulador sim = new Simulador();
        sim.registrarComputador(new Computador(1, "PC-1", ram, FabricaPoliticas.crear(pol)));
        return sim;
    }

    private static void avanzar(Simulador sim, int ciclos) {
        for (int i = 0; i < ciclos; i++) {
            sim.getReloj().avanzarCiclo();
        }
    }

    public static void main(String[] args) {
        System.out.println("1) dos procesos CPU-bound en FCFS (resultados calculados a mano)");
        Simulador s1 = unPc(PoliticaPlanificacion.FCFS, 1024);
        PCB a = cpu("A", 1, 4, 100);
        PCB b = cpu("B", 1, 3, 100);
        s1.cargarProceso(a);
        s1.cargarProceso(b);
        avanzar(s1, 8);
        MetricasSimulacion m = MetricasSimulacion.calcular(s1);
        verificar(a.getCicloInicio() == 1 && b.getCicloInicio() == 5, "A empieza en el ciclo 1 y B en el 5 (supuesto de la cuenta)");
        verificar(m.getProcesos() == 2 && m.getTerminados() == 2 && m.getCompletados() == 2, "2 procesos, 2 completados");
        verificar(m.getCiclos() == 8, "8 ciclos transcurridos (" + m.getCiclos() + ")");
        verificar(cerca(m.getThroughput(), 2.0 / 8), "throughput = 2/8 = 0.25");
        verificar(cerca(m.getRespuestaPromedio(), (1 + 5) / 2.0), "respuesta promedio = (1+5)/2 = 3");
        verificar(cerca(m.getPorcentajeDeadlines(), 100.0), "100 % de deadlines cumplidos");
        double xa = 4.0 / (a.getCicloFin() - 0), xb = 3.0 / (b.getCicloFin() - 0);
        double jain = (xa + xb) * (xa + xb) / (2 * (xa * xa + xb * xb));
        verificar(cerca(m.getEquidad(), jain) && m.getEquidad() < 1.0 && m.getEquidad() > 0.5,
                "equidad de Jain = " + String.format("%.4f", jain) + " (B esperó a A, reparto desigual)");
        verificar(cerca(m.getBloqueadoPromedio(), 0.0) && m.getBloqueadoMaximo() == 0, "nadie se bloqueó: promedio 0");
        verificar(m.getCantidadBuffers() == 0 && m.getCantidadComputadores() == 1, "sin buffers, 1 computador");

        System.out.println("2) un proceso vence su deadline antes de terminar");
        Simulador s2 = unPc(PoliticaPlanificacion.FCFS, 1024);
        PCB ok = cpu("OK", 1, 5, 100);
        PCB tarde = cpu("TARDE", 1, 5, 3);
        s2.cargarProceso(ok);
        s2.cargarProceso(tarde);
        avanzar(s2, 12);
        MetricasSimulacion m2 = MetricasSimulacion.calcular(s2);
        verificar(tarde.getEstado() == EstadoProceso.TERMINADO && tarde.getTiempoRestante() > 0, "TARDE fue cerrado por el sistema");
        verificar(m2.getTerminados() == 2 && m2.getCompletados() == 1 && m2.getTerminadosPorDeadline() == 1,
                "2 terminados: 1 completado y 1 cerrado por deadline");
        verificar(cerca(m2.getPorcentajeDeadlines(), 50.0), "50 % de deadlines cumplidos");
        verificar(m2.getThroughput() > 0 && m2.getThroughput() < 2.0 / 12 + 1e-9, "el cerrado por deadline no cuenta en el throughput");

        System.out.println("3) buffer N=1 con 3 productores: bloqueos y contadores");
        Simulador s3 = unPc(PoliticaPlanificacion.FCFS, 4096);
        Computador pc = s3.buscarComputador(1);
        Buffer buf = new Buffer("BUF", 1, 1, 4, pc);
        s3.registrarBuffer(buf);
        PCB p1 = productor("Prod-1", 1, buf, 1);
        PCB p2 = productor("Prod-2", 1, buf, 1);
        PCB p3 = productor("Prod-3", 1, buf, 1);
        s3.cargarProceso(p1);
        s3.cargarProceso(p2);
        s3.cargarProceso(p3);
        avanzar(s3, 6);
        MetricasSimulacion m3 = MetricasSimulacion.calcular(s3);
        verificar(m3.getBloqueadosAhora() == 2, "2 productores bloqueados ahora (" + m3.getBloqueadosAhora() + ")");
        verificar(m3.getCantidadBuffers() == 1 && m3.getBuffer(0).producidos == 1 && m3.getBuffer(0).consumidos == 0
                && m3.getBuffer(0).ocupacion == 1, "buffer: 1 producido, 0 consumidos, ocupación 1/1");
        long antes = p2.getTiempoBloqueadoAcumulado();
        verificar(antes > 0, "el tiempo bloqueado de Prod-2 ya no es 0 (" + antes + ")");
        avanzar(s3, 3);
        verificar(p2.getTiempoBloqueadoAcumulado() == antes + 3, "3 ciclos más bloqueado suman exactamente 3");
        verificar(MetricasSimulacion.calcular(s3).getBloqueadoMaximo() >= antes + 3, "el máximo bloqueado crece también");

        System.out.println("4) un consumidor libera a los productores: producidos - consumidos = ocupación");
        PCB c1 = consumidor("Cons-1", 1, buf, 1);
        s3.cargarProceso(c1);
        avanzar(s3, 40);
        MetricasSimulacion m4 = MetricasSimulacion.calcular(s3);
        MetricasSimulacion.PorBuffer pb = m4.getBuffer(0);
        verificar(pb.producidos >= 2 && pb.consumidos >= 1, "se produjo y se consumió (prod " + pb.producidos + ", cons " + pb.consumidos + ")");
        verificar(pb.producidos - pb.consumidos == pb.ocupacion, "invariante: producidos − consumidos = ocupación (" + pb.ocupacion + ")");
        verificar(pb.producidos == p1.getElementosProducidos() + p2.getElementosProducidos() + p3.getElementosProducidos()
                && pb.consumidos == c1.getElementosConsumidos(), "los contadores del buffer coinciden con los de los procesos");
        verificar(m4.getBloqueadoPromedio() > 0, "el promedio de tiempo bloqueado es mayor que 0");

        System.out.println("5) simulador vacío y reporte");
        Simulador s5 = unPc(PoliticaPlanificacion.FCFS, 1024);
        MetricasSimulacion m5 = MetricasSimulacion.calcular(s5);
        verificar(m5.getProcesos() == 0 && m5.getRespuestaPromedio() == MetricasSimulacion.NO_DISPONIBLE
                && m5.getPorcentajeDeadlines() == MetricasSimulacion.NO_DISPONIBLE
                && m5.getEquidad() == MetricasSimulacion.NO_DISPONIBLE, "sin procesos: los promedios son 'no disponible'");
        verificar(m5.formatear().contains("n/d"), "el texto muestra n/d y no lanza excepciones");
        verificar(new ReporteSimulacion(s1).generar().contains("Métricas de rendimiento"), "el reporte incluye la sección de métricas");
        boolean lanza = false;
        try {
            MetricasSimulacion.calcular(null);
        } catch (IllegalArgumentException e) {
            lanza = true;
        }
        verificar(lanza, "calcular(null) lanza IllegalArgumentException");

        s1.getReloj().detener();
        s2.getReloj().detener();
        s3.getReloj().detener();
        s5.getReloj().detener();
        System.out.println(fallas == 0 ? "\nTODAS LAS PRUEBAS PASARON" : "\nHAY " + fallas + " PRUEBAS FALLIDAS");
        System.exit(fallas == 0 ? 0 : 1);
    }
}
