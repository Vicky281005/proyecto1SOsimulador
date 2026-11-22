package simulacion;

import disco.DiscoSimulado;
import disco.SolicitudES;
import enums.PoliticaDisco;
import enums.TipoInstruccion;
import enums.TipoOperacionES;
import enums.TipoProceso;
import estructuras.ListaEnlazada;
import modelo.Instruccion;
import modelo.PCB;
import nucleo.Computador;
import planificacion.FCFS;

/** Prueba de #81: muestreo de la serie del cabezal y métricas del disco. */
public class GraficaDiscoPrueba {

    private static int ok = 0, fallos = 0;

    private static void verificar(boolean c, String m) {
        if (c) { ok++; System.out.println("  OK    " + m); }
        else { fallos++; System.out.println("  FALLA " + m); }
    }

    private static ListaEnlazada<Long> lista(long... v) {
        ListaEnlazada<Long> l = new ListaEnlazada<>();
        for (long x : v) l.agregar(x);
        return l;
    }

    private static PCB pcb(String n) {
        ListaEnlazada<Instruccion> prog = new ListaEnlazada<>();
        prog.agregar(new Instruccion(TipoInstruccion.CPU));
        return new PCB(n, 1, TipoProceso.IO_BOUND, 1, 4, 100000, "", 0, 0, prog, 0);
    }

    public static void main(String[] args) {
        System.out.println("-- Muestreo de la serie");
        double[][] vacia = SerieDesplazamiento.muestrear(lista(), 10);
        verificar(vacia[0].length == 0 && vacia[1].length == 0, "historial vacío -> sin puntos");

        double[][] corta = SerieDesplazamiento.muestrear(lista(0, 5, 5, 12), 10);
        verificar(corta[0].length == 4 && corta[0][0] == 1 && corta[0][3] == 4, "historial corto: un punto por ciclo (x=1..4)");
        verificar(corta[1][0] == 0 && corta[1][1] == 5 && corta[1][3] == 12, "los valores y se conservan");

        long[] datos = new long[1000];
        for (int i = 0; i < datos.length; i++) datos[i] = i * 3L;
        double[][] larga = SerieDesplazamiento.muestrear(lista(datos), 100);
        verificar(larga[0].length <= 101, "historial largo: se reduce a <= maxPuntos+1 (" + larga[0].length + ")");
        verificar(larga[0][larga[0].length - 1] == 1000 && larga[1][larga[1].length - 1] == 2997,
                "el último punto es siempre el último ciclo real");
        boolean creciente = true;
        for (int i = 1; i < larga[0].length; i++) if (larga[0][i] <= larga[0][i - 1]) creciente = false;
        verificar(creciente, "los ciclos (x) quedan estrictamente crecientes");

        double[][] justo = SerieDesplazamiento.muestrear(lista(1, 2, 3, 4, 5), 5);
        verificar(justo[0].length == 5, "justo en el máximo: no se descarta nada");

        boolean lanza = false;
        try { SerieDesplazamiento.muestrear(null, 10); } catch (IllegalArgumentException e) { lanza = true; }
        boolean lanza2 = false;
        try { SerieDesplazamiento.muestrear(lista(1), 1); } catch (IllegalArgumentException e) { lanza2 = true; }
        verificar(lanza && lanza2, "argumentos inválidos lanzan IllegalArgumentException");

        System.out.println("-- Serie real del disco");
        Simulador sim = new Simulador();
        sim.registrarComputador(new Computador(1, "PC-1", 10, new FCFS()));
        DiscoSimulado d = new DiscoSimulado("Disco", 200, 50, 20);
        sim.setDisco(d);
        d.setPolitica(PoliticaDisco.SSTF);
        d.encolarSolicitud(new SolicitudES(pcb("P1"), TipoOperacionES.LEER, "/a", 70, 1, 0));
        d.encolarSolicitud(new SolicitudES(pcb("P2"), TipoOperacionES.LEER, "/b", 20, 1, 0));
        for (long c = 1; c <= 120; c++) d.tick(c);
        ListaEnlazada<Long> hist = d.copiarHistorialDesplazamiento();
        double[][] real = SerieDesplazamiento.muestrear(hist, 50);
        verificar(hist.getTamano() == 120, "el disco guardó un valor por ciclo (120)");
        boolean mono = true;
        for (int i = 1; i < real[1].length; i++) if (real[1][i] < real[1][i - 1]) mono = false;
        verificar(mono, "el desplazamiento acumulado nunca decrece");
        verificar(real[1][real[1].length - 1] == d.getDesplazamientoTotal() && d.getDesplazamientoTotal() > 0,
                "la serie termina en el desplazamiento total del disco (" + d.getDesplazamientoTotal() + ")");

        System.out.println("-- Métricas del disco");
        MetricasSimulacion m = MetricasSimulacion.calcular(sim);
        verificar(m.getDisco().hayDisco, "las métricas ven el disco");
        verificar(m.getDisco().desplazamientoTotal == d.getDesplazamientoTotal(), "desplazamiento total coincide con el disco");
        verificar(m.getDisco().solicitudesAtendidas == 2 && d.getTotalSolicitudesAtendidas() == 2, "2 solicitudes atendidas");
        verificar(m.getDisco().porPolitica[PoliticaDisco.SSTF.ordinal()] == 2
                && m.getDisco().porPolitica[PoliticaDisco.FIFO.ordinal()] == 0, "atendidas por política: SSTF=2, FIFO=0");
        verificar(Math.abs(m.getDisco().esperaPromedio - d.getTiempoEsperaPromedio()) < 1e-9, "espera promedio coincide");
        String t = m.formatear();
        verificar(t.contains("Disco y planificación de E/S") && t.contains("Desplazamiento total del cabezal")
                && t.contains("SSTF=2"), "el reporte de texto incluye la sección del disco");

        Simulador sinDisco = new Simulador();
        sinDisco.registrarComputador(new Computador(1, "PC-1", 10, new FCFS()));
        MetricasSimulacion m2 = MetricasSimulacion.calcular(sinDisco);
        verificar(!m2.getDisco().hayDisco && m2.formatear().contains("sin disco configurado"),
                "sin disco: avisa en lugar de fallar");

        System.out.println(fallos == 0 ? "TODAS LAS PRUEBAS PASARON (" + ok + ")" : "HAY " + fallos + " FALLOS");
        if (fallos > 0) System.exit(1);
    }
}
