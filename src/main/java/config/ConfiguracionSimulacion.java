package config;

import archivos.SistemaArchivos;
import disco.DiscoSimulado;
import enums.PoliticaDisco;
import enums.PoliticaPlanificacion;
import enums.TipoOperacionES;
import enums.TipoProceso;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import modelo.PCB;
import nucleo.Computador;
import planificacion.AlgoritmoPlanificacion;
import planificacion.FabricaPoliticas;
import simulacion.Simulador;

/**
 * Modelo integral de configuración de una simulación de ÁvilaOS (Proyecto 2).
 *
 * <p>Almacena la duración del ciclo, los parámetros del disco simulado (cantidad de
 * bloques, tamaño del área de intercambio/swap, posición inicial del cabezal, velocidad
 * y política inicial), la configuración del computador único y los procesos iniciales,
 * cumpliendo con la prohibición de colecciones de {@code java.util}.</p>
 */
public class ConfiguracionSimulacion {

    private long duracionCicloMs;
    private int totalBloquesDisco;
    private int tamanoSwap;
    private int cabezalInicial;
    private int velocidadCabezal;
    private PoliticaDisco politicaDiscoInicial;

    private ListaEnlazada<ConfigComputador> computadores;
    private ListaEnlazada<ConfigProceso> procesos;

    public ConfiguracionSimulacion() {
        this(300, DiscoSimulado.TOTAL_BLOQUES_POR_DEFECTO, 100,
                DiscoSimulado.POSICION_CABEZAL_POR_DEFECTO, DiscoSimulado.VELOCIDAD_CABEZAL_POR_DEFECTO,
                PoliticaDisco.FIFO, new ListaEnlazada<>(), new ListaEnlazada<>());
    }

    public ConfiguracionSimulacion(long duracionCicloMs, int totalBloquesDisco, int tamanoSwap,
                                  int cabezalInicial, int velocidadCabezal, PoliticaDisco politicaDiscoInicial,
                                  ListaEnlazada<ConfigComputador> computadores,
                                  ListaEnlazada<ConfigProceso> procesos) {
        this.duracionCicloMs = duracionCicloMs <= 0 ? 300 : duracionCicloMs;
        this.totalBloquesDisco = totalBloquesDisco <= 0 ? DiscoSimulado.TOTAL_BLOQUES_POR_DEFECTO : totalBloquesDisco;
        this.tamanoSwap = Math.max(0, tamanoSwap);
        this.cabezalInicial = Math.max(0, cabezalInicial);
        this.velocidadCabezal = velocidadCabezal <= 0 ? DiscoSimulado.VELOCIDAD_CABEZAL_POR_DEFECTO : velocidadCabezal;
        this.politicaDiscoInicial = (politicaDiscoInicial != null) ? politicaDiscoInicial : PoliticaDisco.FIFO;
        this.computadores = (computadores != null) ? computadores : new ListaEnlazada<>();
        this.procesos = (procesos != null) ? procesos : new ListaEnlazada<>();
    }

    public long getDuracionCicloMs() { return duracionCicloMs; }
    public void setDuracionCicloMs(long duracionCicloMs) { this.duracionCicloMs = duracionCicloMs; }

    public int getTotalBloquesDisco() { return totalBloquesDisco; }
    public void setTotalBloquesDisco(int totalBloquesDisco) { this.totalBloquesDisco = totalBloquesDisco; }

    public int getTamanoSwap() { return tamanoSwap; }
    public void setTamanoSwap(int tamanoSwap) { this.tamanoSwap = tamanoSwap; }

    public int getCabezalInicial() { return cabezalInicial; }
    public void setCabezalInicial(int cabezalInicial) { this.cabezalInicial = cabezalInicial; }

    public int getVelocidadCabezal() { return velocidadCabezal; }
    public void setVelocidadCabezal(int velocidadCabezal) { this.velocidadCabezal = velocidadCabezal; }

    public PoliticaDisco getPoliticaDiscoInicial() { return politicaDiscoInicial; }
    public void setPoliticaDiscoInicial(PoliticaDisco politicaDiscoInicial) {
        this.politicaDiscoInicial = (politicaDiscoInicial != null) ? politicaDiscoInicial : PoliticaDisco.FIFO;
    }

    public ListaEnlazada<ConfigComputador> getComputadores() { return computadores; }
    public void setComputadores(ListaEnlazada<ConfigComputador> computadores) {
        this.computadores = (computadores != null) ? computadores : new ListaEnlazada<>();
    }

    public ListaEnlazada<ConfigProceso> getProcesos() { return procesos; }
    public void setProcesos(ListaEnlazada<ConfigProceso> procesos) {
        this.procesos = (procesos != null) ? procesos : new ListaEnlazada<>();
    }

