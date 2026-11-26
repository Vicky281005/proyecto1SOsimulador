package config;

import archivos.SistemaArchivos;
import disco.DiscoSimulado;
import enums.PoliticaDisco;
import enums.PoliticaPlanificacion;
import enums.TipoOperacionES;
import enums.TipoProceso;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import gui.VentanaConfiguracion;
import java.io.File;
import java.io.IOException;
import nucleo.Computador;
import simulacion.Simulador;

/**
 * Suite de pruebas para la persistencia dual de JSONs del Proyecto 2 y configuración general
 * de ÁvilaOS (sin buffers ni colecciones de {@code java.util}).
 */
public class PersistenciaPrueba {

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

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("     SUITE DE PRUEBAS DE PERSISTENCIA DUAL PROYECTO 2 (ÁVILA OS)");
        System.out.println("===============================================================================");

        try {
            probarDosJsonOficialesYSimulacionCompleta();
            probarGuardarYPreservarDiscoEntreEjecuciones();
            probarValidacionesEstrictas();
            probarConfigGeneralJsonYCSV();
            probarEdicionTablaComputador();
        } catch (Exception e) {
            System.err.println("Error no controlado durante las pruebas: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("\n===============================================================================");
        System.out.println("  Total: " + totalAserciones + " | Exitosas: " + asercionesExitosas
                + " | Fallidas: " + asercionesFallidas);
        if (asercionesFallidas == 0) {
            System.out.println("  ESTADO FINAL : 100% PASADO");
        } else {
            System.exit(1);
        }
        System.out.println("===============================================================================");
    }

    private static String jsonSistemaArchivosOficial() {
        return """
                {
                  "diskConfig": { "totalBlocks": 200, "initialHead": 50 },
                  "fileSystem": [
                    { "path": "/mbr.bin", "type": "FILE", "startBlock": 0, "blocks": 11, "owner": "admin", "permissions": "public" },
                    { "path": "/sistema", "type": "DIRECTORY", "owner": "admin", "permissions": "public" },
                    { "path": "/usuarios", "type": "DIRECTORY", "owner": "admin", "permissions": "public" },
                    { "path": "/sistema/boot.bin", "type": "FILE", "startBlock": 11, "blocks": 23, "owner": "admin", "permissions": "public" },
                    { "path": "/usuarios/notas.txt", "type": "FILE", "startBlock": 34, "blocks": 28, "owner": "user1", "permissions": "private" },
                    { "path": "/sistema/script.py", "type": "FILE", "startBlock": 62, "blocks": 2, "owner": "admin", "permissions": "public" },
                    { "path": "/sistema/core.dll", "type": "FILE", "startBlock": 64, "blocks": 31, "owner": "admin", "permissions": "public" },
                    { "path": "/sistema/config.sys", "type": "FILE", "startBlock": 95, "blocks": 24, "owner": "admin", "permissions": "public" },
                    { "path": "/usuarios/img1.png", "type": "FILE", "startBlock": 119, "blocks": 4, "owner": "user1", "permissions": "private" },
                    { "path": "/usuarios/data.csv", "type": "FILE", "startBlock": 123, "blocks": 57, "owner": "user2", "permissions": "public" },
                    { "path": "/usuarios/video.mp4", "type": "FILE", "startBlock": 180, "blocks": 20, "owner": "user1", "permissions": "public" }
                  ]
                }
                """;
    }

    private static String jsonProcesosOficial() {
        return """
                {
                  "globalConfig": { "cycleDurationMs": 300, "swapSizeBlocks": 100, "initialDiskPolicy": "FIFO" },
                  "computer": { "ramCapacity": 1024, "policy": "FCFS", "quantum": 0 },
                  "processes": [
                    { "name": "Proc_95", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/sistema/config.sys", "operation": "READ" },
                    { "name": "Proc_180", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/usuarios/video.mp4", "operation": "READ" },
                    { "name": "Proc_34", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/usuarios/notas.txt", "operation": "UPDATE" },
                    { "name": "Proc_119", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/usuarios/img1.png", "operation": "DELETE" },
                    { "name": "Proc_11", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/sistema/boot.bin", "operation": "READ" },
                    { "name": "Proc_123", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/usuarios/data.csv", "operation": "UPDATE" },
                    { "name": "Proc_62", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/sistema/script.py", "operation": "READ" },
                    { "name": "Proc_64", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/sistema/core.dll", "operation": "READ" }
                  ]
                }
                """;
    }

    public static void probarDosJsonOficialesYSimulacionCompleta() throws Exception {
        System.out.println("\n>>> PRUEBA 1: Carga de los 2 JSON oficiales y ejecución de los 8 procesos");
        ConfigSistemaArchivos fsCfg = GestorPersistencia.cargarSistemaArchivosDesdeTexto(jsonSistemaArchivosOficial());
        ConfigProcesosDisco procCfg = GestorPersistencia.cargarProcesosDesdeTexto(jsonProcesosOficial());

        asegurar(fsCfg.getDiskConfig().getTotalBlocks() == 200, "JSON 1: totalBlocks = 200");
        asegurar(fsCfg.getFileSystem().getTamano() == 11, "JSON 1: 11 entradas cargadas");
        asegurar(procCfg.getProcesses().getTamano() == 8, "JSON 2: 8 procesos cargados");
        asegurar(procCfg.getGlobalConfig().getSwapSizeBlocks() == 100, "JSON 2: swapSizeBlocks = 100");

        Simulador sim = GestorPersistencia.construirSimuladorDesdeDosJSON(fsCfg, procCfg);
        asegurar(sim.tieneAmbosJsonCargados(), "Simulador marcado con ambos JSON cargados");
        asegurar(sim.getCantidadComputadores() == 1, "Proyecto 2: exactamente 1 computador registrado");

        DiscoSimulado disco = sim.getDisco();
        SistemaArchivos sa = (SistemaArchivos) sim.getServicioArchivos();
        asegurar(disco != null && sa != null, "Disco y SistemaArchivos conectados");
        asegurar(disco.getBloquesLibresDatos() == 0, "Los 200 bloques iniciales están ocupados");
        asegurar(disco.getBloquesLibresSwap() == 100, "100 bloques Swap disponibles");

        sim.ejecutar();
        asegurar(sim.isTerminada(), "Simulación con los 8 procesos oficiales terminó correctamente");
        asegurar(sa.buscarPorRuta("/usuarios/img1.png") == null, "Proc_119 eliminó /usuarios/img1.png");
        asegurar(disco.getBloquesLibresDatos() == 4, "Quedaron 4 bloques libres tras eliminar img1.png");
    }

    public static void probarGuardarYPreservarDiscoEntreEjecuciones() throws Exception {
        System.out.println("\n>>> PRUEBA 2: Guardado del Sistema de Archivos y reinicio preservando disco");
        ConfigSistemaArchivos fsCfg = GestorPersistencia.cargarSistemaArchivosDesdeTexto(jsonSistemaArchivosOficial());
        ConfigProcesosDisco procCfg1 = GestorPersistencia.cargarProcesosDesdeTexto(jsonProcesosOficial());
        Simulador sim1 = GestorPersistencia.construirSimuladorDesdeDosJSON(fsCfg, procCfg1);
        sim1.ejecutar();

        SistemaArchivos saModificado = (SistemaArchivos) sim1.getServicioArchivos();
        File tmpFs = File.createTempFile("avilaos_fs_", ".json");
        tmpFs.deleteOnExit();
        GestorPersistencia.guardarSistemaArchivosJSON(saModificado, tmpFs);

        ConfigSistemaArchivos recargado = GestorPersistencia.cargarSistemaArchivosJSON(tmpFs);
        asegurar(recargado.getFileSystem().getTamano() == 10,
                "JSON guardado refleja las 10 entradas restantes (sin img1.png)");

        // Segunda corrida con un CREATE usando fileSizeBlocks sobre los 4 bloques liberados
        String json2Nuevo = """
                {
                  "globalConfig": { "cycleDurationMs": 200, "swapSizeBlocks": 100, "initialDiskPolicy": "SSTF" },
                  "computer": { "ramCapacity": 1024, "policy": "FCFS", "quantum": 0 },
                  "processes": [
                    { "name": "Proc_Crear", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/nuevo.bin", "operation": "CREATE", "fileSizeBlocks": 3 }
                  ]
                }
                """;
        ConfigProcesosDisco procCfg2 = GestorPersistencia.cargarProcesosDesdeTexto(json2Nuevo);
        Simulador sim2 = GestorPersistencia.construirSimuladorPreservandoDisco(saModificado, procCfg2);
        sim2.ejecutar();

        asegurar(saModificado.buscarPorRuta("/nuevo.bin") != null,
                "Archivo /nuevo.bin creado sobre el disco preservado");
        asegurar(sim2.getDisco().getBloquesLibresDatos() == 1,
                "Queda 1 bloque libre tras usar 3 de los 4 bloques liberados previamente");

        // Regresión Bugs 1 y 2: archivo fragmentado + cabezal en zona Swap -> guardar y recargar sin error
        String json2Del = """
                {
                  "globalConfig": { "cycleDurationMs": 50, "swapSizeBlocks": 100, "initialDiskPolicy": "FIFO" },
                  "computer": { "ramCapacity": 1024, "policy": "FCFS", "quantum": 0 },
                  "processes": [
                    { "name": "DelMbr", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/mbr.bin", "operation": "DELETE" },
                    { "name": "DelScript", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/sistema/script.py", "operation": "DELETE" }
                  ]
                }
                """;
        Simulador simDel = GestorPersistencia.construirSimuladorPreservandoDisco(
                saModificado, GestorPersistencia.cargarProcesosDesdeTexto(json2Del));
        simDel.ejecutar();

        String json2Frag = """
                {
                  "globalConfig": { "cycleDurationMs": 50, "swapSizeBlocks": 100, "initialDiskPolicy": "FIFO" },
                  "computer": { "ramCapacity": 1024, "policy": "FCFS", "quantum": 0 },
                  "processes": [
                    { "name": "CrearFrag", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/frag.bin", "operation": "CREATE", "fileSizeBlocks": 13 }
                  ]
                }
                """;
        Simulador sim3 = GestorPersistencia.construirSimuladorPreservandoDisco(
                saModificado, GestorPersistencia.cargarProcesosDesdeTexto(json2Frag));
        sim3.ejecutar();
        ListaEnlazada<Integer> cadenaOriginal = sim3.getDisco().obtenerCadenaBloques(
                saModificado.buscarPorRuta("/frag.bin").getPrimerBloque());
        // Mover cabezal a zona Swap (bloque >= totalBloquesDatos) antes de exportar
        sim3.getDisco().asignarBloquesSwap("SwapTest", 4);
        ListaEnlazada<modelo.Instruccion> progSwap = new ListaEnlazada<>();
        progSwap.agregar(new modelo.Instruccion(enums.TipoInstruccion.CPU));
        sim3.getDisco().encolarSolicitud(new disco.SolicitudES(
                new modelo.PCB("SwapMove", 1, TipoProceso.IO_BOUND, 1, 4, 500, "", 0, 0, progSwap, 0),
                TipoOperacionES.SUSPENDER, "", "", 200, 1, 0, true));
        for (int c = 1; c <= 40; c++) {
            sim3.getDisco().tick(c);
        }
        asegurar(sim3.getDisco().getPosicionCabezal() >= sim3.getDisco().getTotalBloquesDatos(),
                "Cabezal situado en área Swap antes de exportar (" + sim3.getDisco().getPosicionCabezal() + ")");

        File tmpFrag = File.createTempFile("avilaos_frag_", ".json");
        tmpFrag.deleteOnExit();
        GestorPersistencia.guardarSistemaArchivosJSON(saModificado, tmpFrag);
        ConfigSistemaArchivos recargadoFrag = GestorPersistencia.cargarSistemaArchivosJSON(tmpFrag);
        asegurar(recargadoFrag.getDiskConfig().getInitialHead() < recargadoFrag.getDiskConfig().getTotalBlocks(),
                "initialHead exportado queda acotado al rango de bloques de datos");

        Simulador simRecargado = GestorPersistencia.construirSimuladorDesdeDosJSON(recargadoFrag, procCfg2);
        SistemaArchivos saRecargado = (SistemaArchivos) simRecargado.getServicioArchivos();
        ListaEnlazada<Integer> cadenaRecargada = simRecargado.getDisco().obtenerCadenaBloques(
                saRecargado.buscarPorRuta("/frag.bin").getPrimerBloque());
        boolean mismaCadena = cadenaOriginal.getTamano() == 13 && cadenaRecargada.getTamano() == 13;
        for (int i = 0; mismaCadena && i < 13; i++) {
            if (!cadenaOriginal.obtener(i).equals(cadenaRecargada.obtener(i))) {
                mismaCadena = false;
            }
        }
        asegurar(mismaCadena, "Archivo fragmentado se guarda con blockChain y se recarga conservando la misma cadena");
    }

    public static void probarValidacionesEstrictas() {
        System.out.println("\n>>> PRUEBA 3: Validaciones estrictas de ambos JSON");
        boolean falloSinFileSize = false;
        try {
            GestorPersistencia.cargarProcesosDesdeTexto("""
                    {
                      "globalConfig": { "cycleDurationMs": 300, "swapSizeBlocks": 50, "initialDiskPolicy": "FIFO" },
                      "computer": { "ramCapacity": 512, "policy": "FCFS", "quantum": 0 },
                      "processes": [
                        { "name": "MalCreate", "memory": 16, "deadline": 100, "instructions": 2, "targetPath": "/a.txt", "operation": "CREATE" }
                      ]
                    }
                    """);
        } catch (Exception ex) {
            falloSinFileSize = true;
        }
        asegurar(falloSinFileSize, "Rechaza CREATE sin campo 'fileSizeBlocks'");

        boolean falloSolapamiento = false;
        try {
            GestorPersistencia.cargarSistemaArchivosDesdeTexto("""
                    {
                      "diskConfig": { "totalBlocks": 100, "initialHead": 10 },
                      "fileSystem": [
                        { "path": "/a.bin", "type": "FILE", "startBlock": 0, "blocks": 10, "owner": "admin", "permissions": "public" },
                        { "path": "/b.bin", "type": "FILE", "startBlock": 5, "blocks": 5, "owner": "admin", "permissions": "public" }
                      ]
                    }
                    """);
        } catch (Exception ex) {
            falloSolapamiento = true;
        }
        asegurar(falloSolapamiento, "Rechaza JSON 1 con bloques solapados");

        boolean falloJson2EnSlot1 = false;
        try {
            GestorPersistencia.cargarSistemaArchivosDesdeTexto(jsonProcesosOficial());
        } catch (IOException ex) {
            falloJson2EnSlot1 = true;
        }
        asegurar(falloJson2EnSlot1, "Rechaza cargar JSON 2 (Procesos) en el selector de JSON 1 (Sistema de Archivos)");

        boolean falloJson1EnSlot2 = false;
        try {
            GestorPersistencia.cargarProcesosDesdeTexto(jsonSistemaArchivosOficial());
        } catch (IOException ex) {
            falloJson1EnSlot2 = true;
        }
        asegurar(falloJson1EnSlot2, "Rechaza cargar JSON 1 (Sistema de Archivos) en el selector de JSON 2 (Procesos)");

        boolean falloMemExcedeRam = false;
        try {
            GestorPersistencia.cargarProcesosDesdeTexto("""
                    {
                      "globalConfig": { "cycleDurationMs": 300, "swapSizeBlocks": 50, "initialDiskPolicy": "FIFO" },
                      "computer": { "ramCapacity": 32, "policy": "FCFS", "quantum": 0 },
                      "processes": [
                        { "name": "Enorme", "memory": 64, "deadline": 100, "instructions": 2 }
                      ]
                    }
                    """);
        } catch (Exception ex) {
            falloMemExcedeRam = true;
        }
        asegurar(falloMemExcedeRam, "Rechaza proceso en JSON 2 con memory > computer.ramCapacity");

        boolean falloPolDesconocida = false;
        try {
            GestorPersistencia.cargarProcesosDesdeTexto("""
                    {
                      "globalConfig": { "cycleDurationMs": 300, "swapSizeBlocks": 50, "initialDiskPolicy": "INVENTADA" },
                      "computer": { "ramCapacity": 512, "policy": "FCFS", "quantum": 0 },
                      "processes": []
                    }
                    """);
        } catch (Exception ex) {
            falloPolDesconocida = true;
        }
        asegurar(falloPolDesconocida, "Rechaza initialDiskPolicy desconocida en JSON 2");
    }

    public static void probarConfigGeneralJsonYCSV() throws Exception {
        System.out.println("\n>>> PRUEBA 4: Exportación/Importación general JSON y CSV (sin buffers)");
        ConfiguracionSimulacion cfg = ConfiguracionSimulacion.crearPorDefecto();
        cfg.getProcesos().agregar(new ConfigProceso(
                3, "ES-Test", TipoProceso.IO_BOUND, 1, 2, 16, 1, 500, 0, 1,
                TipoOperacionES.LEER, "/sistema/boot.bin", 0, "", enums.ModoUsuario.ADMINISTRADOR, "admin"));

        File fJson = File.createTempFile("avilaos_cfg_", ".json");
        File fCsv = File.createTempFile("avilaos_cfg_", ".csv");
        fJson.deleteOnExit();
        fCsv.deleteOnExit();

        GestorPersistencia.guardarConfiguracion(cfg, fJson, FormatoPersistencia.JSON);
        GestorPersistencia.guardarConfiguracion(cfg, fCsv, FormatoPersistencia.CSV);

        ConfiguracionSimulacion cJson = GestorPersistencia.cargarConfiguracion(fJson, FormatoPersistencia.JSON);
        ConfiguracionSimulacion cCsv = GestorPersistencia.cargarConfiguracion(fCsv, FormatoPersistencia.CSV);

        asegurar(cJson.getTotalBloquesDisco() == 200 && cJson.getPoliticaDiscoInicial() == PoliticaDisco.FIFO,
                "JSON general conserva parámetros de disco");
        asegurar(cCsv.getProcesos().getTamano() == 3,
                "CSV general recupera los 3 procesos con operación E/S");

        Simulador sim = cJson.aSimulador();
        Computador pc = sim.buscarComputador(1);
        asegurar(pc != null && sim.getDisco() != null && sim.getServicioArchivos() != null,
                "aSimulador() construye PC único, Disco y SistemaArchivos");
    }

    public static void probarEdicionTablaComputador() {
        System.out.println("\n>>> PRUEBA 5: Edición de política y quantum en VentanaConfiguracion");
        VentanaConfiguracion.setModoPrueba(true);
        try {
            VentanaConfiguracion vc = new VentanaConfiguracion();
            var model = vc.getTablaComputadoresModel();
            asegurar(model.isCellEditable(0, 3), "Política editable en tabla");
            model.setValueAt(PoliticaPlanificacion.ROUND_ROBIN, 0, 3);
            asegurar(model.isCellEditable(0, 4), "Quantum editable al cambiar a Round Robin");
            model.setValueAt("5", 0, 4);
            asegurar(model.getLista().obtener(0).getQuantum() == 5, "Quantum actualizado a 5");

            javax.swing.JTabbedPane tabs = (javax.swing.JTabbedPane) ((java.awt.BorderLayout) vc.getContentPane().getLayout())
                    .getLayoutComponent(java.awt.BorderLayout.CENTER);
            java.awt.Component pieManual = ((java.awt.BorderLayout) vc.getContentPane().getLayout())
                    .getLayoutComponent(java.awt.BorderLayout.SOUTH);
            asegurar(!pieManual.isVisible(), "Barra manual oculta en Pestaña 1 (Carga dual JSON)");
            tabs.setSelectedIndex(1);
            asegurar(pieManual.isVisible(), "Barra manual visible en Pestaña 2 (Computador y Disco)");
            tabs.setSelectedIndex(0);
            asegurar(!pieManual.isVisible(), "Barra manual vuelve a ocultarse al regresar a Pestaña 1");
            vc.dispose();
        } finally {
            VentanaConfiguracion.setModoPrueba(false);
        }
    }
}
