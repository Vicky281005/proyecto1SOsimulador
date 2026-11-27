package config;

import enums.ModoUsuario;
import enums.PoliticaDisco;
import enums.PoliticaPlanificacion;
import enums.TipoInstruccion;
import enums.TipoOperacionES;
import enums.TipoProceso;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import modelo.Instruccion;
import modelo.PCB;

/**
 * Modelo de configuración del <b>JSON 2 (Procesos y Configuración Global/Computador)</b>
 * para el Proyecto 2 de ÁvilaOS.
 *
 * <p>Corresponde exactamente a la estructura especificada en el enunciado:
 * {@code globalConfig} ({@code cycleDurationMs}, {@code swapSizeBlocks}, {@code initialDiskPolicy}),
 * {@code computer} ({@code ramCapacity}, {@code policy}, {@code quantum}) y {@code processes}
 * ({@code name}, {@code memory}, {@code deadline}, {@code instructions}, {@code targetPath},
 * {@code operation}, {@code fileSizeBlocks}, entre otros campos opcionales).</p>
 */
public class ConfigProcesosDisco {

    public static class GlobalConfig {
        private long cycleDurationMs;
        private int swapSizeBlocks;
        private String initialDiskPolicy;

        public GlobalConfig() {
            this(300, 100, "FIFO");
        }

        public GlobalConfig(long cycleDurationMs, int swapSizeBlocks, String initialDiskPolicy) {
            this.cycleDurationMs = cycleDurationMs;
            this.swapSizeBlocks = swapSizeBlocks;
            this.initialDiskPolicy = initialDiskPolicy;
        }

        public long getCycleDurationMs() {
            return cycleDurationMs;
        }

        public void setCycleDurationMs(long cycleDurationMs) {
            this.cycleDurationMs = cycleDurationMs;
        }

        public int getSwapSizeBlocks() {
            return swapSizeBlocks;
        }

        public void setSwapSizeBlocks(int swapSizeBlocks) {
            this.swapSizeBlocks = swapSizeBlocks;
        }

        public String getInitialDiskPolicy() {
            return initialDiskPolicy;
        }

        public void setInitialDiskPolicy(String initialDiskPolicy) {
            this.initialDiskPolicy = initialDiskPolicy;
        }
    }

    public static class ComputerConfig {
        private long ramCapacity;
        private String policy;
        private int quantum;

        public ComputerConfig() {
            this(1024, "FCFS", 0);
        }

        public ComputerConfig(long ramCapacity, String policy, int quantum) {
            this.ramCapacity = ramCapacity;
            this.policy = policy;
            this.quantum = quantum;
        }

        public long getRamCapacity() {
            return ramCapacity;
        }

        public void setRamCapacity(long ramCapacity) {
            this.ramCapacity = ramCapacity;
        }

        public String getPolicy() {
            return policy;
        }

        public void setPolicy(String policy) {
            this.policy = policy;
        }

        public int getQuantum() {
            return quantum;
        }

        public void setQuantum(int quantum) {
            this.quantum = quantum;
        }
    }

    public static class ProcesoJson {
        private String name;
        private long memory;
        private long deadline;
        private int instructions;
        private String targetPath;
        private String operation;
        private Integer fileSizeBlocks;
        private String newName;
        private Integer priority;
        private String mode;
        private String owner;

        public ProcesoJson() {
        }

        public ProcesoJson(String name, long memory, long deadline, int instructions,
                           String targetPath, String operation, Integer fileSizeBlocks) {
            this.name = name;
            this.memory = memory;
            this.deadline = deadline;
            this.instructions = instructions;
            this.targetPath = targetPath;
            this.operation = operation;
            this.fileSizeBlocks = fileSizeBlocks;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public long getMemory() { return memory; }
        public void setMemory(long memory) { this.memory = memory; }

        public long getDeadline() { return deadline; }
        public void setDeadline(long deadline) { this.deadline = deadline; }

        public int getInstructions() { return instructions; }
        public void setInstructions(int instructions) { this.instructions = instructions; }

        public String getTargetPath() { return targetPath; }
        public void setTargetPath(String targetPath) { this.targetPath = targetPath; }

        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }

        public Integer getFileSizeBlocks() { return fileSizeBlocks; }
        public void setFileSizeBlocks(Integer fileSizeBlocks) { this.fileSizeBlocks = fileSizeBlocks; }

        public String getNewName() { return newName; }
        public void setNewName(String newName) { this.newName = newName; }

        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }

