package config;

import archivos.SistemaArchivos;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import disco.DiscoSimulado;
import enums.PoliticaPlanificacion;
import enums.TipoInstruccion;
import enums.TipoOperacionES;
import enums.TipoProceso;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import modelo.Instruccion;
import modelo.PCB;
import nucleo.Computador;
import planificacion.AlgoritmoPlanificacion;
import planificacion.FabricaPoliticas;
import planificacion.RoundRobin;
import simulacion.Simulador;

/**
 * Gestor de persistencia del Proyecto 2 de ÁvilaOS (JSON dual obligatorio y configuración).
 *
 * <p>Permite cargar y guardar el estado del sistema de archivos (<b>JSON 1</b>:
 * {@code diskConfig} + {@code fileSystem}), cargar las políticas y procesos de E/S
 * (<b>JSON 2</b>: {@code globalConfig} + {@code computer} + {@code processes}), así como
 * guardar/cargar configuraciones generales en JSON y CSV usando exclusivamente
 * {@link ListaEnlazada} (sin colecciones de {@code java.util}).</p>
 */
public final class GestorPersistencia {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeHierarchyAdapter(ListaEnlazada.class, new AdaptadorListaEnlazada())
            .setPrettyPrinting()
            .create();

    private GestorPersistencia() {
        // Clase de utilidades estáticas.
    }

    // =========================================================================
    // PERSISTENCIA DUAL OBLIGATORIA DEL PROYECTO 2 (2 ARCHIVOS JSON)
    // =========================================================================

