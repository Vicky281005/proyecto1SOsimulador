package archivos;

import enums.PermisoArchivo;
import enums.TipoNodoArchivo;
import estructuras.Iterador;
import estructuras.ListaEnlazada;

/**
 * Nodo del árbol jerárquico del sistema de archivos de ÁvilaOS.
 *
 * <p>Representa tanto directorios como archivos regulares utilizando exclusivamente
 * {@link ListaEnlazada} propia (sin colecciones de {@code java.util}). Almacena
 * los metadatos requeridos para el {@code JTree}, la tabla de asignación de archivos
 * ({@code JTable}) y la validación de permisos entre modos Administrador y Usuario.</p>
 */
public class NodoArchivo {

    public static final int SIN_BLOQUE = -1;

    private String nombre;
    private String ruta;
    private final TipoNodoArchivo tipo;
    private int tamanoBloques;
    private int primerBloque;
    private String dueno;
    private PermisoArchivo permiso;
    private String procesoCreador;
    private String colorHex;

    private NodoArchivo padre;
    private final ListaEnlazada<NodoArchivo> hijos;

    /**
     * Crea un nodo del sistema de archivos.
     *
     * @param nombre         nombre simple del archivo o directorio (ej. "notas.txt").
     * @param ruta           ruta absoluta normalizada (ej. "/usuarios/notas.txt").
     * @param tipo           {@link TipoNodoArchivo#ARCHIVO} o {@link TipoNodoArchivo#DIRECTORIO}.
     * @param tamanoBloques  tamaño en bloques del disco (0 para directorios).
     * @param primerBloque   índice del primer bloque en la asignación encadenada ({@value #SIN_BLOQUE} para directorios).
     * @param dueno          identificador del propietario (ej. "admin", "user1").
     * @param permiso        visibilidad ({@link PermisoArchivo#PUBLICO} o {@link PermisoArchivo#PRIVADO}).
     * @param procesoCreador nombre del proceso o fuente que creó la entrada.
     * @param colorHex       color hexadecimal asociado a los bloques en el mapa de disco.
     */
    public NodoArchivo(String nombre, String ruta, TipoNodoArchivo tipo,
                       int tamanoBloques, int primerBloque, String dueno,
                       PermisoArchivo permiso, String procesoCreador, String colorHex) {
        if (tipo == null) {
            throw new IllegalArgumentException("El tipo de nodo no puede ser nulo.");
        }
        this.nombre = (nombre != null && !nombre.isEmpty()) ? nombre : "/";
        this.ruta = (ruta != null && !ruta.isEmpty()) ? ruta : "/";
        this.tipo = tipo;
        this.tamanoBloques = (tipo == TipoNodoArchivo.DIRECTORIO) ? 0 : Math.max(0, tamanoBloques);
        this.primerBloque = (tipo == TipoNodoArchivo.DIRECTORIO) ? SIN_BLOQUE : primerBloque;
        this.dueno = (dueno != null && !dueno.trim().isEmpty()) ? dueno.trim() : "admin";
        this.permiso = (permiso != null) ? permiso : PermisoArchivo.PUBLICO;
        this.procesoCreador = (procesoCreador != null && !procesoCreador.trim().isEmpty())
                ? procesoCreador.trim() : "Sistema";
        this.colorHex = (colorHex != null) ? colorHex.trim() : "";
        this.hijos = new ListaEnlazada<>();
    }

    /** Crea una copia defensiva del nodo y recursivamente de todo su subárbol. */
    public NodoArchivo copiarSubarbol(NodoArchivo padreCopia) {
        NodoArchivo copia = new NodoArchivo(nombre, ruta, tipo, tamanoBloques, primerBloque,
                dueno, permiso, procesoCreador, colorHex);
        copia.padre = padreCopia;
        Iterador<NodoArchivo> it = hijos.iterador();
        while (it.haySiguiente()) {
            NodoArchivo hijo = it.siguiente();
            if (hijo != null) {
                copia.hijos.agregar(hijo.copiarSubarbol(copia));
            }
        }
        return copia;
    }

    public boolean esDirectorio() {
        return tipo == TipoNodoArchivo.DIRECTORIO;
    }

    public boolean esArchivo() {
        return tipo == TipoNodoArchivo.ARCHIVO;
    }

    public boolean esPublico() {
        return permiso == PermisoArchivo.PUBLICO;
    }

    public void agregarHijo(NodoArchivo hijo) {
        if (hijo == null) {
            throw new IllegalArgumentException("No se puede agregar un hijo nulo.");
        }
        if (!esDirectorio()) {
            throw new IllegalStateException("Solo un directorio puede contener hijos: " + ruta);
        }
        hijo.setPadre(this);
        hijos.agregar(hijo);
    }

    public boolean eliminarHijo(NodoArchivo hijo) {
        if (hijo == null) {
            return false;
        }
        boolean eliminado = hijos.eliminar(hijo);
        if (eliminado) {
            hijo.setPadre(null);
        }
        return eliminado;
    }

    /** Busca un hijo directo por nombre exacto (sin distinguir mayúsculas/minúsculas opcionalmente exacto en POSIX). */
    public NodoArchivo buscarHijo(String nombreBuscado) {
        if (nombreBuscado == null || nombreBuscado.isEmpty()) {
            return null;
        }
        Iterador<NodoArchivo> it = hijos.iterador();
        while (it.haySiguiente()) {
            NodoArchivo h = it.siguiente();
            if (h != null && h.getNombre().equals(nombreBuscado)) {
                return h;
            }
        }
        return null;
    }

    public Iterador<NodoArchivo> iteradorHijos() {
        return hijos.iterador();
    }

    public int getCantidadHijos() {
        return hijos.getTamano();
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        if (nombre != null && !nombre.trim().isEmpty()) {
            this.nombre = nombre.trim();
        }
    }

    public String getRuta() {
        return ruta;
    }

    public void setRuta(String ruta) {
        if (ruta != null && !ruta.trim().isEmpty()) {
            this.ruta = ruta.trim();
        }
    }

    public TipoNodoArchivo getTipo() {
        return tipo;
    }

    public int getTamanoBloques() {
        return tamanoBloques;
    }

    public void setTamanoBloques(int tamanoBloques) {
        this.tamanoBloques = Math.max(0, tamanoBloques);
    }

    public int getPrimerBloque() {
        return primerBloque;
    }

    public void setPrimerBloque(int primerBloque) {
        this.primerBloque = primerBloque;
    }

    public String getDueno() {
        return dueno;
    }

    public void setDueno(String dueno) {
        if (dueno != null && !dueno.trim().isEmpty()) {
            this.dueno = dueno.trim();
        }
    }

    public PermisoArchivo getPermiso() {
        return permiso;
    }

    public void setPermiso(PermisoArchivo permiso) {
        if (permiso != null) {
            this.permiso = permiso;
        }
    }

    public String getProcesoCreador() {
        return procesoCreador;
    }

    public void setProcesoCreador(String procesoCreador) {
        if (procesoCreador != null && !procesoCreador.trim().isEmpty()) {
            this.procesoCreador = procesoCreador.trim();
        }
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = (colorHex != null) ? colorHex.trim() : "";
    }

    public NodoArchivo getPadre() {
        return padre;
    }

    public void setPadre(NodoArchivo padre) {
        this.padre = padre;
    }

    @Override
    public String toString() {
        return nombre;
    }
}
