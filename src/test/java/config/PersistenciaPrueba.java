package config;

import enums.EstadoProceso;
import enums.PoliticaPlanificacion;
import enums.TipoProceso;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import java.io.File;
import gui.VentanaConfiguracion;
import modelo.PCB;
import nucleo.Computador;
import simulacion.Simulador;
import sincronizacion.Buffer;

/**
 * Suite de pruebas automatizadas para la persistencia y configuración de ÁvilaOS (feat/config).
 *
 * <p>Verifica programáticamente el ciclo de vida de exportación e importación en formatos
 * JSON y CSV, la integridad de los datos, el rechazo de configuraciones inválidas y la
 * construcción y ejecución del simulador con estructuras propias (sin {@code java.util}).</p>
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
        System.out.println("     SUITE DE PRUEBAS DE PERSISTENCIA Y CONFIGURACIÓN (ÁVILA OS)");
        System.out.println("===============================================================================");

        try {
            probarExportacionImportacionJSON();
            probarExportacionImportacionCSV();
            probarValidacionesEstrictas();
            probarEstructurasPropiasSinJavaUtil();
            probarConstruccionYEjecucionSimulador();
            probarEdicionTablaComputadores();
            probarQuantumRoundRobinEnEjecucionYProcesos();
        } catch (Exception e) {
            System.err.println("Error no controlado durante las pruebas: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("\n===============================================================================");
        System.out.println("                       RESUMEN DE RESULTADOS");
        System.out.println("===============================================================================");
        System.out.println("  Total de aserciones evaluadas : " + totalAserciones);
        System.out.println("  Aserciones exitosas          : " + asercionesExitosas);
        System.out.println("  Aserciones fallidas          : " + asercionesFallidas);

        if (asercionesFallidas == 0) {
            System.out.println("  ESTADO FINAL                 : 100% PASADO (Persistencia validada)");
        } else {
            System.err.println("  ESTADO FINAL                 : CON ERRORES");
            System.exit(1);
        }
        System.out.println("===============================================================================");
    }

    private static ConfiguracionSimulacion crearConfiguracionCompleta() {
        ConfiguracionSimulacion cfg = new ConfiguracionSimulacion();
        cfg.setDuracionCicloMs(350);
        cfg.setLatenciaRedCiclos(4);

        // 3 Computadores
        cfg.getComputadores().agregar(new ConfigComputador(1, "PC-Alfa", 2048, PoliticaPlanificacion.FCFS, 2));
        cfg.getComputadores().agregar(new ConfigComputador(2, "PC-Beta", 1024, PoliticaPlanificacion.ROUND_ROBIN, 3));
        cfg.getComputadores().agregar(new ConfigComputador(3, "PC-Gamma", 4096, PoliticaPlanificacion.PRIORIDAD, 2));

        // 2 Buffers
        cfg.getBuffers().agregar(new ConfigBuffer("BUF-ALPHA", 8, 1, 4));
        cfg.getBuffers().agregar(new ConfigBuffer("BUF-BETA", 4, 2, 4));

        // 4 Procesos
        cfg.getProcesos().agregar(new ConfigProceso(1, "Proc-CPU", TipoProceso.CPU_BOUND, 1, 10, 300, 2, 80, 0, "", 1, 1));
        cfg.getProcesos().agregar(new ConfigProceso(2, "Proc-IO", TipoProceso.IO_BOUND, 2, 12, 400, 1, 90, 4, "", 1, 1));
        cfg.getProcesos().agregar(new ConfigProceso(3, "Prod-Remoto", TipoProceso.PRODUCTOR, 3, 10, 250, 3, 120, 0, "BUF-ALPHA", 2, 2));
        cfg.getProcesos().agregar(new ConfigProceso(4, "Cons-Local", TipoProceso.CONSUMIDOR, 1, 10, 250, 3, 120, 0, "BUF-ALPHA", 2, 2));

        return cfg;
    }

    /**
     * Prueba 1: Exportación e importación en formato JSON (Round-Trip completo).
     */
    public static void probarExportacionImportacionJSON() throws Exception {
        System.out.println("\n>>> PRUEBA 1: Exportación e importación en formato JSON");
        ConfiguracionSimulacion original = crearConfiguracionCompleta();

        File archivoTemp = File.createTempFile("avilaos_config_test_", ".json");
        archivoTemp.deleteOnExit();

        GestorPersistencia.guardarConfiguracion(original, archivoTemp, FormatoPersistencia.JSON);
        asegurar(archivoTemp.exists() && archivoTemp.length() > 0, "Archivo JSON generado exitosamente en disco");

        ConfiguracionSimulacion cargada = GestorPersistencia.cargarConfiguracion(archivoTemp, FormatoPersistencia.JSON);

        // Validar parámetros globales
        asegurar(cargada.getDuracionCicloMs() == 350, "Duración de ciclo recuperada correctamente (350 ms)");
        asegurar(cargada.getLatenciaRedCiclos() == 4, "Latencia de red recuperada correctamente (4 ciclos)");

        // Validar computadores
        asegurar(cargada.getComputadores().getTamano() == 3, "Cantidad de computadores recuperada (3)");
        ConfigComputador pc2 = cargada.buscarComputadorPorId(2);
        asegurar(pc2 != null && "PC-Beta".equals(pc2.getNombre()), "Computador PC-Beta encontrado por ID 2");
        asegurar(pc2 != null && pc2.getPolitica() == PoliticaPlanificacion.ROUND_ROBIN, "Política Round Robin preservada");
        asegurar(pc2 != null && pc2.getQuantum() == 3, "Quantum = 3 preservado");

        // Validar buffers
        asegurar(cargada.getBuffers().getTamano() == 2, "Cantidad de buffers recuperada (2)");
        ConfigBuffer bufA = cargada.buscarBufferPorId("BUF-ALPHA");
        asegurar(bufA != null && bufA.getCapacidad() == 8, "Buffer BUF-ALPHA tiene capacidad 8");
        asegurar(bufA != null && bufA.getIdComputadorAnfitrion() == 1, "Anfitrión de BUF-ALPHA es PC-1");

        // Validar procesos
        asegurar(cargada.getProcesos().getTamano() == 4, "Cantidad de procesos recuperada (4)");
        ConfigProceso prod = null;
        Iterador<ConfigProceso> itP = cargada.getProcesos().iterador();
        while (itP.haySiguiente()) {
            ConfigProceso p = itP.siguiente();
            if ("Prod-Remoto".equals(p.getNombre())) {
                prod = p;
                break;
            }
        }
        asegurar(prod != null && prod.getTipo() == TipoProceso.PRODUCTOR, "Productor 'Prod-Remoto' recuperado");
        asegurar(prod != null && "BUF-ALPHA".equals(prod.getIdBufferTarget()), "Buffer target 'BUF-ALPHA' preservado");
        asegurar(prod != null && prod.getElementosRequeridos() == 2, "Elementos requeridos = 2 preservado");
    }

    /**
     * Prueba 2: Exportación e importación en formato CSV (Round-Trip completo).
     */
    public static void probarExportacionImportacionCSV() throws Exception {
        System.out.println("\n>>> PRUEBA 2: Exportación e importación en formato CSV");
        ConfiguracionSimulacion original = crearConfiguracionCompleta();

        File archivoTemp = File.createTempFile("avilaos_config_test_", ".csv");
        archivoTemp.deleteOnExit();

        GestorPersistencia.guardarConfiguracion(original, archivoTemp, FormatoPersistencia.CSV);
        asegurar(archivoTemp.exists() && archivoTemp.length() > 0, "Archivo CSV generado exitosamente en disco");

        ConfiguracionSimulacion cargada = GestorPersistencia.cargarConfiguracion(archivoTemp, FormatoPersistencia.CSV);

        // Validaciones en CSV
        asegurar(cargada.getDuracionCicloMs() == 350, "CSV: Duración de ciclo recuperada (350 ms)");
        asegurar(cargada.getLatenciaRedCiclos() == 4, "CSV: Latencia de red recuperada (4 ciclos)");
        asegurar(cargada.getComputadores().getTamano() == 3, "CSV: 3 computadores recuperados");
        asegurar(cargada.getBuffers().getTamano() == 2, "CSV: 2 buffers recuperados");
        asegurar(cargada.getProcesos().getTamano() == 4, "CSV: 4 procesos recuperados");

        ConfigComputador pc3 = cargada.buscarComputadorPorId(3);
        asegurar(pc3 != null && "PC-Gamma".equals(pc3.getNombre()), "CSV: PC-Gamma identificado");
        asegurar(pc3 != null && pc3.getPolitica() == PoliticaPlanificacion.PRIORIDAD, "CSV: Política PRIORIDAD preservada");

        ConfigBuffer bufB = cargada.buscarBufferPorId("BUF-BETA");
        asegurar(bufB != null && bufB.getCapacidad() == 4, "CSV: Buffer BUF-BETA capacidad 4");

        ConfigProceso pIo = null;
        Iterador<ConfigProceso> it = cargada.getProcesos().iterador();
        while (it.haySiguiente()) {
            ConfigProceso p = it.siguiente();
            if ("Proc-IO".equals(p.getNombre())) {
                pIo = p;
                break;
            }
        }
        asegurar(pIo != null && pIo.getPeriodoES() == 4, "CSV: Periodo de E/S = 4 preservado");
    }

    /**
     * Prueba 3: Detección y rechazo estricto de configuraciones inconsistentes.
     */
    public static void probarValidacionesEstrictas() {
        System.out.println("\n>>> PRUEBA 3: Validaciones estrictas de consistencia");

        // 1. Menos de 2 computadores
        ConfiguracionSimulacion cfg1 = new ConfiguracionSimulacion();
        cfg1.getComputadores().agregar(new ConfigComputador(1, "PC-Unico", 1024, PoliticaPlanificacion.FCFS, 2));
        ListaEnlazada<String> err1 = cfg1.validar();
        asegurar(!err1.estaVacia(), "Detectado error: clúster con menos de 2 computadores");

        // 2. Buffer con computador anfitrión inexistente
        ConfiguracionSimulacion cfg2 = ConfiguracionSimulacion.crearPorDefecto();
        cfg2.getBuffers().agregar(new ConfigBuffer("BUF-FANTASMA", 5, 999)); // PC 999 no existe
        ListaEnlazada<String> err2 = cfg2.validar();
        asegurar(!err2.estaVacia(), "Detectado error: buffer anfitrión inexistente");

        // 3. Proceso que supera la memoria del computador
        ConfiguracionSimulacion cfg3 = ConfiguracionSimulacion.crearPorDefecto();
        // PC-1 tiene 1024 KB; proceso pide 5000 KB
        cfg3.getProcesos().agregar(new ConfigProceso(99, "Proc-Gigante", TipoProceso.CPU_BOUND, 1, 10, 5000, 1, 100, 0, "", 1, 1));
        ListaEnlazada<String> err3 = cfg3.validar();
        asegurar(!err3.estaVacia(), "Detectado error: proceso excede RAM máxima del computador");

        // 4. Productor con buffer inexistente
        ConfiguracionSimulacion cfg4 = ConfiguracionSimulacion.crearPorDefecto();
        cfg4.getProcesos().agregar(new ConfigProceso(98, "Prod-SinBuf", TipoProceso.PRODUCTOR, 1, 10, 100, 1, 100, 0, "BUF-INEXISTENTE", 1, 1));
        ListaEnlazada<String> err4 = cfg4.validar();
        asegurar(!err4.estaVacia(), "Detectado error: productor con buffer inexistente");

        // 5. Verificar que validarEstricto lanza IllegalArgumentException
        boolean excepcionLanzada = false;
        try {
            cfg3.validarEstricto();
        } catch (IllegalArgumentException ex) {
            excepcionLanzada = true;
        }
        asegurar(excepcionLanzada, "validarEstricto() lanzó IllegalArgumentException ante datos inválidos");
    }

    /**
     * Prueba 4: Verificación de uso de estructuras propias sin java.util collections.
     */
    public static void probarEstructurasPropiasSinJavaUtil() {
        System.out.println("\n>>> PRUEBA 4: Verificación de TDA propio (ListaEnlazada sin java.util)");
        ConfiguracionSimulacion cfg = ConfiguracionSimulacion.crearPorDefecto();

        asegurar(cfg.getComputadores() instanceof ListaEnlazada, "getComputadores() es una ListaEnlazada propia");
        asegurar(cfg.getBuffers() instanceof ListaEnlazada, "getBuffers() es una ListaEnlazada propia");
        asegurar(cfg.getProcesos() instanceof ListaEnlazada, "getProcesos() es una ListaEnlazada propia");

        // Verificación de iteración pura
        Iterador<ConfigComputador> it = cfg.getComputadores().iterador();
        int count = 0;
        while (it.haySiguiente()) {
            it.siguiente();
            count++;
        }
        asegurar(count == cfg.getComputadores().getTamano(), "Iterador propio recorrió todos los elementos");
    }

    /**
     * Prueba 5: Construcción y ejecución del Simulador a partir de configuración importada.
     */
    public static void probarConstruccionYEjecucionSimulador() throws Exception {
        System.out.println("\n>>> PRUEBA 5: Construcción y ejecución de simulación desde configuración");
        ConfiguracionSimulacion cfg = crearConfiguracionCompleta();

        // Convertir configuración en Simulador funcional
        Simulador sim = cfg.aSimulador();
        asegurar(sim != null, "Simulador instanciado exitosamente desde configuración");
        asegurar(sim.getCantidadComputadores() == 3, "Simulador tiene 3 computadores registrados");
        asegurar(sim.getDuracionCicloMs() == 350, "Duración de ciclo aplicada al simulador (350 ms)");
        asegurar(sim.getLatenciaRedCiclos() == 4, "Latencia de red aplicada al simulador (4 ciclos)");

        // Verificar buffers en el simulador
        Buffer bufAlfa = sim.buscarBuffer("BUF-ALPHA");
        asegurar(bufAlfa != null, "Buffer BUF-ALPHA registrado en el simulador");
        asegurar(bufAlfa != null && bufAlfa.getCapacidad() == 8, "BUF-ALPHA tiene capacidad 8");

        // Ejecutar 15 ciclos del reloj para verificar avance real de la simulación
        for (int i = 0; i < 15; i++) {
            sim.getReloj().avanzarCiclo();
        }

        asegurar(sim.getCicloActual() == 15, "El reloj de la simulación avanzó a ciclo 15");
        Computador pc1 = sim.buscarComputador(1);
        asegurar(pc1 != null && pc1.getCpu().getCiclosEjecutados() > 0, "CPU de PC-Alfa ejecutó instrucciones");

        // Verificar invariante de semáforos del buffer
        asegurar(bufAlfa.getValorVacios() + bufAlfa.getValorLlenos() == bufAlfa.getCapacidad(),
                "Invariante de semáforos respetada tras ejecución: vacios + llenos == capacidad");
    }

    /**
     * Prueba 6: Verificación de edición directa en tabla de Computadores:
     * política mediante desplegable y quantum editable con doble clic solo cuando es Round Robin.
     */
    public static void probarEdicionTablaComputadores() {
        System.out.println("\n>>> PRUEBA 6: Edición en tabla de computadores (Política y Quantum)");
        VentanaConfiguracion.setModoPrueba(true);
        try {
            VentanaConfiguracion vc = new VentanaConfiguracion();
            var model = vc.getTablaComputadoresModel();

            // Fila 0 es PC-1 (FCFS por defecto)
            asegurar(model.isCellEditable(0, 3), "Columna Política Planificación es editable directamente en la tabla");
            asegurar(!model.isCellEditable(0, 4), "Columna Quantum NO es editable cuando la política es FCFS");
            asegurar("-".equals(model.getValueAt(0, 4)), "Valor de Quantum se visualiza como '-' cuando no es Round Robin");

            // Cambiar política a Round Robin directamente desde la tabla
            model.setValueAt(PoliticaPlanificacion.ROUND_ROBIN, 0, 3);
            ConfigComputador pc1 = model.getLista().obtener(0);
            asegurar(pc1.getPolitica() == PoliticaPlanificacion.ROUND_ROBIN, "Política de PC-1 actualizada a Round Robin");
            asegurar(model.isCellEditable(0, 4), "Columna Quantum AHORA es editable al seleccionar Round Robin");
            asegurar(model.getValueAt(0, 4) instanceof Integer, "Quantum muestra valor numérico tras cambiar a Round Robin");

            // Editar el Quantum con un valor válido (> 0)
            model.setValueAt("5", 0, 4);
            asegurar(pc1.getQuantum() == 5, "Quantum modificado exitosamente a 5");
            asegurar(Integer.valueOf(5).equals(model.getValueAt(0, 4)), "Tabla refleja nuevo valor de Quantum (5)");

            // Intentar valores inválidos (0, negativos, texto no numérico)
            model.setValueAt("0", 0, 4);
            asegurar(pc1.getQuantum() == 5, "Quantum = 0 rechazado; mantiene valor previo (5)");

            model.setValueAt("-2", 0, 4);
            asegurar(pc1.getQuantum() == 5, "Quantum negativo rechazado; mantiene valor previo (5)");

            model.setValueAt("invalido", 0, 4);
            asegurar(pc1.getQuantum() == 5, "Quantum no numérico rechazado; mantiene valor previo (5)");

            // Cambiar política a Prioridades: el quantum debe volver a ser no editable
            model.setValueAt(PoliticaPlanificacion.PRIORIDAD, 0, 3);
            asegurar(pc1.getPolitica() == PoliticaPlanificacion.PRIORIDAD, "Política cambiada a PRIORIDAD");
            asegurar(!model.isCellEditable(0, 4), "Columna Quantum vuelve a ser NO editable al cambiar a Prioridades");
            asegurar("-".equals(model.getValueAt(0, 4)), "Quantum vuelve a mostrar '-'");

            // Fila 1 es PC-2 (EDF por defecto)
            asegurar(!model.isCellEditable(1, 4), "PC-2 (EDF) tiene Quantum no editable");

            // Verificar que el editor de Política es un JComboBox
            javax.swing.table.TableCellEditor editorCol3 = vc.getTblComputadores().getColumnModel().getColumn(3).getCellEditor();
            asegurar(editorCol3 instanceof javax.swing.DefaultCellEditor, "Columna Política tiene DefaultCellEditor configurado");

            // Verificar que el editor de Quantum requiere 2 clics (doble clic)
            javax.swing.table.TableCellEditor editorCol4 = vc.getTblComputadores().getColumnModel().getColumn(4).getCellEditor();
            asegurar(editorCol4 instanceof javax.swing.DefaultCellEditor de && de.getClickCountToStart() == 2,
                    "Columna Quantum requiere doble clic para iniciar edición (clickCountToStart == 2)");

            vc.dispose();
        } finally {
            VentanaConfiguracion.setModoPrueba(false);
        }
    }

    /**
     * Prueba 7: Verificación del cambio de Quantum en tiempo de ejecución
     * y visualización del Quantum en la tabla de procesos de la vista de configuración.
     */
    public static void probarQuantumRoundRobinEnEjecucionYProcesos() {
        System.out.println("\n>>> PRUEBA 7: Quantum Round Robin en ejecución y en tabla de procesos");
        VentanaConfiguracion.setModoPrueba(true);
        try {
            // 1. Cambio de quantum en tiempo de ejecución en Planificador
            Computador pc = new Computador(1, "PC-Test", 1024,
                    planificacion.FabricaPoliticas.crear(PoliticaPlanificacion.ROUND_ROBIN, 2));
            asegurar(pc.getPlanificador().getQuantum() == 2, "Quantum inicial de Round Robin es 2");

            pc.getPlanificador().setQuantum(5);
            asegurar(pc.getPlanificador().getQuantum() == 5, "Quantum actualizado en caliente a 5 en tiempo de ejecución");

            boolean errorQuantumInvalido = false;
            try {
                pc.getPlanificador().setQuantum(0);
            } catch (IllegalArgumentException ex) {
                errorQuantumInvalido = true;
            }
            asegurar(errorQuantumInvalido, "Rechazo de quantum inválido (<= 0) con IllegalArgumentException");

            // 2. Visualización del Quantum en la tabla de procesos de VentanaConfiguracion
            VentanaConfiguracion vc = new VentanaConfiguracion();
            var modeloProcesos = vc.getTablaProcesosModel();
            var modeloComputadores = vc.getTablaComputadoresModel();

            // En la configuración por defecto:
            // PC-1 es FCFS (sin quantum)
            // PC-2 es EDF (sin quantum)
            // PC-3 es PRIORIDAD (sin quantum)
            // P1 y P2 están asignados a PC-1 -> su Quantum (RR) debe ser "-"
            asegurar("-".equals(modeloProcesos.getValueAt(0, 4)), "P1 asignado a PC-1 (FCFS) muestra '-' en columna Quantum");
            asegurar("-".equals(modeloProcesos.getValueAt(1, 4)), "P2 asignado a PC-1 (FCFS) muestra '-' en columna Quantum");

            // Cambiamos PC-1 a Round Robin con quantum 4 en la tabla de computadores
            modeloComputadores.setValueAt(PoliticaPlanificacion.ROUND_ROBIN, 0, 3);
            modeloComputadores.setValueAt("4", 0, 4);

            // Ahora los procesos P1 y P2 (asignados a PC-1) deben mostrar Quantum 4 automáticamente
            asegurar(Integer.valueOf(4).equals(modeloProcesos.getValueAt(0, 4)),
                    "P1 ahora muestra Quantum 4 porque su computador destino (PC-1) es Round Robin");
            asegurar(Integer.valueOf(4).equals(modeloProcesos.getValueAt(1, 4)),
                    "P2 ahora muestra Quantum 4 porque su computador destino (PC-1) es Round Robin");

            // Cambiamos el quantum de PC-1 a 6
            modeloComputadores.setValueAt("6", 0, 4);
            asegurar(Integer.valueOf(6).equals(modeloProcesos.getValueAt(0, 4)),
                    "P1 refleja inmediatamente el nuevo Quantum 6 de su computador");

            // Si cambiamos PC-1 de vuelta a FCFS, los procesos vuelven a mostrar '-'
            modeloComputadores.setValueAt(PoliticaPlanificacion.FCFS, 0, 3);
            asegurar("-".equals(modeloProcesos.getValueAt(0, 4)),
                    "P1 vuelve a mostrar '-' al cambiar PC-1 a FCFS");

            vc.dispose();
        } finally {
            VentanaConfiguracion.setModoPrueba(false);
        }
    }
}
