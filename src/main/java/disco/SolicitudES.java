package disco;

import enums.TipoOperacionES;
import modelo.PCB;

/**
 * Solicitud de Entrada/Salida que se encola en la cola única del disco simulado (SD).
 *
 * <p>Representa tanto peticiones sobre el sistema de archivos (crear, leer, actualizar,
 * eliminar) generadas por procesos de E/S en modo Kernel, como peticiones del planificador
 * a mediano plazo para suspender o reactivar procesos en el área de intercambio (Swap).</p>
 */
public class SolicitudES {

    private static int generadorId = 1;

    private final int id;
    private final PCB proceso;
    private final TipoOperacionES operacion;
    private final String ruta;
    private final String nuevoNombre;
    private final int bloqueDestino;
    private final int cantidadBloques;
    private final long cicloLlegada;
    private final boolean esSwap;

    private int bloquesRestantesTransferencia;
    private long cicloInicioAtencion = -1;
    private long cicloFinAtencion = -1;

    /**
     * Crea una solicitud de E/S estándar sobre un archivo o directorio.
     *
     * @param proceso         proceso que origina la solicitud (puede ser nulo en pruebas aisladas).
     * @param operacion       tipo de operación solicitada.
     * @param ruta            ruta del archivo o directorio en el sistema de archivos.
     * @param bloqueDestino   número del primer bloque hacia el cual debe desplazarse el cabezal.
     * @param cantidadBloques cantidad de bloques involucrados en la transferencia.
     * @param cicloLlegada    ciclo del reloj global en el que la solicitud entró a la cola.
     */
    public SolicitudES(PCB proceso, TipoOperacionES operacion, String ruta,
                       int bloqueDestino, int cantidadBloques, long cicloLlegada) {
        this(proceso, operacion, ruta, "", bloqueDestino, cantidadBloques, cicloLlegada,
                operacion != null && operacion.esOperacionSwap());
    }

    /**
     * Constructor completo para solicitudes de archivos (incluyendo renombrado en UPDATE)
     * y solicitudes del área de intercambio (Swap).
     */
    public SolicitudES(PCB proceso, TipoOperacionES operacion, String ruta, String nuevoNombre,
                       int bloqueDestino, int cantidadBloques, long cicloLlegada, boolean esSwap) {
        if (operacion == null) {
            throw new IllegalArgumentException("El tipo de operación de E/S no puede ser nulo.");
        }
        if (bloqueDestino < 0) {
            throw new IllegalArgumentException("El bloque destino no puede ser negativo: " + bloqueDestino);
        }
        this.id = siguienteId();
        this.proceso = proceso;
        this.operacion = operacion;
        this.ruta = (ruta != null) ? ruta.trim() : "";
        this.nuevoNombre = (nuevoNombre != null) ? nuevoNombre.trim() : "";
        this.bloqueDestino = bloqueDestino;
        this.cantidadBloques = Math.max(1, cantidadBloques);
        this.cicloLlegada = Math.max(0, cicloLlegada);
        this.esSwap = esSwap;
        this.bloquesRestantesTransferencia = this.cantidadBloques;
    }

    private static synchronized int siguienteId() {
        return generadorId++;
    }

    /** Reinicia el contador de identificadores (útil para suites de prueba). */
    public static synchronized void reiniciarGeneradorId() {
        generadorId = 1;
    }

    public int getId() {
        return id;
    }

    public PCB getProceso() {
        return proceso;
    }

    public int getIdProceso() {
        return (proceso != null) ? proceso.getId() : -1;
    }

    public String getNombreProceso() {
        return (proceso != null) ? proceso.getNombre() : ("Sol-" + id);
    }

    public TipoOperacionES getOperacion() {
        return operacion;
    }

    public String getRuta() {
        return ruta;
    }

    public String getNuevoNombre() {
        return nuevoNombre;
    }

    public int getBloqueDestino() {
        return bloqueDestino;
    }

    public int getCantidadBloques() {
        return cantidadBloques;
    }

    public long getCicloLlegada() {
        return cicloLlegada;
    }

    public boolean isEsSwap() {
        return esSwap;
    }

    public int getBloquesRestantesTransferencia() {
        return bloquesRestantesTransferencia;
    }

    public void consumirCicloTransferencia() {
        if (bloquesRestantesTransferencia > 0) {
            bloquesRestantesTransferencia--;
        }
    }

    public long getCicloInicioAtencion() {
        return cicloInicioAtencion;
    }

    public void setCicloInicioAtencion(long cicloInicioAtencion) {
        this.cicloInicioAtencion = cicloInicioAtencion;
    }

    public long getCicloFinAtencion() {
        return cicloFinAtencion;
    }

    public void setCicloFinAtencion(long cicloFinAtencion) {
        this.cicloFinAtencion = cicloFinAtencion;
    }

    /**
     * Tiempo que la solicitud esperó en la cola de E/S antes de comenzar a ser atendida.
     *
     * @return ciclos de espera, o {@code 0} si aún no ha sido atendida.
     */
    public long getTiempoEspera() {
        if (cicloInicioAtencion < 0 || cicloInicioAtencion < cicloLlegada) {
            return 0;
        }
        return cicloInicioAtencion - cicloLlegada;
    }

    @Override
    public String toString() {
        String proc = getNombreProceso();
        String destino = ruta.isEmpty() ? ("bloque " + bloqueDestino) : (ruta + " [bloque " + bloqueDestino + "]");
        return proc + " -> " + operacion.getCodigoJson() + " " + destino;
    }
}
