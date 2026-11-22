package integracion;

import archivos.NodoArchivo;
import archivos.SistemaArchivos;
import config.ConfigProcesosDisco;
import config.ConfigSistemaArchivos;
import config.GestorPersistencia;
import disco.DiscoSimulado;
import disco.EscuchaFinES;
import disco.SolicitudES;
import enums.EstadoProceso;
import enums.ModoUsuario;
import enums.PoliticaDisco;
import enums.TipoOperacionES;
import estructuras.ListaEnlazada;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import modelo.PCB;
import simulacion.MetricasSimulacion;
import simulacion.Simulador;

/**
 * Pruebas integrales del Proyecto 2 (issue #83).
 *
 * <ol>
 *   <li>Políticas de disco con el Anexo y los casos P1 a P4 del enunciado (orden y desplazamiento).</li>
 *   <li>Carga de los 2 JSON del enunciado y ejecución completa con las 4 políticas.</li>
 *   <li>CRUD de punta a punta: proceso de E/S, modo Kernel, disco, sistema de archivos y permisos.</li>
 * </ol>
 *
 * <p>Se ejecuta desde la raíz del proyecto (usa {@code ejemplos/proyecto2/*.json}):
 * {@code java -cp "target/classes;target/test-classes;GSON" integracion.IntegracionProyecto2Prueba}</p>
 */
public class IntegracionProyecto2Prueba {

    private static final String FS_JSON = "ejemplos/proyecto2/sistema_archivos.json";
    private static final String PROC_JSON = "ejemplos/proyecto2/procesos_config.json";

    private static int ok = 0, fallos = 0;

    private static void verificar(boolean c, String m) {
        if (c) { ok++; System.out.println("  OK    " + m); }
        else { fallos++; System.out.println("  FALLA " + m); }
    }

    // ------------------------------------------------------------------ disco

    private static final class Resultado {
        String orden = "";
        long desplazamiento;
    }

    /** Encola todas las solicitudes antes del primer ciclo y simula hasta que el disco queda libre. */
    private static Resultado correrDisco(PoliticaDisco politica, int swap, int[] bloques) {
        DiscoSimulado d = new DiscoSimulado("Disco", 200, 50, swap);
        d.setPolitica(politica);
        Resultado r = new Resultado();
        StringBuilder orden = new StringBuilder();
        d.setEscuchaFinES((SolicitudES s, boolean exito, String detalle) -> {
            if (orden.length() > 0) orden.append(',');
            orden.append(s.getBloqueDestino());
        });
        for (int b : bloques) {
            d.encolarSolicitud(new SolicitudES(null, TipoOperacionES.LEER, "/x", b, 1, 0));
        }
        long ciclo = 0;
        while (d.hayTrabajoPendiente() && ciclo < 5000) {
            d.tick(++ciclo);
        }
        r.orden = orden.toString();
        r.desplazamiento = d.getDesplazamientoTotal();
        return r;
    }

    private static void caso(String nombre, PoliticaDisco p, int[] bloques, String ordenEsperado, long despEsperado) {
        Resultado r = correrDisco(p, 0, bloques);
        verificar(r.orden.equals(ordenEsperado), nombre + ": orden " + r.orden);
        verificar(r.desplazamiento == despEsperado,
                nombre + ": desplazamiento " + r.desplazamiento + " (esperado " + despEsperado + ")");
    }

    // ------------------------------------------------------------------ JSON

    private static String leer(String ruta) throws Exception {
        return new String(Files.readAllBytes(new File(ruta).toPath()), StandardCharsets.UTF_8);
    }

