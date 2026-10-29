package config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import enums.PoliticaPlanificacion;
import enums.TipoInstruccion;
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

/**
 * Gestor de persistencia para guardar y cargar configuraciones completas de simulación
 * en formatos JSON y CSV.
 *
 * <p>Restricción arquitectónica: Mapea los datos exclusivamente hacia estructuras propias
 * ({@link ListaEnlazada}), sin utilizar ninguna colección de {@code java.util}.</p>
 */
public final class GestorPersistencia {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeHierarchyAdapter(ListaEnlazada.class, new AdaptadorListaEnlazada())
            .setPrettyPrinting()
            .create();

    private GestorPersistencia() {
        // Clase de utilidades estáticas.
    }

    /**
     * Guarda la configuración de simulación en el archivo especificado utilizando el formato indicado.
     *
     * @param config configuración a guardar.
     * @param archivo archivo de destino.
     * @param formato formato de serialización (JSON o CSV).
     * @throws IOException si ocurre un error de entrada/salida.
     */
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

        File dirPadre = archivo.getParentFile();
        if (dirPadre != null && !dirPadre.exists()) {
            dirPadre.mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(archivo), StandardCharsets.UTF_8))) {
            if (formato == FormatoPersistencia.JSON) {
                guardarJSON(config, writer);
            } else {
                guardarCSV(config, writer);
            }
        }
    }

    /**
     * Carga y valida una configuración de simulación desde el archivo especificado.
     *
     * @param archivo archivo a leer.
     * @param formato formato de serialización (JSON o CSV). Si es nulo, se detecta por extensión.
     * @return objeto {@link ConfiguracionSimulacion} cargado y validado.
     * @throws IOException si ocurre un error de lectura o parseo.
     */
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
                config = cargarJSON(reader);
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

    /** Serializa la configuración como una cadena de texto en formato JSON o CSV. */
    public static String guardarComoTexto(ConfiguracionSimulacion config, FormatoPersistencia formato) throws IOException {
        StringWriter sw = new StringWriter();
        if (formato == FormatoPersistencia.JSON) {
            guardarJSON(config, sw);
        } else {
            guardarCSV(config, sw);
        }
        return sw.toString();
    }

    /** Deserializa la configuración a partir de una cadena de texto en formato JSON o CSV. */
    public static ConfiguracionSimulacion cargarDesdeTexto(String contenido, FormatoPersistencia formato) throws IOException {
        if (contenido == null || contenido.trim().isEmpty()) {
            throw new IOException("El contenido está vacío.");
        }
        StringReader sr = new StringReader(contenido);
        ConfiguracionSimulacion config = (formato == FormatoPersistencia.JSON) ? cargarJSON(sr) : cargarCSV(sr);
        if (config != null) {
            config.validarEstricto();
        }
        return config;
    }

    // =========================================================================
    // PERSISTENCIA JSON
    // =========================================================================

    private static void guardarJSON(ConfiguracionSimulacion config, Writer writer) throws IOException {
        GSON.toJson(config, writer);
        writer.flush();
    }

    private static ConfiguracionSimulacion cargarJSON(Reader reader) {
        return GSON.fromJson(reader, ConfiguracionSimulacion.class);
    }

    // =========================================================================
    // PERSISTENCIA CSV
    // =========================================================================

    private static void guardarCSV(ConfiguracionSimulacion config, Writer writer) throws IOException {
        BufferedWriter bw = (writer instanceof BufferedWriter) ? (BufferedWriter) writer : new BufferedWriter(writer);

        bw.write("# CONFIGURACION_AVILA_OS");
        bw.newLine();
        bw.write("# PARAMETROS_GLOBALES");
        bw.newLine();
        bw.write("duracionCicloMs,latenciaRedCiclos");
        bw.newLine();
        bw.write(config.getDuracionCicloMs() + "," + config.getLatenciaRedCiclos());
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

        bw.write("# BUFFERS");
        bw.newLine();
        bw.write("id,capacidad,idComputadorAnfitrion,tamanoElemento");
        bw.newLine();
        Iterador<ConfigBuffer> itBuf = config.getBuffers().iterador();
        while (itBuf.haySiguiente()) {
            ConfigBuffer buf = itBuf.siguiente();
            bw.write(escaparCSV(buf.getId()) + "," + buf.getCapacidad() + "," + buf.getIdComputadorAnfitrion() + ","
                    + buf.getTamanoElemento());
            bw.newLine();
        }
        bw.newLine();

        bw.write("# PROCESOS");
        bw.newLine();
        bw.write("id,nombre,tipo,computadorAsignado,instrucciones,memoria,prioridad,deadline,periodoES,idBufferTarget,ciclosPorAcceso,elementosRequeridos");
        bw.newLine();
        Iterador<ConfigProceso> itProc = config.getProcesos().iterador();
        while (itProc.haySiguiente()) {
            ConfigProceso p = itProc.siguiente();
            bw.write(p.getId() + "," + escaparCSV(p.getNombre()) + "," + p.getTipo().name() + ","
                    + p.getComputadorAsignado() + "," + p.getInstrucciones() + "," + p.getMemoria() + ","
                    + p.getPrioridad() + "," + p.getDeadline() + "," + p.getPeriodoES() + ","
                    + escaparCSV(p.getIdBufferTarget()) + "," + p.getCiclosPorAcceso() + ","
                    + p.getElementosRequeridos());
            bw.newLine();
        }
        bw.flush();
    }

    private static ConfiguracionSimulacion cargarCSV(Reader reader) throws IOException {
        BufferedReader br = (reader instanceof BufferedReader) ? (BufferedReader) reader : new BufferedReader(reader);
        ConfiguracionSimulacion config = new ConfiguracionSimulacion();
        config.setComputadores(new ListaEnlazada<>());
        config.setBuffers(new ListaEnlazada<>());
        config.setProcesos(new ListaEnlazada<>());

        String linea;
        String seccionActual = "";

        while ((linea = br.readLine()) != null) {
            linea = linea.trim();
            if (linea.isEmpty()) {
                continue;
            }

            if (linea.startsWith("#")) {
                String encabezado = linea.toUpperCase();
                if (encabezado.contains("PARAMETROS_GLOBALES") || encabezado.contains("GLOBAL")) {
                    seccionActual = "GLOBAL";
                } else if (encabezado.contains("COMPUTADORES")) {
                    seccionActual = "COMPUTADORES";
                } else if (encabezado.contains("BUFFERS")) {
                    seccionActual = "BUFFERS";
                } else if (encabezado.contains("PROCESOS")) {
                    seccionActual = "PROCESOS";
                }
                continue;
            }

            // Omitir cabeceras de columnas
            if (linea.startsWith("duracionCicloMs") || linea.startsWith("id,nombre")
                    || linea.startsWith("id,capacidad") || linea.startsWith("id,nombre,tipo")) {
                continue;
            }

            String[] partes = linea.split(",", -1);
            for (int i = 0; i < partes.length; i++) {
                partes[i] = desescaparCSV(partes[i].trim());
            }

            try {
                switch (seccionActual) {
                    case "GLOBAL":
                        if (partes.length >= 2) {
                            config.setDuracionCicloMs(Long.parseLong(partes[0]));
                            config.setLatenciaRedCiclos(Integer.parseInt(partes[1]));
                        }
                        break;
                    case "COMPUTADORES":
                        if (partes.length >= 5) {
                            int id = Integer.parseInt(partes[0]);
                            String nombre = partes[1];
                            long ram = Long.parseLong(partes[2]);
                            PoliticaPlanificacion pol = parsearPolitica(partes[3]);
                            int quantum = Integer.parseInt(partes[4]);
                            config.getComputadores().agregar(new ConfigComputador(id, nombre, ram, pol, quantum));
                        }
                        break;
                    case "BUFFERS":
                        if (partes.length >= 3) {
                            String idBuf = partes[0];
                            int cap = Integer.parseInt(partes[1]);
                            int anf = Integer.parseInt(partes[2]);
                            long tam = partes.length >= 4 && !partes[3].isEmpty() ? Long.parseLong(partes[3]) : 4;
                            config.getBuffers().agregar(new ConfigBuffer(idBuf, cap, anf, tam));
                        }
                        break;
                    case "PROCESOS":
                        if (partes.length >= 12) {
                            int idProc = Integer.parseInt(partes[0]);
                            String nomProc = partes[1];
                            TipoProceso tipoProc = parsearTipoProceso(partes[2]);
                            int compAsig = Integer.parseInt(partes[3]);
                            int instr = Integer.parseInt(partes[4]);
                            long mem = Long.parseLong(partes[5]);
                            int prio = Integer.parseInt(partes[6]);
                            long dl = Long.parseLong(partes[7]);
                            int per = Integer.parseInt(partes[8]);
                            String bufTarget = partes[9];
                            int ciclosAcceso = Integer.parseInt(partes[10]);
                            int elemReq = Integer.parseInt(partes[11]);
                            config.getProcesos().agregar(new ConfigProceso(idProc, nomProc, tipoProc, compAsig,
                                    instr, mem, prio, dl, per, bufTarget, ciclosAcceso, elemReq));
                        }
                        break;
                    default:
                        // Línea fuera de sección identificada, se ignora.
                        break;
                }
            } catch (Exception e) {
                throw new IOException("Error procesando línea CSV en sección [" + seccionActual + "]: '" + linea + "' -> " + e.getMessage(), e);
            }
        }

        return config;
    }

    // =========================================================================
    // UTILIDADES DE CONSTRUCCIÓN Y PARSEO
    // =========================================================================

    /**
     * Construye una instancia de {@link PCB} a partir de la configuración de un proceso.
     * Carga el programa de instrucciones utilizando exclusivamente {@link ListaEnlazada}.
     */
    public static PCB crearPCB(ConfigProceso cp, long cicloActual) {
        if (cp == null) {
            throw new IllegalArgumentException("El proceso de configuración no puede ser nulo.");
        }

        ListaEnlazada<Instruccion> programa = new ListaEnlazada<>();
        TipoProceso tipo = cp.getTipo();

        if (tipo == TipoProceso.PRODUCTOR) {
            int req = Math.max(1, cp.getElementosRequeridos());
            for (int i = 0; i < req; i++) {
                programa.agregar(new Instruccion(TipoInstruccion.PRODUCIR));
            }
        } else if (tipo == TipoProceso.CONSUMIDOR) {
            int req = Math.max(1, cp.getElementosRequeridos());
            for (int i = 0; i < req; i++) {
                programa.agregar(new Instruccion(TipoInstruccion.CONSUMIR));
            }
        } else {
            int totalInstr = Math.max(1, cp.getInstrucciones());
            int periodoES = (tipo == TipoProceso.IO_BOUND) ? cp.getPeriodoES() : 0;
            for (int i = 1; i <= totalInstr; i++) {
                boolean esES = periodoES > 0 && (i % periodoES == 0);
                programa.agregar(new Instruccion(esES ? TipoInstruccion.READ_IO : TipoInstruccion.CPU,
                        esES ? String.valueOf(Math.max(1, cp.getCiclosPorAcceso())) : ""));
            }
        }

        return new PCB(cp.getNombre(), cp.getComputadorAsignado(), tipo,
                cp.getPrioridad(), cp.getMemoria(), cp.getDeadline(),
                cp.getIdBufferTarget(), cp.getElementosRequeridos(), cp.getCiclosPorAcceso(),
                programa, cicloActual);
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
        if (t.contains("PRIORIDAD")) {
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
        if (t.contains("PROD")) {
            return TipoProceso.PRODUCTOR;
        }
        if (t.contains("CONS")) {
            return TipoProceso.CONSUMIDOR;
        }
        return TipoProceso.CPU_BOUND;
    }

    private static FormatoPersistencia detectarFormatoPorExtension(String nombreArchivo) {
        if (nombreArchivo != null && nombreArchivo.toLowerCase().endsWith(".csv")) {
            return FormatoPersistencia.CSV;
        }
        return FormatoPersistencia.JSON;
    }

    private static String escaparCSV(String valor) {
        if (valor == null) {
            return "";
        }
        if (valor.contains(",") || valor.contains("\"") || valor.contains("\n")) {
            return "\"" + valor.replace("\"", "\"\"") + "\"";
        }
        return valor;
    }

    private static String desescaparCSV(String valor) {
        if (valor == null) {
            return "";
        }
        if (valor.startsWith("\"") && valor.endsWith("\"") && valor.length() >= 2) {
            valor = valor.substring(1, valor.length() - 1);
            return valor.replace("\"\"", "\"");
        }
        return valor;
    }
}