    /** Busca un computador por su ID. */
    public ConfigComputador buscarComputadorPorId(int id) {
        if (computadores == null) {
            return null;
        }
        Iterador<ConfigComputador> it = computadores.iterador();
        while (it.haySiguiente()) {
            ConfigComputador c = it.siguiente();
            if (c != null && c.getId() == id) {
                return c;
            }
        }
        return null;
    }

    /**
     * Valida la configuración completa (parámetros globales, disco, computador único y procesos).
     *
     * @return lista de mensajes de error; vacía si todo es válido.
     */
    public ListaEnlazada<String> validar() {
        ListaEnlazada<String> errores = new ListaEnlazada<>();

        if (duracionCicloMs <= 0) {
            errores.agregar("La duración del ciclo debe ser mayor que 0 ms (actual: " + duracionCicloMs + ").");
        }
        if (totalBloquesDisco <= 0) {
            errores.agregar("El número de bloques del disco debe ser mayor que 0 (actual: " + totalBloquesDisco + ").");
        }
        if (tamanoSwap < 0) {
            errores.agregar("El tamaño del área de intercambio (swap) no puede ser negativo.");
        }
        if (cabezalInicial < 0 || (totalBloquesDisco > 0 && cabezalInicial >= totalBloquesDisco)) {
            errores.agregar("La posición inicial del cabezal (" + cabezalInicial
                    + ") debe estar entre 0 y " + (totalBloquesDisco - 1) + ".");
        }
        if (velocidadCabezal <= 0) {
            errores.agregar("La velocidad del cabezal debe ser mayor que 0.");
        }

        if (computadores == null || computadores.estaVacia()) {
            errores.agregar("El sistema requiere configurar al menos 1 computador.");
        } else {
            Iterador<ConfigComputador> itPc = computadores.iterador();
            int idx = 0;
            while (itPc.haySiguiente()) {
                ConfigComputador pc = itPc.siguiente();
                idx++;
                if (pc.getId() <= 0) {
                    errores.agregar("El computador #" + idx + " tiene ID inválido (" + pc.getId() + ").");
                }
                if (pc.getNombre() == null || pc.getNombre().trim().isEmpty()) {
                    errores.agregar("El computador #" + idx + " tiene un nombre vacío.");
                }
                if (pc.getMemoriaTotal() <= 0) {
                    errores.agregar("El computador '" + pc.getNombre() + "' debe tener RAM > 0 bloques.");
                }
                if (pc.getPolitica() == PoliticaPlanificacion.ROUND_ROBIN && pc.getQuantum() <= 0) {
                    errores.agregar("El computador '" + pc.getNombre() + "' usa Round Robin y debe tener quantum > 0.");
                }
            }
        }

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
                if (pcAsig == null && computadores != null && !computadores.estaVacia()) {
                    pcAsig = computadores.obtener(0);
                }
                if (pcAsig == null) {
                    errores.agregar("El proceso '" + nom + "' no tiene un computador válido asignado.");
                } else if (p.getMemoria() > pcAsig.getMemoriaTotal()) {
                    errores.agregar("El proceso '" + nom + "' requiere " + p.getMemoria()
                            + " bloques de RAM, superando la RAM de " + pcAsig.getNombre()
                            + " (" + pcAsig.getMemoriaTotal() + " bloques).");
                }

                if (p.getInstrucciones() <= 0) {
                    errores.agregar("El proceso '" + nom + "' debe tener al menos 1 instrucción.");
                }
                if (p.getMemoria() <= 0) {
                    errores.agregar("El proceso '" + nom + "' debe requerir memoria > 0 bloques.");
                }
                if (p.getPrioridad() <= 0) {
                    errores.agregar("El proceso '" + nom + "' debe tener prioridad >= 1.");
                }
                if (p.getDeadline() <= 0) {
                    errores.agregar("El proceso '" + nom + "' debe tener un deadline > 0.");
                }
                if (p.getTipo() == TipoProceso.IO_BOUND && p.getOperacion() == null) {
                    if (p.getPeriodoES() <= 0 || p.getPeriodoES() > p.getInstrucciones()) {
                        errores.agregar("El proceso I/O '" + nom + "' tiene un periodo de E/S inválido ("
                                + p.getPeriodoES() + ").");
                    }
                }
                if (p.getOperacion() != null && p.getOperacion().esOperacionArchivo()) {
                    if (p.getRutaObjetivo() == null || p.getRutaObjetivo().trim().isEmpty()) {
                        errores.agregar("El proceso E/S '" + nom + "' requiere una ruta objetivo.");
                    }
                    if (p.getOperacion() == TipoOperacionES.CREAR && p.getTamanoArchivoBloques() < 0) {
                        errores.agregar("El proceso CREAR '" + nom + "' debe tener tamaño en bloques >= 0.");
                    }
                }
            }
        }

        return errores;
    }

    /** Valida de forma estricta y lanza {@link IllegalArgumentException} si hay errores. */
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
     * Construye un {@link Simulador} de 1 único computador con su {@link DiscoSimulado}
     * y {@link SistemaArchivos} conectado.
     */
    public Simulador aSimulador() {
        validarEstricto();

        Simulador sim = new Simulador();
        sim.setDuracionCicloMs(duracionCicloMs);

        ConfigComputador c = computadores.obtener(0);
        AlgoritmoPlanificacion alg = (c.getPolitica() == PoliticaPlanificacion.ROUND_ROBIN)
                ? FabricaPoliticas.crear(PoliticaPlanificacion.ROUND_ROBIN, c.getQuantum())
                : FabricaPoliticas.crear(c.getPolitica());
        Computador comp = new Computador(c.getId(), c.getNombre(), c.getMemoriaTotal(), alg);
        sim.registrarComputador(comp);

        DiscoSimulado disco = new DiscoSimulado("Disco-SD", totalBloquesDisco, cabezalInicial, 0);
        disco.configurarAreaSwap(tamanoSwap);
        disco.setPolitica(politicaDiscoInicial != null ? politicaDiscoInicial : PoliticaDisco.FIFO);
        disco.setVelocidadCabezal(velocidadCabezal > 0 ? velocidadCabezal : DiscoSimulado.VELOCIDAD_CABEZAL_POR_DEFECTO);

        SistemaArchivos sa = new SistemaArchivos(disco);
        sim.setDisco(disco);
        sim.setServicioArchivos(sa);

        Iterador<ConfigProceso> itProc = procesos.iterador();
        while (itProc.haySiguiente()) {
            ConfigProceso cp = itProc.siguiente();
            cp.setComputadorAsignado(comp.getId());
            PCB pcb = GestorPersistencia.crearPCB(cp, sim.getCicloActual());
            sim.cargarProceso(pcb);
        }

        return sim;
    }

    /** Extrae la configuración actual desde un {@link Simulador} en memoria. */
    public static ConfiguracionSimulacion desdeSimulador(Simulador sim) {
        if (sim == null) {
            return crearPorDefecto();
        }
        ConfiguracionSimulacion config = new ConfiguracionSimulacion();
        config.setDuracionCicloMs(sim.getDuracionCicloMs());

        DiscoSimulado disco = sim.getDisco();
        if (disco != null) {
            config.setTotalBloquesDisco(disco.getTotalBloquesDatos());
            config.setTamanoSwap(disco.getTamanoSwap());
            config.setCabezalInicial(disco.getPosicionCabezal());
            config.setVelocidadCabezal(disco.getVelocidadCabezal());
            config.setPoliticaDiscoInicial(disco.getPoliticaActual());
        }

        Iterador<Computador> itPc = sim.iteradorComputadores();
        if (itPc.haySiguiente()) {
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
            config.getComputadores().agregar(new ConfigComputador(
                    c.getId(), c.getNombre(), c.getMemoriaTotal(), pol, quantum));
        } else {
            config.getComputadores().agregar(new ConfigComputador(1, "PC-1", 1024, PoliticaPlanificacion.FCFS, 2));
        }

        return config;
    }

    /** Crea una configuración por defecto válida para el Proyecto 2 (1 computador y disco SD). */
    public static ConfiguracionSimulacion crearPorDefecto() {
        ConfiguracionSimulacion config = new ConfiguracionSimulacion();
        config.setDuracionCicloMs(300);
        config.setTotalBloquesDisco(200);
        config.setTamanoSwap(100);
        config.setCabezalInicial(50);
        config.setVelocidadCabezal(DiscoSimulado.VELOCIDAD_CABEZAL_POR_DEFECTO);
        config.setPoliticaDiscoInicial(PoliticaDisco.FIFO);

        config.getComputadores().agregar(new ConfigComputador(1, "PC-1", 1024, PoliticaPlanificacion.FCFS, 2));
        config.getProcesos().agregar(new ConfigProceso(1, "P1", TipoProceso.CPU_BOUND, 1, 10, 16, 1, 500, 0, 1));
        config.getProcesos().agregar(new ConfigProceso(2, "P2", TipoProceso.CPU_BOUND, 1, 12, 16, 2, 500, 0, 1));
        return config;
    }
}