        public String getOwner() { return owner; }
        public void setOwner(String owner) { this.owner = owner; }
    }

    private GlobalConfig globalConfig;
    private ComputerConfig computer;
    private ListaEnlazada<ProcesoJson> processes;

    public ConfigProcesosDisco() {
        // Dejar en null para detectar si faltan claves al deserializar con Gson
        this.globalConfig = null;
        this.computer = null;
        this.processes = null;
    }

    public ConfigProcesosDisco(GlobalConfig globalConfig, ComputerConfig computer,
                               ListaEnlazada<ProcesoJson> processes) {
        this.globalConfig = (globalConfig != null) ? globalConfig : new GlobalConfig(300, 100, "FIFO");
        this.computer = (computer != null) ? computer : new ComputerConfig(1024, "FCFS", 0);
        this.processes = (processes != null) ? processes : new ListaEnlazada<>();
    }

    public GlobalConfig getGlobalConfig() {
        return globalConfig;
    }

    public void setGlobalConfig(GlobalConfig globalConfig) {
        this.globalConfig = globalConfig;
    }

    public ComputerConfig getComputer() {
        return computer;
    }

    public void setComputer(ComputerConfig computer) {
        this.computer = computer;
    }

    public ListaEnlazada<ProcesoJson> getProcesses() {
        return processes;
    }

    public void setProcesses(ListaEnlazada<ProcesoJson> processes) {
        this.processes = (processes != null) ? processes : new ListaEnlazada<>();
    }

    /** Valida la configuración global, del computador único y de los procesos sin {@code java.util}. */
    public ListaEnlazada<String> validar() {
        ListaEnlazada<String> errores = new ListaEnlazada<>();

        if (globalConfig == null) {
            errores.agregar("Falta la sección 'globalConfig'.");
        } else {
            if (globalConfig.getCycleDurationMs() <= 0) {
                errores.agregar("'globalConfig.cycleDurationMs' debe ser mayor que cero.");
            }
            if (globalConfig.getSwapSizeBlocks() < 0) {
                errores.agregar("'globalConfig.swapSizeBlocks' no puede ser negativo.");
            }
            if (!esPoliticaDiscoValida(globalConfig.getInitialDiskPolicy())) {
                errores.agregar("'globalConfig.initialDiskPolicy' inválida ('"
                        + globalConfig.getInitialDiskPolicy() + "'). Use FIFO, SSTF, SCAN o C-SCAN.");
            }
        }

        if (computer == null) {
            errores.agregar("Falta la sección 'computer'.");
        } else {
            if (computer.getRamCapacity() <= 0) {
                errores.agregar("'computer.ramCapacity' debe ser mayor que cero.");
            }
            if (!esPoliticaCpuValida(computer.getPolicy())) {
                errores.agregar("'computer.policy' inválida ('"
                        + computer.getPolicy() + "'). Use FCFS, ROUND_ROBIN, EDF o PRIORIDAD.");
            }
            PoliticaPlanificacion polCpu = GestorPersistencia.parsearPolitica(computer.getPolicy());
            if (polCpu == PoliticaPlanificacion.ROUND_ROBIN && computer.getQuantum() <= 0) {
                errores.agregar("'computer.quantum' debe ser mayor que cero cuando la política es Round Robin.");
            }
        }

        if (processes == null) {
            errores.agregar("Falta el arreglo 'processes'.");
            return errores;
        }

        Iterador<ProcesoJson> it = processes.iterador();
        int idx = 0;
        while (it.haySiguiente()) {
            ProcesoJson p = it.siguiente();
            idx++;
            if (p == null) {
                errores.agregar("El proceso #" + idx + " es nulo.");
                continue;
            }
            String nom = (p.getName() == null || p.getName().trim().isEmpty()) ? ("#" + idx) : p.getName().trim();
            if (p.getName() == null || p.getName().trim().isEmpty()) {
                errores.agregar("El proceso #" + idx + " tiene el nombre vacío.");
            }
            if (p.getMemory() <= 0) {
                errores.agregar("El proceso '" + nom + "' debe tener 'memory' > 0.");
            } else if (computer != null && computer.getRamCapacity() > 0 && p.getMemory() > computer.getRamCapacity()) {
                errores.agregar("El proceso '" + nom + "' requiere " + p.getMemory()
                        + " bloques de RAM, superando 'computer.ramCapacity' (" + computer.getRamCapacity() + ").");
            }
            if (p.getDeadline() <= 0) {
                errores.agregar("El proceso '" + nom + "' debe tener 'deadline' > 0.");
            }
            if (p.getInstructions() <= 0) {
                errores.agregar("El proceso '" + nom + "' debe tener 'instructions' > 0.");
            }

            if (p.getOperation() != null && !p.getOperation().trim().isEmpty()) {
                TipoOperacionES op = parsearOperacionEstricta(p.getOperation());
                if (op == null || !op.esOperacionArchivo()) {
                    errores.agregar("Operación inválida '" + p.getOperation() + "' en el proceso '" + nom
                            + "'. Se esperaba CREATE, READ, UPDATE o DELETE.");
                }
                if (p.getTargetPath() == null || p.getTargetPath().trim().isEmpty()) {
                    errores.agregar("El proceso de E/S '" + nom + "' debe indicar 'targetPath'.");
                }
                if (op == TipoOperacionES.CREAR) {
                    if (p.getFileSizeBlocks() == null || p.getFileSizeBlocks() < 0) {
                        errores.agregar("El proceso CREATE '" + nom
                                + "' debe indicar 'fileSizeBlocks' >= 0 (0 para directorio, >= 1 para archivo).");
                    }
                }
            }
        }
        return errores;
    }

    public void validarEstricto() {
        ListaEnlazada<String> errs = validar();
        if (!errs.estaVacia()) {
            StringBuilder sb = new StringBuilder("JSON de Procesos y Configuración inválido:\n");
            Iterador<String> it = errs.iterador();
            while (it.haySiguiente()) {
                sb.append(" • ").append(it.siguiente()).append("\n");
            }
            throw new IllegalArgumentException(sb.toString().trim());
        }
    }

    private static boolean esPoliticaDiscoValida(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return false;
        }
        String t = texto.trim().toUpperCase();
        return t.equals("FIFO") || t.equals("SSTF") || t.equals("SCAN")
                || t.equals("C-SCAN") || t.equals("C_SCAN") || t.equals("CSCAN");
    }

    private static boolean esPoliticaCpuValida(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return false;
        }
        String t = texto.trim().toUpperCase();
        return t.equals("FCFS") || t.contains("FIRST COME")
                || t.equals("ROUND_ROBIN") || t.equals("ROUND ROBIN") || t.equals("RR")
                || t.equals("EDF") || t.contains("DEADLINE")
                || t.equals("PRIORIDAD") || t.equals("PRIORIDADES") || t.equals("PRIORITY");
    }

    /** Convierte un {@link ProcesoJson} en una instancia de {@link PCB} lista para el computador único. */
    public static PCB crearPCB(ProcesoJson pj, int idComputador, long cicloActual) {
        if (pj == null) {
            throw new IllegalArgumentException("El proceso JSON no puede ser nulo.");
        }
        int totalInstr = Math.max(1, pj.getInstructions());
        int prioridad = (pj.getPriority() != null && pj.getPriority() > 0) ? pj.getPriority() : 1;

        boolean esArchivoES = pj.getOperation() != null && !pj.getOperation().trim().isEmpty();
        ListaEnlazada<Instruccion> programa = new ListaEnlazada<>();

        if (esArchivoES) {
            TipoOperacionES op = TipoOperacionES.desdeTexto(pj.getOperation());
            programa.agregar(new Instruccion(TipoInstruccion.OPERACION_ES, op.getCodigoJson()));
            for (int i = 1; i < totalInstr; i++) {
                programa.agregar(new Instruccion(TipoInstruccion.CPU));
            }
            PCB pcb = new PCB(pj.getName().trim(), idComputador, TipoProceso.IO_BOUND,
                    prioridad, pj.getMemory(), pj.getDeadline(), "", 0, 0, programa, cicloActual);
            pcb.setOperacion(op);
            pcb.setRutaObjetivo(pj.getTargetPath() != null ? pj.getTargetPath().trim() : "");
            pcb.setTamanoArchivoBloques(pj.getFileSizeBlocks() != null ? pj.getFileSizeBlocks() : 0);
            pcb.setNuevoNombre(pj.getNewName() != null ? pj.getNewName().trim() : "");
            pcb.setModoSolicitante(ModoUsuario.desdeTexto(pj.getMode()));
            pcb.setUsuario(pj.getOwner() != null ? pj.getOwner().trim() : "admin");
            return pcb;
        }

        for (int i = 0; i < totalInstr; i++) {
            programa.agregar(new Instruccion(TipoInstruccion.CPU));
        }
        return new PCB(pj.getName().trim(), idComputador, TipoProceso.CPU_BOUND,
                prioridad, pj.getMemory(), pj.getDeadline(), "", 0, 0, programa, cicloActual);
    }

    public PoliticaDisco getPoliticaDiscoEnum() {
        return (globalConfig != null)
                ? PoliticaDisco.desdeTexto(globalConfig.getInitialDiskPolicy())
                : PoliticaDisco.FIFO;
    }

    public PoliticaPlanificacion getPoliticaCpuEnum() {
        return (computer != null)
                ? GestorPersistencia.parsearPolitica(computer.getPolicy())
                : PoliticaPlanificacion.FCFS;
    }

    private static TipoOperacionES parsearOperacionEstricta(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }
        String t = texto.trim().toUpperCase();
        return switch (t) {
            case "CREATE", "CREAR" -> TipoOperacionES.CREAR;
            case "READ", "LEER" -> TipoOperacionES.LEER;
            case "UPDATE", "ACTUALIZAR", "MODIFICAR", "RENOMBRAR" -> TipoOperacionES.ACTUALIZAR;
            case "DELETE", "ELIMINAR", "BORRAR" -> TipoOperacionES.ELIMINAR;
            default -> null;
        };
    }
}
