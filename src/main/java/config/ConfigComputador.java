package config;

import enums.PoliticaPlanificacion;

/**
 * Modelo de configuración de un computador para exportación/importación.
 */
public class ConfigComputador {

    private int id;
    private String nombre;
    private long memoriaTotal;
    private PoliticaPlanificacion politica;
    private int quantum;

    public ConfigComputador() {
        this(1, "PC-1", 1024, PoliticaPlanificacion.FCFS, 2);
    }

    public ConfigComputador(int id, String nombre, long memoriaTotal, PoliticaPlanificacion politica, int quantum) {
        this.id = id;
        this.nombre = (nombre == null || nombre.trim().isEmpty()) ? ("PC-" + id) : nombre.trim();
        this.memoriaTotal = memoriaTotal;
        this.politica = (politica == null) ? PoliticaPlanificacion.FCFS : politica;
        this.quantum = quantum <= 0 ? 2 : quantum;
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
        this.nombre = (nombre == null || nombre.trim().isEmpty()) ? ("PC-" + id) : nombre.trim();
    }

    public long getMemoriaTotal() {
        return memoriaTotal;
    }

    public void setMemoriaTotal(long memoriaTotal) {
        this.memoriaTotal = memoriaTotal;
    }

    public PoliticaPlanificacion getPolitica() {
        return politica;
    }

    public void setPolitica(PoliticaPlanificacion politica) {
        this.politica = politica;
    }

    public int getQuantum() {
        return quantum;
    }

    public void setQuantum(int quantum) {
        this.quantum = quantum;
    }

    @Override
    public String toString() {
        return nombre + " [ID: " + id + ", RAM: " + memoriaTotal + " KB, " + politica + ", q=" + quantum + "]";
    }
}
