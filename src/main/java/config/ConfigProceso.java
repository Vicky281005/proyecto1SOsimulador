package config;

import enums.TipoProceso;

/**
 * Modelo de configuración de un proceso para exportación/importación.
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
    private String idBufferTarget;
    private int ciclosPorAcceso;
    private int elementosRequeridos;

    public ConfigProceso() {
        this(1, "Proc-1", TipoProceso.CPU_BOUND, 1, 10, 200, 1, 100, 0, "", 1, 1);
    }

    public ConfigProceso(int id, String nombre, TipoProceso tipo, int computadorAsignado,
                         int instrucciones, long memoria, int prioridad, long deadline,
                         int periodoES, String idBufferTarget, int ciclosPorAcceso, int elementosRequeridos) {
        this.id = id;
        this.nombre = (nombre == null || nombre.trim().isEmpty()) ? ("Proc-" + id) : nombre.trim();
        this.tipo = (tipo == null) ? TipoProceso.CPU_BOUND : tipo;
        this.computadorAsignado = computadorAsignado;
        this.instrucciones = instrucciones <= 0 ? 10 : instrucciones;
        this.memoria = memoria <= 0 ? 100 : memoria;
        this.prioridad = prioridad <= 0 ? 1 : prioridad;
        this.deadline = deadline <= 0 ? 100 : deadline;
        this.periodoES = periodoES < 0 ? 0 : periodoES;
        this.idBufferTarget = idBufferTarget == null ? "" : idBufferTarget.trim();
        this.ciclosPorAcceso = ciclosPorAcceso <= 0 ? 1 : ciclosPorAcceso;
        this.elementosRequeridos = elementosRequeridos <= 0 ? 1 : elementosRequeridos;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = (nombre == null || nombre.trim().isEmpty()) ? ("Proc-" + id) : nombre.trim();
    }

    public TipoProceso getTipo() {
        return tipo;
    }

    public void setTipo(TipoProceso tipo) {
        this.tipo = tipo;
    }

    public int getComputadorAsignado() {
        return computadorAsignado;
    }

    public void setComputadorAsignado(int computadorAsignado) {
        this.computadorAsignado = computadorAsignado;
    }

    public int getInstrucciones() {
        return instrucciones;
    }

    public void setInstrucciones(int instrucciones) {
        this.instrucciones = instrucciones <= 0 ? 10 : instrucciones;
    }

    public long getMemoria() {
        return memoria;
    }

    public void setMemoria(long memoria) {
        this.memoria = memoria <= 0 ? 100 : memoria;
    }

    public int getPrioridad() {
        return prioridad;
    }

    public void setPrioridad(int prioridad) {
        this.prioridad = prioridad <= 0 ? 1 : prioridad;
    }

    public long getDeadline() {
        return deadline;
    }

    public void setDeadline(long deadline) {
        this.deadline = deadline <= 0 ? 100 : deadline;
    }

    public int getPeriodoES() {
        return periodoES;
    }

    public void setPeriodoES(int periodoES) {
        this.periodoES = periodoES < 0 ? 0 : periodoES;
    }

    public String getIdBufferTarget() {
        return idBufferTarget;
    }

    public void setIdBufferTarget(String idBufferTarget) {
        this.idBufferTarget = idBufferTarget == null ? "" : idBufferTarget.trim();
    }

    public int getCiclosPorAcceso() {
        return ciclosPorAcceso;
    }

    public void setCiclosPorAcceso(int ciclosPorAcceso) {
        this.ciclosPorAcceso = ciclosPorAcceso <= 0 ? 1 : ciclosPorAcceso;
    }

    public int getElementosRequeridos() {
        return elementosRequeridos;
    }

    public void setElementosRequeridos(int elementosRequeridos) {
        this.elementosRequeridos = elementosRequeridos <= 0 ? 1 : elementosRequeridos;
    }

    @Override
    public String toString() {
        return nombre + " [" + tipo + ", PC: " + computadorAsignado + ", RAM: " + memoria + " KB, Prio: " + prioridad + "]";
    }
}
