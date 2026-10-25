package config;

import enums.PoliticaPlanificacion;
import enums.TipoProceso;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import modelo.PCB;
import nucleo.Computador;
import planificacion.AlgoritmoPlanificacion;
import planificacion.FabricaPoliticas;
import simulacion.Simulador;
import sincronizacion.Buffer;

/**
 * Objeto central que consolida todos los parámetros exportables e importables de ÁvilaOS:
 * duración del ciclo, latencia de red, computadores, buffers y procesos iniciales.
 *
 * <p>Utiliza exclusivamente {@link ListaEnlazada} propia para almacenar sus colecciones,
 * cumpliendo con la prohibición del uso de {@code java.util} collections.</p>
 */
public class ConfiguracionSimulacion {

    private long duracionCicloMs;
    private int latenciaRedCiclos;
    private ListaEnlazada<ConfigComputador> computadores;
    private ListaEnlazada<ConfigBuffer> buffers;
    private ListaEnlazada<ConfigProceso> procesos;

    public ConfiguracionSimulacion() {
        this(500, 3, new ListaEnlazada<>(), new ListaEnlazada<>(), new ListaEnlazada<>());
    }

    public ConfiguracionSimulacion(long duracionCicloMs, int latenciaRedCiclos,
                                  ListaEnlazada<ConfigComputador> computadores,
                                  ListaEnlazada<ConfigBuffer> buffers,
                                  ListaEnlazada<ConfigProceso> procesos) {
        this.duracionCicloMs = duracionCicloMs <= 0 ? 500 : duracionCicloMs;
        this.latenciaRedCiclos = latenciaRedCiclos < 0 ? 0 : latenciaRedCiclos;
        this.computadores = (computadores != null) ? computadores : new ListaEnlazada<>();
        this.buffers = (buffers != null) ? buffers : new ListaEnlazada<>();
        this.procesos = (procesos != null) ? procesos : new ListaEnlazada<>();
    }

    public long getDuracionCicloMs() {
        return duracionCicloMs;
    }

    public void setDuracionCicloMs(long duracionCicloMs) {
        this.duracionCicloMs = duracionCicloMs;
    }

    public int getLatenciaRedCiclos() {
        return latenciaRedCiclos;
    }

    public void setLatenciaRedCiclos(int latenciaRedCiclos) {
        this.latenciaRedCiclos = latenciaRedCiclos;
    }

    public ListaEnlazada<ConfigComputador> getComputadores() {
        return computadores;
    }

    public void setComputadores(ListaEnlazada<ConfigComputador> computadores) {
        this.computadores = (computadores != null) ? computadores : new ListaEnlazada<>();
    }

    public ListaEnlazada<ConfigBuffer> getBuffers() {
        return buffers;
    }

    public void setBuffers(ListaEnlazada<ConfigBuffer> buffers) {
        this.buffers = (buffers != null) ? buffers : new ListaEnlazada<>();
    }

    public ListaEnlazada<ConfigProceso> getProcesos() {
        return procesos;
    }

    public void setProcesos(ListaEnlazada<ConfigProceso> procesos) {
        this.procesos = (procesos != null) ? procesos : new ListaEnlazada<>();
    }

    public ConfigComputador buscarComputadorPorId(int id) {
        Iterador<ConfigComputador> it = computadores.iterador();
        while (it.haySiguiente()) {
            ConfigComputador c = it.siguiente();
            if (c.getId() == id) {
                return c;
            }
        }
        return null;
    }

    public ConfigBuffer buscarBufferPorId(String id) {
        if (id == null) {
            return null;
        }
        Iterador<ConfigBuffer> it = buffers.iterador();
        while (it.haySiguiente()) {
            ConfigBuffer b = it.siguiente();
            if (id.equalsIgnoreCase(b.getId())) {
                return b;
            }
        }
        return null;
    }

