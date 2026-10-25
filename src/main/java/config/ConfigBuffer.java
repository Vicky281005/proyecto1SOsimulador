package config;

/**
 * Modelo de configuración de un buffer acotado para exportación/importación.
 */
public class ConfigBuffer {

    private String id;
    private int capacidad;
    private int idComputadorAnfitrion;
    private long tamanoElemento;

    public ConfigBuffer() {
        this("BUF-1", 5, 1, 4);
    }

    public ConfigBuffer(String id, int capacidad, int idComputadorAnfitrion) {
        this(id, capacidad, idComputadorAnfitrion, 4);
    }

    public ConfigBuffer(String id, int capacidad, int idComputadorAnfitrion, long tamanoElemento) {
        this.id = (id == null || id.trim().isEmpty()) ? "BUF-1" : id.trim();
        this.capacidad = capacidad <= 0 ? 5 : capacidad;
        this.idComputadorAnfitrion = idComputadorAnfitrion;
        this.tamanoElemento = tamanoElemento <= 0 ? 4 : tamanoElemento;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = (id == null || id.trim().isEmpty()) ? "BUF-1" : id.trim();
    }

    public int getCapacidad() {
        return capacidad;
    }

    public void setCapacidad(int capacidad) {
        this.capacidad = capacidad <= 0 ? 5 : capacidad;
    }

    public int getIdComputadorAnfitrion() {
        return idComputadorAnfitrion;
    }

    public void setIdComputadorAnfitrion(int idComputadorAnfitrion) {
        this.idComputadorAnfitrion = idComputadorAnfitrion;
    }

    public long getTamanoElemento() {
        return tamanoElemento;
    }

    public void setTamanoElemento(long tamanoElemento) {
        this.tamanoElemento = tamanoElemento <= 0 ? 4 : tamanoElemento;
    }

    @Override
    public String toString() {
        return id + " [Capacidad: " + capacidad + ", PC Anfitrión: " + idComputadorAnfitrion + "]";
    }
}