    /**
     * Carga y valida el <b>JSON 1 (Sistema de Archivos y Disco)</b> desde archivo.
     */
    public static ConfigSistemaArchivos cargarSistemaArchivosJSON(File archivo) throws IOException {
        if (archivo == null || !archivo.exists() || !archivo.isFile()) {
            throw new IOException("El archivo JSON del sistema de archivos no existe: " + archivo);
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(archivo), StandardCharsets.UTF_8))) {
            return parsearYDeserializarSistemaArchivos(reader);
        } catch (JsonSyntaxException ex) {
            throw new IOException("Formato JSON inválido en archivo de sistema de archivos: " + ex.getMessage(), ex);
        }
    }

    /** Deserializa y valida el JSON 1 desde una cadena de texto. */
    public static ConfigSistemaArchivos cargarSistemaArchivosDesdeTexto(String contenido) throws IOException {
        if (contenido == null || contenido.trim().isEmpty()) {
            throw new IOException("El JSON del sistema de archivos está vacío.");
        }
        try {
            return parsearYDeserializarSistemaArchivos(new java.io.StringReader(contenido));
        } catch (JsonSyntaxException ex) {
            throw new IOException("Formato JSON inválido: " + ex.getMessage(), ex);
        }
    }

    private static ConfigSistemaArchivos parsearYDeserializarSistemaArchivos(java.io.Reader reader) throws IOException {
        JsonObject obj = parsearObjetoRaiz(reader, "sistema de archivos");
        if (obj.has("globalConfig") || obj.has("computer") || obj.has("processes")) {
            throw new IOException(
                    "El archivo seleccionado corresponde a Procesos y Configuración (JSON 2), "
                            + "no al Sistema de Archivos y Disco (JSON 1).");
        }
        if (!obj.has("diskConfig") || !obj.get("diskConfig").isJsonObject()) {
            throw new IOException("Falta el bloque obligatorio 'diskConfig' en el JSON de Sistema de Archivos.");
        }
        JsonObject diskObj = obj.getAsJsonObject("diskConfig");
        if (!diskObj.has("totalBlocks") || !diskObj.has("initialHead")) {
            throw new IOException("El bloque 'diskConfig' debe incluir 'totalBlocks' e 'initialHead'.");
        }
        if (!obj.has("fileSystem") || !obj.get("fileSystem").isJsonArray()) {
            throw new IOException("Falta el arreglo obligatorio 'fileSystem' en el JSON de Sistema de Archivos.");
        }
        ConfigSistemaArchivos cfg = GSON.fromJson(obj, ConfigSistemaArchivos.class);
        if (cfg == null) {
            throw new IOException("No se pudo deserializar el JSON del sistema de archivos.");
        }
        cfg.validarEstricto();
        return cfg;
    }

    /** Guarda el estado de un {@link SistemaArchivos} en el formato oficial del JSON 1. */
    public static void guardarSistemaArchivosJSON(SistemaArchivos sa, File archivo) throws IOException {
        guardarSistemaArchivosJSON(ConfigSistemaArchivos.desdeSistemaArchivos(sa), archivo);
    }

    /** Guarda una configuración de sistema de archivos en el formato oficial del JSON 1. */
    public static void guardarSistemaArchivosJSON(ConfigSistemaArchivos config, File archivo) throws IOException {
        if (config == null) {
            throw new IllegalArgumentException("La configuración del sistema de archivos no puede ser nula.");
        }
        if (archivo == null) {
            throw new IllegalArgumentException("El archivo de destino no puede ser nulo.");
        }
        config.validarEstricto();
        asegurarDirectorioPadre(archivo);
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(archivo), StandardCharsets.UTF_8))) {
            GSON.toJson(config, writer);
            writer.flush();
        }
    }

    /** Serializa el JSON 1 como cadena de texto. */
    public static String guardarSistemaArchivosComoTexto(ConfigSistemaArchivos config) {
        if (config == null) {
            throw new IllegalArgumentException("La configuración del sistema de archivos no puede ser nula.");
        }
        config.validarEstricto();
        return GSON.toJson(config);
    }

    /**
     * Carga y valida el <b>JSON 2 (Procesos y Configuración Global/Computador)</b> desde archivo.
     */
    public static ConfigProcesosDisco cargarProcesosJSON(File archivo) throws IOException {
        if (archivo == null || !archivo.exists() || !archivo.isFile()) {
            throw new IOException("El archivo JSON de procesos no existe: " + archivo);
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(archivo), StandardCharsets.UTF_8))) {
            return parsearYDeserializarProcesos(reader);
        } catch (JsonSyntaxException ex) {
            throw new IOException("Formato JSON inválido en archivo de procesos: " + ex.getMessage(), ex);
        }
    }

    /** Deserializa y valida el JSON 2 desde una cadena de texto. */
    public static ConfigProcesosDisco cargarProcesosDesdeTexto(String contenido) throws IOException {
        if (contenido == null || contenido.trim().isEmpty()) {
            throw new IOException("El JSON de procesos está vacío.");
        }
        try {
            return parsearYDeserializarProcesos(new java.io.StringReader(contenido));
        } catch (JsonSyntaxException ex) {
            throw new IOException("Formato JSON inválido: " + ex.getMessage(), ex);
        }
    }

    private static ConfigProcesosDisco parsearYDeserializarProcesos(java.io.Reader reader) throws IOException {
        JsonObject obj = parsearObjetoRaiz(reader, "procesos y configuración");
        if (obj.has("diskConfig") || obj.has("fileSystem")) {
            throw new IOException(
                    "El archivo seleccionado corresponde al Sistema de Archivos y Disco (JSON 1), "
                            + "no a Procesos y Configuración (JSON 2).");
        }
        if (!obj.has("globalConfig") || !obj.get("globalConfig").isJsonObject()) {
            throw new IOException("Falta el bloque obligatorio 'globalConfig' en el JSON de Procesos.");
        }
        if (!obj.has("computer") || !obj.get("computer").isJsonObject()) {
            throw new IOException("Falta el bloque obligatorio 'computer' en el JSON de Procesos.");
        }
        if (!obj.has("processes") || !obj.get("processes").isJsonArray()) {
            throw new IOException("Falta el arreglo obligatorio 'processes' en el JSON de Procesos.");
        }
        ConfigProcesosDisco cfg = GSON.fromJson(obj, ConfigProcesosDisco.class);
        if (cfg == null) {
            throw new IOException("No se pudo deserializar el JSON de procesos.");
        }
        cfg.validarEstricto();
        return cfg;
    }

    private static JsonObject parsearObjetoRaiz(java.io.Reader reader, String descripcion) throws IOException {
        JsonElement elem = JsonParser.parseReader(reader);
        if (elem == null || !elem.isJsonObject()) {
            throw new IOException("El JSON de " + descripcion + " debe ser un objeto raíz válido.");
        }
        return elem.getAsJsonObject();
    }

    /** Guarda la configuración del JSON 2 en archivo. */
    public static void guardarProcesosJSON(ConfigProcesosDisco config, File archivo) throws IOException {
        if (config == null) {
            throw new IllegalArgumentException("La configuración de procesos no puede ser nula.");
        }
        if (archivo == null) {
            throw new IllegalArgumentException("El archivo de destino no puede ser nulo.");
        }
        config.validarEstricto();
        asegurarDirectorioPadre(archivo);
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(archivo), StandardCharsets.UTF_8))) {
            GSON.toJson(config, writer);
            writer.flush();
        }
    }

    /** Serializa el JSON 2 como cadena de texto. */
    public static String guardarProcesosComoTexto(ConfigProcesosDisco config) {
        if (config == null) {
            throw new IllegalArgumentException("La configuración de procesos no puede ser nula.");
        }
        config.validarEstricto();
        return GSON.toJson(config);
    }

    /**
     * Construye un {@link Simulador} completo con 1 único {@link Computador},
     * {@link DiscoSimulado} y {@link SistemaArchivos} a partir de los dos archivos
     * JSON obligatorios del Proyecto 2.
     */
    public static Simulador construirSimuladorDesdeDosJSON(ConfigSistemaArchivos fsCfg,
                                                           ConfigProcesosDisco procCfg) {
        if (fsCfg == null || procCfg == null) {
            throw new IllegalArgumentException(
                    "Se requieren ambos archivos JSON (Sistema de Archivos y Procesos/Configuración).");
        }
        fsCfg.validarEstricto();
        procCfg.validarEstricto();

        DiscoSimulado disco = new DiscoSimulado("Disco-SD",
                fsCfg.getDiskConfig().getTotalBlocks(),
                fsCfg.getDiskConfig().getInitialHead(),
                0);
        SistemaArchivos sa = new SistemaArchivos(disco);
        fsCfg.aplicarADiscoYSistema(disco, sa);

        return ensamblarSimulador(disco, sa, procCfg);
    }

    /**
     * Reinicia una simulación cargando un nuevo JSON 2 de procesos pero <b>preservando
     * intacto el estado modificado del disco y sistema de archivos</b> de la ejecución anterior.
     */
    public static Simulador construirSimuladorPreservandoDisco(SistemaArchivos saPreservado,
                                                                ConfigProcesosDisco procCfg) {
        if (saPreservado == null || saPreservado.getDisco() == null) {
            throw new IllegalArgumentException("No existe un sistema de archivos/disco previo que preservar.");
        }
        if (procCfg == null) {
            throw new IllegalArgumentException("La configuración de procesos (JSON 2) no puede ser nula.");
        }
        procCfg.validarEstricto();
        saPreservado.cancelarCreacionesPendientes();
        saPreservado.getDisco().reiniciarParaNuevaSimulacion();
        return ensamblarSimulador(saPreservado.getDisco(), saPreservado, procCfg);
    }

    private static Simulador ensamblarSimulador(DiscoSimulado disco, SistemaArchivos sa,
                                                ConfigProcesosDisco procCfg) {
        disco.configurarAreaSwap(procCfg.getGlobalConfig().getSwapSizeBlocks());
        disco.setPolitica(procCfg.getPoliticaDiscoEnum());

        PoliticaPlanificacion polCpu = procCfg.getPoliticaCpuEnum();
        int quantum = procCfg.getComputer().getQuantum();
        AlgoritmoPlanificacion alg = (polCpu == PoliticaPlanificacion.ROUND_ROBIN)
                ? FabricaPoliticas.crear(PoliticaPlanificacion.ROUND_ROBIN,
                        quantum > 0 ? quantum : RoundRobin.QUANTUM_POR_DEFECTO)
                : FabricaPoliticas.crear(polCpu);

        Computador pc = new Computador(1, "PC-1", procCfg.getComputer().getRamCapacity(), alg);

        Simulador sim = new Simulador();
        sim.setDuracionCicloMs(procCfg.getGlobalConfig().getCycleDurationMs());
        sim.registrarComputador(pc);
        sim.setDisco(disco);
        sim.setServicioArchivos(sa);

        Iterador<ConfigProcesosDisco.ProcesoJson> it = procCfg.getProcesses().iterador();
        while (it.haySiguiente()) {
            ConfigProcesosDisco.ProcesoJson pj = it.siguiente();
            PCB pcb = ConfigProcesosDisco.crearPCB(pj, pc.getId(), sim.getCicloActual());
            sim.cargarProceso(pcb);
        }

        sim.marcarJsonCargados(true, true);
        return sim;
    }

    // =========================================================================
    // PERSISTENCIA GENERAL DE ConfiguracionSimulacion (JSON Y CSV SIN BUFFERS)
    // =========================================================================

    public static void guardarConfiguracion(ConfiguracionSimulacion config, File archivo, FormatoPersistencia formato)
            throws IOException {
        if (config == null) {
            throw new IllegalArgumentException("La configuración a guardar no puede ser nula.");
        }
        if (archivo == null) {
            throw new IllegalArgumentException("El archivo de destino no puede ser nulo.");
        }
        if (formato == null) {
            formato = detectarFormatoPorExtension(archivo.getName());
        }
        config.validarEstricto();
        asegurarDirectorioPadre(archivo);

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(archivo), StandardCharsets.UTF_8))) {
            if (formato == FormatoPersistencia.JSON) {
                GSON.toJson(config, writer);
                writer.flush();
            } else {
                guardarCSV(config, writer);
            }
        }
    }

    public static ConfiguracionSimulacion cargarConfiguracion(File archivo, FormatoPersistencia formato)
            throws IOException {
        if (archivo == null || !archivo.exists() || !archivo.isFile()) {
            throw new IOException("El archivo especificado no existe o no es válido: " + archivo);
        }
        if (formato == null) {
            formato = detectarFormatoPorExtension(archivo.getName());
        }
        ConfiguracionSimulacion config;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(archivo), StandardCharsets.UTF_8))) {
            if (formato == FormatoPersistencia.JSON) {
                config = GSON.fromJson(reader, ConfiguracionSimulacion.class);
            } else {
                config = cargarCSV(reader);
            }
        }
        if (config == null) {
            throw new IOException("No se pudo deserializar la configuración del archivo.");
        }
        config.validarEstricto();
        return config;
    }

    public static String guardarComoTexto(ConfiguracionSimulacion config, FormatoPersistencia formato) throws IOException {
        StringWriter sw = new StringWriter();
        if (formato == FormatoPersistencia.JSON) {
            GSON.toJson(config, sw);
            sw.flush();
        } else {
            guardarCSV(config, sw);
        }
        return sw.toString();
    }

    public static ConfiguracionSimulacion cargarDesdeTexto(String contenido, FormatoPersistencia formato) throws IOException {
        if (contenido == null || contenido.trim().isEmpty()) {
            throw new IOException("El contenido está vacío.");
        }
        StringReader sr = new StringReader(contenido);
        ConfiguracionSimulacion config = (formato == FormatoPersistencia.JSON)
                ? GSON.fromJson(sr, ConfiguracionSimulacion.class)
                : cargarCSV(sr);
        if (config != null) {
            config.validarEstricto();
        }
        return config;
    }

    private static void guardarCSV(ConfiguracionSimulacion config, Writer writer) throws IOException {
        BufferedWriter bw = (writer instanceof BufferedWriter) ? (BufferedWriter) writer : new BufferedWriter(writer);

        bw.write("# CONFIGURACION_AVILA_OS_P2");
        bw.newLine();
        bw.write("# PARAMETROS_GLOBALES");
        bw.newLine();
        bw.write("duracionCicloMs,totalBloquesDisco,tamanoSwap,cabezalInicial,velocidadCabezal,politicaDiscoInicial");
        bw.newLine();
        bw.write(config.getDuracionCicloMs() + "," + config.getTotalBloquesDisco() + ","
                + config.getTamanoSwap() + "," + config.getCabezalInicial() + ","
                + config.getVelocidadCabezal() + ","
                + (config.getPoliticaDiscoInicial() != null ? config.getPoliticaDiscoInicial().name() : "FIFO"));
        bw.newLine();
        bw.newLine();

        bw.write("# COMPUTADORES");
        bw.newLine();
        bw.write("id,nombre,memoriaTotal,politica,quantum");
        bw.newLine();
        Iterador<ConfigComputador> itPc = config.getComputadores().iterador();
        while (itPc.haySiguiente()) {
            ConfigComputador pc = itPc.siguiente();
            bw.write(pc.getId() + "," + escaparCSV(pc.getNombre()) + "," + pc.getMemoriaTotal() + ","
                    + pc.getPolitica().name() + "," + pc.getQuantum());
            bw.newLine();
        }
        bw.newLine();

        bw.write("# PROCESOS");
        bw.newLine();
        bw.write("id,nombre,tipo,computadorAsignado,instrucciones,memoria,prioridad,deadline,periodoES,ciclosPorAcceso,operacion,rutaObjetivo,tamanoArchivoBloques");
        bw.newLine();
        Iterador<ConfigProceso> itProc = config.getProcesos().iterador();
        while (itProc.haySiguiente()) {
            ConfigProceso p = itProc.siguiente();
            String op = p.getOperacion() != null ? p.getOperacion().getCodigoJson() : "";
            bw.write(p.getId() + "," + escaparCSV(p.getNombre()) + "," + p.getTipo().name() + ","
                    + p.getComputadorAsignado() + "," + p.getInstrucciones() + "," + p.getMemoria() + ","
                    + p.getPrioridad() + "," + p.getDeadline() + "," + p.getPeriodoES() + ","
                    + p.getCiclosPorAcceso() + "," + escaparCSV(op) + ","
                    + escaparCSV(p.getRutaObjetivo()) + "," + p.getTamanoArchivoBloques());
            bw.newLine();
        }
        bw.flush();
    }

    private static ConfiguracionSimulacion cargarCSV(Reader reader) throws IOException {
        BufferedReader br = (reader instanceof BufferedReader) ? (BufferedReader) reader : new BufferedReader(reader);
        ConfiguracionSimulacion config = new ConfiguracionSimulacion();
        config.setComputadores(new ListaEnlazada<>());
        config.setProcesos(new ListaEnlazada<>());

        String linea;
        String seccionActual = "";
        while ((linea = br.readLine()) != null) {
            linea = linea.trim();
            if (linea.isEmpty()) {
                continue;
            }
            if (linea.startsWith("#")) {
                String enc = linea.toUpperCase();
                if (enc.contains("GLOBAL")) {
                    seccionActual = "GLOBAL";
                } else if (enc.contains("COMPUTADORES")) {
                    seccionActual = "COMPUTADORES";
                } else if (enc.contains("PROCESOS")) {
                    seccionActual = "PROCESOS";
                }
                continue;
            }
            if (linea.startsWith("duracionCicloMs") || linea.startsWith("id,nombre")) {
                continue;
            }

            String[] partes = linea.split(",", -1);
            for (int i = 0; i < partes.length; i++) {
                partes[i] = desescaparCSV(partes[i].trim());
            }

            try {
                switch (seccionActual) {
                    case "GLOBAL" -> {
                        if (partes.length >= 6) {
                            config.setDuracionCicloMs(Long.parseLong(partes[0]));
                            config.setTotalBloquesDisco(Integer.parseInt(partes[1]));
                            config.setTamanoSwap(Integer.parseInt(partes[2]));
                            config.setCabezalInicial(Integer.parseInt(partes[3]));
                            config.setVelocidadCabezal(Integer.parseInt(partes[4]));
                            config.setPoliticaDiscoInicial(enums.PoliticaDisco.desdeTexto(partes[5]));
                        }
                    }
                    case "COMPUTADORES" -> {
                        if (partes.length >= 5) {
                            config.getComputadores().agregar(new ConfigComputador(
                                    Integer.parseInt(partes[0]),
                                    partes[1],
                                    Long.parseLong(partes[2]),
                                    parsearPolitica(partes[3]),
                                    Integer.parseInt(partes[4])));
                        }
                    }
                    case "PROCESOS" -> {
                        if (partes.length >= 10) {
                            TipoOperacionES op = (partes.length >= 11 && !partes[10].isEmpty())
                                    ? TipoOperacionES.desdeTexto(partes[10]) : null;
                            String ruta = (partes.length >= 12) ? partes[11] : "";
                            int tamArchivo = (partes.length >= 13 && !partes[12].isEmpty())
                                    ? Integer.parseInt(partes[12]) : 0;
                            config.getProcesos().agregar(new ConfigProceso(
                                    Integer.parseInt(partes[0]),
                                    partes[1],
                                    parsearTipoProceso(partes[2]),
                                    Integer.parseInt(partes[3]),
                                    Integer.parseInt(partes[4]),
                                    Long.parseLong(partes[5]),
                                    Integer.parseInt(partes[6]),
                                    Long.parseLong(partes[7]),
                                    Integer.parseInt(partes[8]),
                                    Integer.parseInt(partes[9]),
                                    op, ruta, tamArchivo, "", enums.ModoUsuario.ADMINISTRADOR, "admin"));
                        }
                    }
                    default -> { }
                }
            } catch (Exception e) {
                throw new IOException("Error en sección [" + seccionActual + "]: '" + linea + "' -> " + e.getMessage(), e);
            }
        }
        return config;
    }

    // =========================================================================
    // UTILIDADES DE CONSTRUCCIÓN Y PARSEO
    // =========================================================================

    /** Construye un {@link PCB} a partir de {@link ConfigProceso}. */
    public static PCB crearPCB(ConfigProceso cp, long cicloActual) {
        if (cp == null) {
            throw new IllegalArgumentException("El proceso de configuración no puede ser nulo.");
        }
        ListaEnlazada<Instruccion> programa = new ListaEnlazada<>();
        TipoProceso tipo = cp.getTipo();
        int totalInstr = Math.max(1, cp.getInstrucciones());

        if (cp.getOperacion() != null && cp.getOperacion().esOperacionArchivo()) {
            programa.agregar(new Instruccion(TipoInstruccion.OPERACION_ES, cp.getOperacion().getCodigoJson()));
            for (int i = 1; i < totalInstr; i++) {
                programa.agregar(new Instruccion(TipoInstruccion.CPU));
            }
            PCB pcb = new PCB(cp.getNombre(), cp.getComputadorAsignado(), TipoProceso.IO_BOUND,
                    cp.getPrioridad(), cp.getMemoria(), cp.getDeadline(),
                    "", 0, 0, programa, cicloActual);
            pcb.setOperacion(cp.getOperacion());
            pcb.setRutaObjetivo(cp.getRutaObjetivo());
            pcb.setTamanoArchivoBloques(cp.getTamanoArchivoBloques());
            pcb.setNuevoNombre(cp.getNuevoNombre());
            pcb.setModoSolicitante(cp.getModoSolicitante());
            pcb.setUsuario(cp.getUsuario());
            return pcb;
        }

        int periodoES = (tipo == TipoProceso.IO_BOUND) ? cp.getPeriodoES() : 0;
        for (int i = 1; i <= totalInstr; i++) {
            boolean esES = periodoES > 0 && (i % periodoES == 0);
            programa.agregar(new Instruccion(esES ? TipoInstruccion.READ_IO : TipoInstruccion.CPU,
                    esES ? String.valueOf(Math.max(1, cp.getCiclosPorAcceso())) : ""));
        }
        return new PCB(cp.getNombre(), cp.getComputadorAsignado(), tipo,
                cp.getPrioridad(), cp.getMemoria(), cp.getDeadline(),
                "", 0, cp.getCiclosPorAcceso(), programa, cicloActual);
    }

    public static PoliticaPlanificacion parsearPolitica(String texto) {
        if (texto == null) {
            return PoliticaPlanificacion.FCFS;
        }
        String t = texto.trim().toUpperCase();
        if (t.contains("ROUND") || t.contains("RR")) {
            return PoliticaPlanificacion.ROUND_ROBIN;
        }
        if (t.contains("DEADLINE") || t.contains("EDF")) {
            return PoliticaPlanificacion.EDF;
        }
        if (t.contains("PRIORIDAD") || t.contains("PRIORITY")) {
            return PoliticaPlanificacion.PRIORIDAD;
        }
        return PoliticaPlanificacion.FCFS;
    }

    private static TipoProceso parsearTipoProceso(String texto) {
        if (texto == null) {
            return TipoProceso.CPU_BOUND;
        }
        String t = texto.trim().toUpperCase();
        if (t.contains("IO") || t.contains("I/O")) {
            return TipoProceso.IO_BOUND;
        }
        return TipoProceso.CPU_BOUND;
    }

    private static FormatoPersistencia detectarFormatoPorExtension(String nombreArchivo) {
        if (nombreArchivo != null && nombreArchivo.toLowerCase().endsWith(".csv")) {
            return FormatoPersistencia.CSV;
        }
        return FormatoPersistencia.JSON;
    }

    private static void asegurarDirectorioPadre(File archivo) {
        File dirPadre = archivo.getParentFile();
        if (dirPadre != null && !dirPadre.exists()) {
            dirPadre.mkdirs();
        }
    }

    private static String escaparCSV(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String desescaparCSV(String s) {
        if (s == null) {
            return "";
        }
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1).replace("\"\"", "\"");
        }
        return s;
    }
}