    /**
     * Valida integralmente la configuración y retorna una lista de mensajes de error.
     * Si la lista está vacía, la configuración es completamente válida.
     */
    public ListaEnlazada<String> validar() {
        ListaEnlazada<String> errores = new ListaEnlazada<>();

        if (duracionCicloMs <= 0) {
            errores.agregar("La duración del ciclo debe ser mayor a 0 ms (actual: " + duracionCicloMs + ").");
        }
        if (latenciaRedCiclos < 0) {
            errores.agregar("La latencia de red no puede ser negativa (actual: " + latenciaRedCiclos + ").");
        }

        // Validación de computadores
        if (computadores == null || computadores.getTamano() < 2) {
            errores.agregar("El sistema requiere al menos 2 computadores (actual: "
                    + (computadores == null ? 0 : computadores.getTamano()) + ").");
        } else {
            Iterador<ConfigComputador> itPc1 = computadores.iterador();
            int idx1 = 0;
            while (itPc1.haySiguiente()) {
                ConfigComputador pc1 = itPc1.siguiente();
                idx1++;
                if (pc1.getId() <= 0) {
                    errores.agregar("El computador #" + idx1 + " tiene ID inválido (" + pc1.getId() + "). Debe ser > 0.");
                }
                if (pc1.getNombre() == null || pc1.getNombre().trim().isEmpty()) {
                    errores.agregar("El computador #" + idx1 + " tiene un nombre vacío.");
                }
                if (pc1.getMemoriaTotal() <= 0) {
                    errores.agregar("El computador '" + pc1.getNombre() + "' debe tener memoria RAM > 0 KB.");
                }
                if (pc1.getPolitica() == PoliticaPlanificacion.ROUND_ROBIN && pc1.getQuantum() <= 0) {
                    errores.agregar("El computador '" + pc1.getNombre() + "' usa Round Robin y debe tener quantum > 0.");
                }

                // Unicidad de ID
                Iterador<ConfigComputador> itPc2 = computadores.iterador();
                int idx2 = 0;
                while (itPc2.haySiguiente()) {
                    ConfigComputador pc2 = itPc2.siguiente();
                    idx2++;
                    if (idx2 > idx1 && pc1.getId() == pc2.getId()) {
                        errores.agregar("ID de computador duplicado (" + pc1.getId() + ") entre '"
                                + pc1.getNombre() + "' y '" + pc2.getNombre() + "'.");
                    }
                }
            }
        }

        // Validación de buffers
        if (buffers != null) {
            Iterador<ConfigBuffer> itBuf1 = buffers.iterador();
            int bIdx1 = 0;
            while (itBuf1.haySiguiente()) {
                ConfigBuffer b1 = itBuf1.siguiente();
                bIdx1++;
                if (b1.getId() == null || b1.getId().trim().isEmpty()) {
                    errores.agregar("El buffer #" + bIdx1 + " tiene un ID vacío.");
                }
                if (b1.getCapacidad() <= 0) {
                    errores.agregar("El buffer '" + b1.getId() + "' debe tener capacidad > 0.");
                }
                if (buscarComputadorPorId(b1.getIdComputadorAnfitrion()) == null) {
                    errores.agregar("El buffer '" + b1.getId() + "' asigna un computador anfitrión inexistente (ID: "
                            + b1.getIdComputadorAnfitrion() + ").");
                }

                // Unicidad de ID de buffer
                Iterador<ConfigBuffer> itBuf2 = buffers.iterador();
                int bIdx2 = 0;
                while (itBuf2.haySiguiente()) {
                    ConfigBuffer b2 = itBuf2.siguiente();
                    bIdx2++;
                    if (bIdx2 > bIdx1 && b1.getId().equalsIgnoreCase(b2.getId())) {
                        errores.agregar("ID de buffer duplicado ('" + b1.getId() + "').");
                    }
                }
            }
        }

        // Validación de procesos
        if (procesos != null) {
            Iterador<ConfigProceso> itP = procesos.iterador();
            int pIdx = 0;
            while (itP.haySiguiente()) {
                ConfigProceso p = itP.siguiente();
                pIdx++;
                String nom = p.getNombre() == null ? ("#" + pIdx) : p.getNombre();
                if (p.getNombre() == null || p.getNombre().trim().isEmpty()) {
                    errores.agregar("El proceso #" + pIdx + " tiene un nombre vacío.");
                }
                ConfigComputador pcAsig = buscarComputadorPorId(p.getComputadorAsignado());
                if (pcAsig == null) {
                    errores.agregar("El proceso '" + nom + "' asigna un computador inexistente (ID: "
                            + p.getComputadorAsignado() + ").");
                } else if (p.getMemoria() > pcAsig.getMemoriaTotal()) {
                    errores.agregar("El proceso '" + nom + "' requiere " + p.getMemoria() + " KB de RAM, superando la RAM de "
                            + pcAsig.getNombre() + " (" + pcAsig.getMemoriaTotal() + " KB).");
                }

                if (p.getInstrucciones() <= 0) {
                    errores.agregar("El proceso '" + nom + "' debe tener al menos 1 instrucción.");
                }
                if (p.getMemoria() <= 0) {
                    errores.agregar("El proceso '" + nom + "' debe requerir memoria > 0 KB.");
                }
                if (p.getPrioridad() <= 0) {
                    errores.agregar("El proceso '" + nom + "' debe tener prioridad >= 1.");
                }
                if (p.getDeadline() <= 0) {
                    errores.agregar("El proceso '" + nom + "' debe tener un deadline > 0.");
                }

                if (p.getTipo() == TipoProceso.IO_BOUND) {
                    if (p.getPeriodoES() <= 0 || p.getPeriodoES() > p.getInstrucciones()) {
                        errores.agregar("El proceso I/O '" + nom + "' tiene un periodo de E/S inválido ("
                                + p.getPeriodoES() + "). Debe estar entre 1 y " + p.getInstrucciones() + ".");
                    }
                } else if (p.getTipo() == TipoProceso.PRODUCTOR || p.getTipo() == TipoProceso.CONSUMIDOR) {
                    if (p.getIdBufferTarget() == null || p.getIdBufferTarget().trim().isEmpty()) {
                        errores.agregar("El proceso " + p.getTipo() + " '" + nom + "' no tiene buffer asignado.");
                    } else if (buscarBufferPorId(p.getIdBufferTarget()) == null) {
                        errores.agregar("El proceso " + p.getTipo() + " '" + nom + "' asigna un buffer inexistente ('"
                                + p.getIdBufferTarget() + "').");
                    }
                    if (p.getCiclosPorAcceso() <= 0) {
                        errores.agregar("El proceso " + p.getTipo() + " '" + nom + "' debe tener ciclos por acceso >= 1.");
                    }
                    if (p.getElementosRequeridos() <= 0) {
                        errores.agregar("El proceso " + p.getTipo() + " '" + nom + "' debe tener elementos requeridos >= 1.");
                    }
                }
            }
        }

        return errores;
    }

