package config;

import disco.DiscoSimulado;
import estructuras.ListaEnlazada;

/** Valida el rango de tamaño del disco (100 a 500 bloques) en ambas configuraciones. */
public class ValidacionDiscoPrueba {

    private static int fallos = 0;

    private static void asegurar(boolean condicion, String descripcion) {
        if (condicion) {
            System.out.println("    [OK] " + descripcion);
        } else {
            fallos++;
            System.err.println("    [FALLO] " + descripcion);
        }
    }

    private static String json(int bloques) {
        return "{ \"diskConfig\": { \"totalBlocks\": " + bloques + ", \"initialHead\": 50 },"
                + " \"fileSystem\": [ { \"path\": \"/a.bin\", \"type\": \"FILE\", \"startBlock\": 0,"
                + " \"blocks\": 5, \"owner\": \"admin\", \"permissions\": \"public\" } ] }";
    }

    private static boolean tieneErrorDeBloques(ListaEnlazada<String> errs) {
        for (int i = 0; i < errs.getTamano(); i++) {
            if (errs.obtener(i).contains("entre")) return true;
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Validación del tamaño del disco (100-500) ===");
        asegurar(DiscoSimulado.MIN_BLOQUES_DISCO == 100 && DiscoSimulado.MAX_BLOQUES_DISCO == 500, "Constantes 100 y 500");

        int[] invalidos = {0, 99, 501, 1000};
        int[] validos = {100, 200, 500};
        for (int n : invalidos) {
            boolean rechazado = false;
            try {
                GestorPersistencia.cargarSistemaArchivosDesdeTexto(json(n));
            } catch (IllegalArgumentException ex) {
                rechazado = ex.getMessage().contains("entre");
            }
            asegurar(rechazado, "JSON 1: " + n + " bloques se rechaza al cargar");
        }
        for (int n : validos) {
            ConfigSistemaArchivos c = GestorPersistencia.cargarSistemaArchivosDesdeTexto(json(n));
            asegurar(!tieneErrorDeBloques(c.validar()), "JSON 1: " + n + " bloques se acepta");
        }
        for (int n : invalidos) {
            ConfiguracionSimulacion c = ConfiguracionSimulacion.crearPorDefecto();
            c.setTotalBloquesDisco(n);
            asegurar(tieneErrorDeBloques(c.validar()), "Config manual: " + n + " bloques se rechaza");
        }
        for (int n : validos) {
            ConfiguracionSimulacion c = ConfiguracionSimulacion.crearPorDefecto();
            c.setTotalBloquesDisco(n);
            asegurar(!tieneErrorDeBloques(c.validar()), "Config manual: " + n + " bloques se acepta");
        }
        System.out.println(fallos == 0 ? "RESULTADO: TODAS LAS PRUEBAS PASARON" : "RESULTADO: " + fallos + " FALLOS");
        if (fallos > 0) System.exit(1);
    }
}
