package enums;

public enum ModoCPU {
    USUARIO("Usuario"),
    KERNEL("Kernel");

    private final String etiqueta;

    ModoCPU(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}