    /**
     * Valida de forma estricta. Si encuentra inconsistencias, lanza {@link IllegalArgumentException}.
     */
    public void validarEstricto() {
        ListaEnlazada<String> errs = validar();
        if (!errs.estaVacia()) {
            StringBuilder sb = new StringBuilder("Configuración inválida:\n");
            Iterador<String> it = errs.iterador();
            while (it.haySiguiente()) {
                sb.append(" • ").append(it.siguiente()).append("\n");
            }
            throw new IllegalArgumentException(sb.toString().trim());
        }
    }

    /**
     * Construye un {@link Simulador} completo a partir de esta configuración.
     * Carga todos los computadores, buffers y procesos iniciales usando exclusivamente
     * estructuras propias ({@link ListaEnlazada}).
     */
    public Simulador aSimulador() {
        validarEstricto();

        Simulador sim = new Simulador();
        sim.setDuracionCicloMs(duracionCicloMs);
        sim.setLatenciaRedCiclos(latenciaRedCiclos);

        // 1. Registrar computadores
        Iterador<ConfigComputador> itPc = computadores.iterador();
        while (itPc.haySiguiente()) {
            ConfigComputador c = itPc.siguiente();
            AlgoritmoPlanificacion alg = (c.getPolitica() == PoliticaPlanificacion.ROUND_ROBIN)
                    ? FabricaPoliticas.crear(PoliticaPlanificacion.ROUND_ROBIN, c.getQuantum())
                    : FabricaPoliticas.crear(c.getPolitica());
            Computador comp = new Computador(c.getId(), c.getNombre(), c.getMemoriaTotal(), alg);
            sim.registrarComputador(comp);
        }

        // 2. Registrar buffers
        Iterador<ConfigBuffer> itBuf = buffers.iterador();
        while (itBuf.haySiguiente()) {
            ConfigBuffer b = itBuf.siguiente();
            Computador anfitrion = sim.buscarComputador(b.getIdComputadorAnfitrion());
            Buffer buf = new Buffer(b.getId(), b.getCapacidad(), b.getIdComputadorAnfitrion(),
                    b.getTamanoElemento(), anfitrion);
            sim.registrarBuffer(buf);
        }

        // 3. Cargar procesos iniciales
        Iterador<ConfigProceso> itProc = procesos.iterador();
        while (itProc.haySiguiente()) {
            ConfigProceso cp = itProc.siguiente();
            PCB pcb = GestorPersistencia.crearPCB(cp, sim.getCicloActual());
            sim.cargarProceso(pcb);
        }

        return sim;
    }

