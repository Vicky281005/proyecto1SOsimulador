package config;

import enums.ModoUsuario;
import enums.TipoOperacionES;
import enums.TipoProceso;

/**
 * Modelo de configuración de un proceso para exportación/importación en el Proyecto 2
 * de ÁvilaOS (sin buffers de productor-consumidor).
 */
public class ConfigProceso {

    private int id;
    private String nombre;
    private TipoProceso tipo;
    private int computadorAsignado;
    private int instrucciones;
    private long memoria;
    private int prioridad;
    private long deadline;
    private int periodoES;
    private int ciclosPorAcceso;

    // Atributos de operación sobre el sistema de archivos (Proyecto 2)
    private TipoOperacionES operacion;
    private String rutaObjetivo;
    private int tamanoArchivoBloques;
    private String nuevoNombre;
    private ModoUsuario modoSolicitante;
    private String usuario;

    public ConfigProceso() {
        this(1, "Proc-1", TipoProceso.CPU_BOUND, 1, 10, 16, 1, 500, 0, 1);
    }

    public ConfigProceso(int id, String nombre, TipoProceso tipo, int computadorAsignado,
                         int instrucciones, long memoria, int prioridad, long deadline,
                         int periodoES, int ciclosPorAcceso) {
        this(id, nombre, tipo, computadorAsignado, instrucciones, memoria, prioridad, deadline,
                periodoES, ciclosPorAcceso, null, "", 0, "", ModoUsuario.ADMINISTRADOR, "admin");
    }

    public ConfigProceso(int id, String nombre, TipoProceso tipo, int computadorAsignado,
                         int instrucciones, long memoria, int prioridad, long deadline,
                         int periodoES, int ciclosPorAcceso,
                         TipoOperacionES operacion, String rutaObjetivo, int tamanoArchivoBloques,
                         String nuevoNombre, ModoUsuario modoSolicitante, String usuario) {
        this.id = id;
        this.nombre = (nombre == null || nombre.trim().isEmpty()) ? ("Proc-" + id) : nombre.trim();
        this.tipo = (tipo == null) ? TipoProceso.CPU_BOUND : tipo;
        this.computadorAsignado = computadorAsignado;
        this.instrucciones = instrucciones <= 0 ? 10 : instrucciones;
        this.memoria = memoria <= 0 ? 16 : memoria;
        this.prioridad = prioridad <= 0 ? 1 : prioridad;
        this.deadline = deadline <= 0 ? 500 : deadline;
        this.periodoES = periodoES < 0 ? 0 : periodoES;
        this.ciclosPorAcceso = ciclosPorAcceso <= 0 ? 1 : ciclosPorAcceso;
        this.operacion = operacion;
        this.rutaObjetivo = (rutaObjetivo == null) ? "" : rutaObjetivo.trim();
        this.tamanoArchivoBloques = Math.max(0, tamanoBloquesValidado(tamanoArchivoBloques));
        this.nuevoNombre = (nuevoNombre == null) ? "" : nuevoNombre.trim();
        this.modoSolicitante = (modoSolicitante == null) ? ModoUsuario.ADMINISTRADOR : modoSolicitante;
        this.usuario = (usuario == null || usuario.trim().isEmpty()) ? "admin" : usuario.trim();
    }

    private static int tamanoBloquesValidado(int v) {
        return Math.max(0, v);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) {
        this.nombre = (nombre == null || nombre.trim().isEmpty()) ? ("Proc-" + id) : nombre.trim();
    }

    public TipoProceso getTipo() { return tipo; }
    public void setTipo(TipoProceso tipo) { this.tipo = (tipo == null) ? TipoProceso.CPU_BOUND : tipo; }

    public int getComputadorAsignado() { return computadorAsignado; }
    public void setComputadorAsignado(int computadorAsignado) { this.computadorAsignado = computadorAsignado; }

    public int getInstrucciones() { return instrucciones; }
    public void setInstrucciones(int instrucciones) {
        this.instrucciones = instrucciones <= 0 ? 10 : instrucciones;
    }

    public long getMemoria() { return memoria; }
    public void setMemoria(long memoria) {
        this.memoria = memoria <= 0 ? 16 : memoria;
    }

    public int getPrioridad() { return prioridad; }
    public void setPrioridad(int prioridad) {
        this.prioridad = prioridad <= 0 ? 1 : prioridad;
    }

    public long getDeadline() { return deadline; }
    public void setDeadline(long deadline) {
        this.deadline = deadline <= 0 ? 500 : deadline;
    }

    public int getPeriodoES() { return periodoES; }
    public void setPeriodoES(int periodoES) {
        this.periodoES = periodoES < 0 ? 0 : periodoES;
    }

    public int getCiclosPorAcceso() { return ciclosPorAcceso; }
    public void setCiclosPorAcceso(int ciclosPorAcceso) {
        this.ciclosPorAcceso = ciclosPorAcceso <= 0 ? 1 : ciclosPorAcceso;
    }

    public TipoOperacionES getOperacion() { return operacion; }
    public void setOperacion(TipoOperacionES operacion) { this.operacion = operacion; }

    public String getRutaObjetivo() { return rutaObjetivo; }
    public void setRutaObjetivo(String rutaObjetivo) {
        this.rutaObjetivo = (rutaObjetivo == null) ? "" : rutaObjetivo.trim();
    }

    public int getTamanoArchivoBloques() { return tamanoArchivoBloques; }
    public void setTamanoArchivoBloques(int tamanoArchivoBloques) {
        this.tamanoArchivoBloques = Math.max(0, tamanoArchivoBloques);
    }

    public String getNuevoNombre() { return nuevoNombre; }
    public void setNuevoNombre(String nuevoNombre) {
        this.nuevoNombre = (nuevoNombre == null) ? "" : nuevoNombre.trim();
    }

    public ModoUsuario getModoSolicitante() { return modoSolicitante; }
    public void setModoSolicitante(ModoUsuario modoSolicitante) {
        this.modoSolicitante = (modoSolicitante == null) ? ModoUsuario.ADMINISTRADOR : modoSolicitante;
    }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) {
        this.usuario = (usuario == null || usuario.trim().isEmpty()) ? "admin" : usuario.trim();
    }
}
