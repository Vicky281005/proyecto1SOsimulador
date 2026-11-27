package gui;

import disco.BloqueDisco;
import disco.DiscoSimulado;
import disco.SolicitudES;
import enums.PoliticaDisco;
import enums.TipoInstruccion;
import enums.TipoOperacionES;
import enums.TipoProceso;
import estructuras.ListaEnlazada;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import modelo.Instruccion;
import modelo.PCB;
import nucleo.Computador;
import planificacion.FCFS;
import simulacion.Simulador;

/**
 * Prueba de la vista del disco (necesita pantalla; sin pantalla se omite).
 * Ejecutar: {@code java -cp "target/classes;target/test-classes;GSON" gui.VentanaDiscoPrueba}
 */
public class VentanaDiscoPrueba {

    private static int ok = 0, fallos = 0;

    private static void verificar(boolean c, String m) {
        if (c) { ok++; System.out.println("  OK    " + m); }
        else { fallos++; System.out.println("  FALLA " + m); }
    }

    private static PCB pcb(String n) {
        ListaEnlazada<Instruccion> prog = new ListaEnlazada<>();
        prog.agregar(new Instruccion(TipoInstruccion.CPU));
        return new PCB(n, 1, TipoProceso.IO_BOUND, 1, 4, 100000, "", 0, 0, prog, 0);
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Sin pantalla: prueba omitida.");
            return;
        }
        // 1) Sin disco
        Simulador vacio = new Simulador();
        vacio.registrarComputador(new Computador(1, "PC-1", 10, new FCFS()));
        VentanaDisco[] v = new VentanaDisco[1];
        SwingUtilities.invokeAndWait(() -> { v[0] = new VentanaDisco(null, vacio); v[0].setVisible(true); });
        verificar(v[0].getTextoCabezal().contains("No hay disco"), "sin disco: avisa que no hay disco configurado");
        SwingUtilities.invokeAndWait(() -> v[0].dispose());

        // 2) Con disco
        Simulador sim = new Simulador();
        sim.registrarComputador(new Computador(1, "PC-1", 10, new FCFS()));
        DiscoSimulado d = new DiscoSimulado("Disco", 200, 50, 20);
        sim.setDisco(d);
        d.asignarBloquesArchivo("/docs/a.txt", 3, "#2ecc71");
        d.asignarBloquesSwap("SWAP-A", 2);
        d.encolarSolicitud(new SolicitudES(pcb("P1"), TipoOperacionES.LEER, "/docs/a.txt", 120, 1, 0));
        d.encolarSolicitud(new SolicitudES(pcb("P2"), TipoOperacionES.LEER, "/docs/b.txt", 10, 1, 0));
        SwingUtilities.invokeAndWait(() -> { v[0] = new VentanaDisco(null, sim); v[0].setVisible(true); });
        verificar(v[0].getTextoCabezal().contains("bloque 50"), "muestra la posición del cabezal (50)");
        verificar(v[0].getTextoCola().contains("P1") && v[0].getTextoCola().contains("P2"), "la cola lista las dos solicitudes");

        ListaEnlazada<BloqueDisco> bl = d.copiarBloques();
        BloqueDisco archivo = null, libre = null, swapOcup = null, swapLibre = null;
        for (int i = 0; i < bl.getTamano(); i++) {
            BloqueDisco b = bl.obtener(i);
            if (b.isAreaSwap()) { if (b.isOcupadoPorSwap()) swapOcup = b; else swapLibre = b; }
            else if (b.isOcupado()) archivo = b; else libre = b;
        }
        verificar(PanelMapaDisco.colorDe(archivo).equals(new java.awt.Color(0x2ecc71)), "bloque de archivo usa el color de su archivo");
        verificar(PanelMapaDisco.colorDe(libre).equals(PanelMapaDisco.COLOR_LIBRE), "bloque libre en gris");
        verificar(PanelMapaDisco.colorDe(swapOcup).equals(PanelMapaDisco.COLOR_SWAP_OCUPADO), "swap ocupado en naranja");
        verificar(PanelMapaDisco.colorDe(swapLibre).equals(PanelMapaDisco.COLOR_SWAP_LIBRE), "swap libre en naranja claro");

        BufferedImage img = new BufferedImage(600, 320, BufferedImage.TYPE_INT_RGB);
        boolean[] pinto = {true};
        SwingUtilities.invokeAndWait(() -> {
            try { v[0].getMapa().setSize(600, 320); v[0].getMapa().paint(img.getGraphics()); }
            catch (RuntimeException ex) { pinto[0] = false; ex.printStackTrace(); }
        });
        verificar(pinto[0], "el mapa se dibuja sin errores");
        int cx = (50 % PanelMapaDisco.COLUMNAS) * 28 + 4, cy = (50 / PanelMapaDisco.COLUMNAS) * 28 + 3;
        verificar(img.getRGB(cx, cy) == PanelMapaDisco.COLOR_CABEZAL.getRGB(), "el cabezal (bloque 50) se pinta con borde rojo");

        // Cambio de política y velocidad desde la ventana
        SwingUtilities.invokeAndWait(() -> {
            try {
                java.lang.reflect.Field f = VentanaDisco.class.getDeclaredField("comboPolitica");
                f.setAccessible(true);
                ((javax.swing.JComboBox<?>) f.get(v[0])).setSelectedItem(PoliticaDisco.SSTF);
                java.lang.reflect.Field g = VentanaDisco.class.getDeclaredField("spinVelocidad");
                g.setAccessible(true);
                ((javax.swing.JSpinner) g.get(v[0])).setValue(5);
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        });
        verificar(d.getPoliticaActual() == PoliticaDisco.SSTF, "el combo cambia la política del disco en caliente");
        verificar(d.getVelocidadCabezal() == 5, "el spinner cambia la velocidad del cabezal");

        // El disco avanza y la vista sigue
        for (long c = 1; c <= 30; c++) d.tick(c);
        SwingUtilities.invokeAndWait(() -> v[0].refrescar());
        verificar(!v[0].getTextoCabezal().contains("bloque 50"), "tras avanzar el reloj el cabezal se movió");
        verificar(d.getTotalSolicitudesAtendidas() >= 1, "el disco atendió solicitudes");

        // Un cambio hecho por fuera se refleja en el combo
        d.setPolitica(PoliticaDisco.C_SCAN);
        SwingUtilities.invokeAndWait(() -> v[0].refrescar());
        boolean[] igual = new boolean[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                java.lang.reflect.Field f = VentanaDisco.class.getDeclaredField("comboPolitica");
                f.setAccessible(true);
                igual[0] = ((javax.swing.JComboBox<?>) f.get(v[0])).getSelectedItem() == PoliticaDisco.C_SCAN;
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        });
        verificar(igual[0] && d.getPoliticaActual() == PoliticaDisco.C_SCAN, "un cambio externo de política se refleja sin reescribirse");
        SwingUtilities.invokeAndWait(() -> v[0].dispose());

        System.out.println(fallos == 0 ? "TODAS LAS PRUEBAS PASARON (" + ok + ")" : "HAY " + fallos + " FALLOS");
        System.exit(fallos == 0 ? 0 : 1);
    }
}