    /**
     * Extrae un objeto {@link ConfiguracionSimulacion} a partir de un {@link Simulador} en memoria.
     */
    public static ConfiguracionSimulacion desdeSimulador(Simulador sim) {
        if (sim == null) {
            return crearPorDefecto();
        }

        ConfiguracionSimulacion config = new ConfiguracionSimulacion();
        config.setDuracionCicloMs(sim.getDuracionCicloMs());
        config.setLatenciaRedCiclos(sim.getLatenciaRedCiclos());

        // Computadores
        Iterador<Computador> itPc = sim.iteradorComputadores();
        while (itPc.haySiguiente()) {
            Computador c = itPc.siguiente();
            PoliticaPlanificacion pol = PoliticaPlanificacion.FCFS;
            String nomPol = c.getPlanificador().getPolitica().getNombre().toLowerCase();
            if (nomPol.contains("round robin")) {
                pol = PoliticaPlanificacion.ROUND_ROBIN;
            } else if (nomPol.contains("deadline") || nomPol.contains("edf")) {
                pol = PoliticaPlanificacion.EDF;
            } else if (nomPol.contains("prioridad")) {
                pol = PoliticaPlanificacion.PRIORIDAD;
            }
            int quantum = c.getPlanificador().getQuantum();
            config.getComputadores().agregar(new ConfigComputador(c.getId(), c.getNombre(),
                    c.getMemoriaTotal(), pol, quantum));
        }

        // Buffers
        Iterador<Buffer> itBuf = sim.iteradorBuffers();
        while (itBuf.haySiguiente()) {
            Buffer b = itBuf.siguiente();
            config.getBuffers().agregar(new ConfigBuffer(b.getId(), b.getCapacidad(),
                    b.getIdComputadorAnfitrion(), 4));
        }

        return config;
    }

    /**
     * Crea una configuración por defecto representativa para inicializar la GUI o simulaciones nuevas.
     */
    public static ConfiguracionSimulacion crearPorDefecto() {
        ConfiguracionSimulacion config = new ConfiguracionSimulacion();
        config.setDuracionCicloMs(500);
        config.setLatenciaRedCiclos(3);

        // 3 Computadores iniciales
        config.getComputadores().agregar(new ConfigComputador(1, "PC-1", 1024, PoliticaPlanificacion.FCFS, 2));
        config.getComputadores().agregar(new ConfigComputador(2, "PC-2", 1024, PoliticaPlanificacion.EDF, 2));
        config.getComputadores().agregar(new ConfigComputador(3, "PC-3", 1024, PoliticaPlanificacion.PRIORIDAD, 2));

        // 1 Buffer inicial en PC-1
        config.getBuffers().agregar(new ConfigBuffer("BUF-1", 5, 1, 4));

        // Carga inicial de procesos
        config.getProcesos().agregar(new ConfigProceso(1, "P1", TipoProceso.CPU_BOUND, 1, 12, 500, 1, 60, 0, "", 1, 1));
        config.getProcesos().agregar(new ConfigProceso(2, "P2", TipoProceso.CPU_BOUND, 1, 15, 500, 1, 40, 0, "", 1, 1));
        config.getProcesos().agregar(new ConfigProceso(3, "P3", TipoProceso.IO_BOUND, 2, 14, 600, 1, 70, 3, "", 1, 1));
        config.getProcesos().agregar(new ConfigProceso(4, "Prod-1", TipoProceso.PRODUCTOR, 2, 10, 200, 2, 80, 0, "BUF-1", 1, 2));
        config.getProcesos().agregar(new ConfigProceso(5, "Cons-1", TipoProceso.CONSUMIDOR, 3, 10, 200, 2, 80, 0, "BUF-1", 1, 2));

        return config;
    }
}
