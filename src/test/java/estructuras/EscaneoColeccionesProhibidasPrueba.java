package estructuras;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Prueba estática automatizada para verificar la ausencia total de colecciones
 * de {@code java.util} en el código fuente de ÁvilaOS.
 *
 * <p>Escanea todos los archivos {@code .java} del proyecto y confirma que
 * ninguna clase haga uso de colecciones nativas de Java (List, ArrayList, Map,
 * HashMap, Set, HashSet, Queue, Deque, Vector, Stack, Collections, etc.),
 * garantizando el cumplimiento estricto de las restricciones del enunciado.</p>
 */
public class EscaneoColeccionesProhibidasPrueba {

    private static int totalAserciones = 0;
    private static int asercionesExitosas = 0;
    private static int asercionesFallidas = 0;

    private static void asegurar(boolean condicion, String descripcion) {
        totalAserciones++;
        if (condicion) {
            asercionesExitosas++;
            System.out.println("    [OK] " + descripcion);
        } else {
            asercionesFallidas++;
            System.err.println("    [FALLO] " + descripcion);
        }
    }

    private static final String[] COLECCIONES_PROHIBIDAS = {
        "java.util.List",
        "java.util.ArrayList",
        "java.util.LinkedList",
        "java.util.Map",
        "java.util.HashMap",
        "java.util.TreeMap",
        "java.util.LinkedHashMap",
        "java.util.Set",
        "java.util.HashSet",
        "java.util.TreeSet",
        "java.util.Queue",
        "java.util.Deque",
        "java.util.ArrayDeque",
        "java.util.PriorityQueue",
        "java.util.Vector",
        "java.util.Stack",
        "java.util.Collection",
        "java.util.Collections",
        "java.util.Dictionary",
        "java.util.Hashtable",
        "java.util.*"
    };

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("  STATIC SCANNER - ESCANEO DE COLECCIONES PROHIBIDAS (ÁVILA OS)");
        System.out.println("===============================================================================");

        File dirMain = new File("src/main/java");
        File dirTest = new File("src/test/java");
        asegurar(dirMain.exists() && dirMain.isDirectory(), "Directorio src/main/java existe");
        asegurar(dirTest.exists() && dirTest.isDirectory(), "Directorio src/test/java existe");

        ListaEnlazada<File> archivos = new ListaEnlazada<>();
        recolectarArchivosJava(dirMain, archivos);
        recolectarArchivosJava(dirTest, archivos);

        int totalArchivos = archivos.getTamano();
        asegurar(totalArchivos > 0, "Se encontraron " + totalArchivos + " archivos Java en el proyecto");

        int totalLineas = 0;
        int totalViolaciones = 0;
        ListaEnlazada<String> detalleViolaciones = new ListaEnlazada<>();

        Iterador<File> it = archivos.iterador();
        while (it.haySiguiente()) {
            File f = it.siguiente();
            if (f.getName().equals("EscaneoColeccionesProhibidasPrueba.java")) {
                continue;
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                String linea;
                int nroLinea = 0;
                while ((linea = reader.readLine()) != null) {
                    nroLinea++;
                    totalLineas++;
                    String trim = linea.trim();
                    // Ignorar comentarios
                    if (trim.startsWith("//") || trim.startsWith("*") || trim.startsWith("/*")) {
                        continue;
                    }
                    for (String prohibida : COLECCIONES_PROHIBIDAS) {
                        if (trim.contains("import " + prohibida)
                                || trim.contains(prohibida + "<")
                                || trim.contains("new " + prohibida)) {
                            totalViolaciones++;
                            detalleViolaciones.agregar(f.getPath() + ":" + nroLinea + " -> " + trim);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error leyendo archivo: " + f.getPath() + " - " + e.getMessage());
            }
        }

        System.out.println("    -> Total archivos escaneados : " + totalArchivos);
        System.out.println("    -> Total líneas analizadas   : " + totalLineas);
        System.out.println("    -> Total violaciones detectadas: " + totalViolaciones);

        if (totalViolaciones > 0) {
            Iterador<String> itV = detalleViolaciones.iterador();
            while (itV.haySiguiente()) {
                System.err.println("       VIOLACIÓN: " + itV.siguiente());
            }
        }

        asegurar(totalViolaciones == 0, "Cero (0) usos de colecciones java.util detectados en el código fuente");
        asegurar(totalArchivos >= 50, "Cobertura exhaustiva (54 archivos del clúster)");

        System.out.println("\n===============================================================================");
        System.out.println("                       RESUMEN DEL ESCANEO");
        System.out.println("===============================================================================");
        System.out.println("  Total de aserciones evaluadas : " + totalAserciones);
        System.out.println("  Aserciones exitosas          : " + asercionesExitosas);
        System.out.println("  Aserciones fallidas          : " + asercionesFallidas);
        System.out.println("  ESTADO FINAL                 : " + (asercionesFallidas == 0 ? "100% LIMPIO (0 colecciones java.util)" : "FALLÓ"));
        System.out.println("===============================================================================");

        if (asercionesFallidas > 0) {
            System.exit(1);
        }
    }

    private static void recolectarArchivosJava(File dir, ListaEnlazada<File> lista) {
        File[] hijos = dir.listFiles();
        if (hijos == null) {
            return;
        }
        for (File h : hijos) {
            if (h.isDirectory()) {
                recolectarArchivosJava(h, lista);
            } else if (h.isFile() && h.getName().endsWith(".java")) {
                lista.agregar(h);
            }
        }
    }
}