    private static Simulador cargar(String politica) throws Exception {
        String procesos = leer(PROC_JSON).replace("\"initialDiskPolicy\": \"FIFO\"",
                "\"initialDiskPolicy\": \"" + politica + "\"");
        ConfigSistemaArchivos fs = GestorPersistencia.cargarSistemaArchivosJSON(new File(FS_JSON));
        ConfigProcesosDisco pr = GestorPersistencia.cargarProcesosDesdeTexto(procesos);
        return GestorPersistencia.construirSimuladorDesdeDosJSON(fs, pr);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== 1. Políticas de disco: Anexo del enunciado (cabezal en 50, 200 bloques)");
        int[] anexo = {82, 17, 43, 140, 24, 16, 190};
        caso("Anexo FIFO", PoliticaDisco.FIFO, anexo, "82,17,43,140,24,16,190", 518);
        caso("Anexo SSTF", PoliticaDisco.SSTF, anexo, "43,24,17,16,82,140,190", 208);
        caso("Anexo SCAN", PoliticaDisco.SCAN, anexo, "82,140,190,43,24,17,16", 332);
        caso("Anexo C-SCAN", PoliticaDisco.C_SCAN, anexo, "82,140,190,16,17,24,43", 192);

        System.out.println("== 1b. Casos P1 a P4 del enunciado (8 solicitudes)");
        int[] p = {95, 180, 34, 119, 11, 123, 62, 64};
        caso("P1 FIFO", PoliticaDisco.FIFO, p, "95,180,34,119,11,123,62,64", 644);
        caso("P2 SSTF", PoliticaDisco.SSTF, p, "62,64,34,11,95,119,123,180", 236);
        caso("P3 SCAN", PoliticaDisco.SCAN, p, "62,64,95,119,123,180,34,11", 337);
        caso("P4 C-SCAN", PoliticaDisco.C_SCAN, p, "62,64,95,119,123,180,11,34", 183);

        if (!new File(FS_JSON).exists() || !new File(PROC_JSON).exists()) {
            System.out.println("No se encontraron los JSON en ejemplos/proyecto2: ejecute desde la raíz del proyecto.");
            fallos++;
        } else {
            System.out.println("== 2. Los 2 JSON del enunciado, simulación completa con cada política");
            String[] politicas = {"FIFO", "SSTF", "SCAN", "C-SCAN"};
            long[] esperado = {644, 299, 337, 213};
            long[] desplazamientos = new long[4];
            for (int i = 0; i < 4; i++) {
                Simulador sim = cargar(politicas[i]);
                SistemaArchivos sa = (SistemaArchivos) sim.getServicioArchivos();
                verificar(sa.getCantidadArchivos() == 8, politicas[i] + ": el JSON 1 carga los 8 archivos");
                verificar(sa.buscarPorRuta("/usuarios/video.mp4") != null
                        && sa.buscarPorRuta("/usuarios/video.mp4").getPrimerBloque() == 180,
                        politicas[i] + ": video.mp4 queda en el bloque 180 como indica el JSON");
                verificar(sim.getDisco().getPoliticaActual().getCodigoJson().equalsIgnoreCase(politicas[i]),
                        politicas[i] + ": el JSON 2 fija la política inicial del disco");
                sim.ejecutar();
                MetricasSimulacion m = MetricasSimulacion.calcular(sim);
                desplazamientos[i] = sim.getDisco().getDesplazamientoTotal();
                verificar(m.getTerminados() == 8 && m.getTerminadosPorDeadline() == 0,
                        politicas[i] + ": los 8 procesos terminan sin vencer deadline");
                verificar(sim.getDisco().getTotalSolicitudesAtendidas() == 8,
                        politicas[i] + ": el disco atiende las 8 solicitudes");
                verificar(desplazamientos[i] == esperado[i],
                        politicas[i] + ": desplazamiento total " + desplazamientos[i] + " (esperado " + esperado[i] + ")");
                verificar(sim.getDisco().getSolicitudesAtendidas(sim.getDisco().getPoliticaActual()) == 8,
                        politicas[i] + ": las 8 se cuentan en la política activa");
            }
            verificar(desplazamientos[3] < desplazamientos[1] && desplazamientos[1] < desplazamientos[2]
                    && desplazamientos[2] < desplazamientos[0],
                    "para las mismas solicitudes: C-SCAN < SSTF < SCAN < FIFO");

            System.out.println("== 3. CRUD de punta a punta con permisos");
            Simulador sim = cargar("FIFO");
            SistemaArchivos sa = (SistemaArchivos) sim.getServicioArchivos();
            PCB crear = sim.solicitarOperacionES(1, "ES-crear", TipoOperacionES.CREAR, "/usuarios/nuevo.bin", "", 3,
                    ModoUsuario.ADMINISTRADOR, "admin");
            PCB prohibido = sim.solicitarOperacionES(1, "ES-prohibido", TipoOperacionES.ELIMINAR, "/sistema/core.dll", "", 0,
                    ModoUsuario.USUARIO, "user2");
            PCB renombrar = sim.solicitarOperacionES(1, "ES-renombrar", TipoOperacionES.ACTUALIZAR, "/sistema/script.py",
                    "script2.py", 0, ModoUsuario.ADMINISTRADOR, "admin");
            sim.ejecutar();

            NodoArchivo nuevo = sa.buscarPorRuta("/usuarios/nuevo.bin");
            verificar(crear.getEstado() == EstadoProceso.TERMINADO, "el proceso CREATE termina");
            verificar(nuevo != null && nuevo.getTamanoBloques() == 3, "CREATE: el archivo existe con 3 bloques");
            verificar(sa.buscarPorRuta("/sistema/core.dll") != null,
                    "un USUARIO no puede borrar un archivo ajeno: sigue existiendo");
            verificar(prohibido.getRechazoES() != null && !prohibido.getRechazoES().isEmpty(),
                    "la operación prohibida queda rechazada con un aviso");
            verificar(prohibido.getEstado() == EstadoProceso.TERMINADO,
                    "el rechazo no detiene la simulación: el proceso termina");
            verificar(sa.buscarPorRuta("/sistema/script2.py") != null && sa.buscarPorRuta("/sistema/script.py") == null,
                    "UPDATE: el archivo se renombró");
            verificar(sa.buscarPorRuta("/usuarios/img1.png") == null,
                    "el proceso del JSON (DELETE) eliminó /usuarios/img1.png");
            verificar(renombrar.getEstado() == EstadoProceso.TERMINADO, "el proceso UPDATE termina");

            long ocupados = 0;
            ListaEnlazada<NodoArchivo> todos = sa.listarTodosLosArchivos();
            for (int i = 0; i < todos.getTamano(); i++) {
                ocupados += todos.obtener(i).getTamanoBloques();
            }
            DiscoSimulado d = sim.getDisco();
            verificar(d.getTotalBloquesDatos() - d.getBloquesLibresDatos() == ocupados,
                    "bloques ocupados en el disco = suma de los bloques de los archivos (" + ocupados + ")");
        }

        System.out.println(fallos == 0 ? "TODAS LAS PRUEBAS PASARON (" + ok + ")" : "HAY " + fallos + " FALLOS");
        System.exit(fallos == 0 ? 0 : 1);
    }
}
