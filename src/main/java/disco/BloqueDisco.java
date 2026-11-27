package disco;

/**
 * Representa un bloque físico numerado del disco simulado (SD) de ÁvilaOS.
 *
 * <p>Implementa la estructura base para la <b>asignación encadenada</b>: cada
 * bloque almacena si está libre u ocupado, el identificador del archivo o proceso
 * suspendido que lo utiliza y el índice del {@link #siguienteBloque} en la cadena
 * ({@value #FIN_CADENA} si es el último bloque).</p>
 */
public class BloqueDisco {

    /** Centinela que indica el final de la cadena enlazada de bloques de un archivo o proceso. */
    public static final int FIN_CADENA = -1;

    private final int numero;
    private boolean ocupado;
    private boolean areaSwap;
    private boolean ocupadoPorSwap;
    private String propietario;
    private String colorHex;
    private int siguienteBloque;

    /**
     * Crea un bloque inicialmente libre en la posición indicada.
     *
     * @param numero índice físico del bloque (0 .. totalBloques - 1).
     */
    public BloqueDisco(int numero) {
        if (numero < 0) {
            throw new IllegalArgumentException("El número de bloque no puede ser negativo: " + numero);
        }
        this.numero = numero;
        this.ocupado = false;
        this.areaSwap = false;
        this.ocupadoPorSwap = false;
        this.propietario = "";
        this.colorHex = "";
        this.siguienteBloque = FIN_CADENA;
    }

    /**
     * Constructor de copia defensiva para entregar snapshots seguros a la interfaz gráfica.
     *
     * @param otro bloque original a copiar.
     */
    public BloqueDisco(BloqueDisco otro) {
        this.numero = otro.numero;
        this.ocupado = otro.ocupado;
        this.areaSwap = otro.areaSwap;
        this.ocupadoPorSwap = otro.ocupadoPorSwap;
        this.propietario = otro.propietario;
        this.colorHex = otro.colorHex;
        this.siguienteBloque = otro.siguienteBloque;
    }

    /**
     * Asigna este bloque a un archivo del sistema de archivos.
     *
     * @param rutaArchivo     ruta del archivo propietario.
     * @param siguienteBloque índice del siguiente bloque en la cadena, o {@value #FIN_CADENA}.
     * @param colorHex        color hexadecimal representativo para la interfaz gráfica.
     */
    public void ocuparParaArchivo(String rutaArchivo, int siguienteBloque, String colorHex) {
        this.ocupado = true;
        this.ocupadoPorSwap = false;
        this.propietario = (rutaArchivo != null) ? rutaArchivo.trim() : "";
        this.siguienteBloque = siguienteBloque;
        this.colorHex = (colorHex != null) ? colorHex.trim() : "";
    }

    /**
     * Asigna este bloque a un proceso suspendido en el área de intercambio (Swap).
     *
     * @param propietarioSwap identificador o nombre del proceso suspendido.
     * @param siguienteBloque índice del siguiente bloque de swap del proceso, o {@value #FIN_CADENA}.
     */
    public void ocuparParaSwap(String propietarioSwap, int siguienteBloque) {
        this.ocupado = true;
        this.ocupadoPorSwap = true;
        this.propietario = (propietarioSwap != null) ? propietarioSwap.trim() : "SWAP";
        this.siguienteBloque = siguienteBloque;
        this.colorHex = "#F59E0B";
    }

    /**
     * Libera el bloque dejando intacta su pertenencia o no al área reservada de Swap.
     */
    public void liberar() {
        this.ocupado = false;
        this.ocupadoPorSwap = false;
        this.propietario = "";
        this.colorHex = "";
        this.siguienteBloque = FIN_CADENA;
    }

    public int getNumero() {
        return numero;
    }

    public boolean isOcupado() {
        return ocupado;
    }

    public boolean isLibre() {
        return !ocupado;
    }

    public boolean isAreaSwap() {
        return areaSwap;
    }

    public void setAreaSwap(boolean areaSwap) {
        this.areaSwap = areaSwap;
    }

    public boolean isOcupadoPorSwap() {
        return ocupadoPorSwap;
    }

    public String getPropietario() {
        return propietario;
    }

    public void setPropietario(String propietario) {
        this.propietario = (propietario != null) ? propietario.trim() : "";
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = (colorHex != null) ? colorHex.trim() : "";
    }

    public int getSiguienteBloque() {
        return siguienteBloque;
    }

    public void setSiguienteBloque(int siguienteBloque) {
        this.siguienteBloque = siguienteBloque;
    }

    @Override
    public String toString() {
        if (!ocupado) {
            return "Bloque " + numero + (areaSwap ? " [SWAP LIBRE]" : " [LIBRE]");
        }
        String sig = (siguienteBloque == FIN_CADENA) ? "EOF" : String.valueOf(siguienteBloque);
        return "Bloque " + numero + " [" + propietario + " -> " + sig + "]";
    }
}
