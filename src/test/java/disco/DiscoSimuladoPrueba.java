package disco;

import enums.PoliticaDisco;
import enums.TipoOperacionES;
import estructuras.ListaEnlazada;
import hardware.Reloj;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pruebas unitarias de {@link BloqueDisco} y {@link DiscoSimulado}:
 * asignación encadenada, área de intercambio (Swap), integración con {@link Reloj}
 * y verificación del caso FIFO del Anexo.
 */
public class DiscoSimuladoPrueba {

    private static int fallos = 0;

    private static void comprobar(boolean condicion, String mensaje) {
        if (condicion) {
            System.out.println("  OK    " + mensaje);
        } else {
            fallos++;
            System.out.println("  FALLO " + mensaje);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Pruebas de DiscoSimulado y BloqueDisco ===");

        probarAsignacionEncadenada();
        probarAreaSwapConYsinArchivosPrevios();
        probarRelojYCabezalFIFOAnexo();

        if (fallos == 0) {
            System.out.println("=== TODAS LAS PRUEBAS DE DISCO PASARON ===");
        } else {
            System.out.println("=== FALLOS DETECTADOS: " + fallos + " ===");
            System.exit(1);
        }
    }

    private static void probarAsignacionEncadenada() {
        DiscoSimulado disco = new DiscoSimulado("SD-Test", 50, 10, 10);
        comprobar(disco.getTotalBloquesDatos() == 40, "Con swap=10 sobre disco de 50 libre, quedan 40 bloques de datos");
        comprobar(disco.getTamanoSwap() == 10, "Área Swap configurada en 10 bloques");
        comprobar(disco.getBloquesLibresDatos() == 40, "Todos los 40 bloques de datos inician libres");

        int primeroA = disco.asignarBloquesArchivo("/sistema/a.bin", 5, "#FF0000");
        comprobar(primeroA == 0, "Primer archivo inicia en bloque 0");
        comprobar(disco.getBloquesLibresDatos() == 35, "Bloques libres bajan a 35 tras asignar 5");

        ListaEnlazada<Integer> cadenaA = disco.obtenerCadenaBloques(primeroA);
        comprobar(cadenaA.getTamano() == 5, "Cadena encadenada tiene exactamente 5 bloques");

        // Fragmentación intencional: liberar solo bloque central y asignar archivo nuevo que salte huecos
        int primeroB = disco.asignarBloquesArchivo("/sistema/b.bin", 3, "#00FF00");
        comprobar(primeroB == 5, "Segundo archivo inicia en bloque 5");

        disco.renombrarPropietarioCadena(primeroB, "/sistema/b_nuevo.bin");
        ListaEnlazada<BloqueDisco> snapshot = disco.copiarBloques();
        comprobar("/sistema/b_nuevo.bin".equals(snapshot.obtener(5).getPropietario()),
                "Renombrar actualiza el propietario en los bloques encadenados");

        int liberados = disco.liberarCadenaBloques(primeroA);
        comprobar(liberados == 5, "Al borrar /sistema/a.bin se liberan sus 5 bloques encadenados");
        comprobar(disco.getBloquesLibresDatos() == 37, "Quedan 37 bloques libres (40 - 3 de b_nuevo)");

        int falloEspacio = disco.asignarBloquesArchivo("/gigante.iso", 100, "#0000FF");
        comprobar(falloEspacio == -1, "Rechaza asignación si no hay bloques suficientes");
    }

    private static void probarAreaSwapConYsinArchivosPrevios() {
        // Caso JSON 1 + JSON 2: 200 bloques completamente llenos por archivos y luego swapSize=100
        DiscoSimulado disco = new DiscoSimulado("SD-JSON", 200, 50, 0);
        boolean cargado = disco.ocuparBloquesContiguosArchivo("/lleno.bin", 0, 200, "#123456");
        comprobar(cargado, "Se cargan los 200 bloques completos de archivos (0..199)");
        comprobar(disco.getBloquesLibresDatos() == 0, "0 bloques libres en datos");

        disco.configurarAreaSwap(100);
        comprobar(disco.getTotalBloquesDatos() == 200, "Conserva los 200 bloques de datos intactos");
        comprobar(disco.getTotalBloques() == 300, "Anexa 100 bloques para Swap sin pisar archivos (total 300)");
        comprobar(disco.getBloquesLibresSwap() == 100, "100 bloques Swap disponibles");

        int swapP1 = disco.asignarBloquesSwap("P1", 16);
        comprobar(swapP1 == 200, "Proceso suspendido se escribe en área Swap desde bloque 200");
        comprobar(disco.getBloquesLibresSwap() == 84, "Quedan 84 bloques libres en Swap");

        int liberados = disco.liberarBloquesSwap(swapP1);
        comprobar(liberados == 16, "Al reactivar se liberan los 16 bloques Swap");
        comprobar(disco.getBloquesLibresSwap() == 100, "Área Swap recupera los 100 bloques libres");
    }

    private static void probarRelojYCabezalFIFOAnexo() {
        // Anexo del enunciado: 200 bloques, cabezal en 50, cola: 82, 17, 43, 140, 24, 16, 190
        // Desplazamiento esperado FIFO = 518 bloques
        DiscoSimulado disco = new DiscoSimulado("SD-Anexo", 200, 50, 0);
        disco.setVelocidadCabezal(20);
        AtomicInteger completadas = new AtomicInteger(0);
        disco.setEscuchaFinES((sol, exito, det) -> completadas.incrementAndGet());

        int[] bloques = {82, 17, 43, 140, 24, 16, 190};
        for (int i = 0; i < bloques.length; i++) {
            disco.encolarSolicitud(new SolicitudES(null, TipoOperacionES.LEER, "/f" + i, bloques[i], 1, 0));
        }

        Reloj reloj = new Reloj();
        reloj.registrar(disco);
        try {
            int guard = 0;
            while (disco.hayTrabajoPendiente() && guard < 1000) {
                reloj.avanzarCiclo();
                guard++;
            }
        } finally {
            reloj.detener();
        }

        comprobar(completadas.get() == 7, "Se atendieron las 7 solicitudes del Anexo mediante Reloj y EscuchaFinES");
        comprobar(disco.getDesplazamientoTotal() == 518,
                "Desplazamiento total FIFO coincide exactamente con el Anexo (518 bloques, obtenido: "
                        + disco.getDesplazamientoTotal() + ")");
        comprobar(disco.getSolicitudesAtendidas(PoliticaDisco.FIFO) == 7,
                "Contador de solicitudes atendidas por FIFO = 7");
        comprobar(disco.getPosicionCabezal() == 190, "El cabezal termina en el último bloque atendido (190)");
    }
}
