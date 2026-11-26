package archivos;

import disco.BloqueDisco;
import disco.DiscoSimulado;
import disco.OperacionRechazadaException;
import disco.ServicioArchivos;
import disco.SolicitudES;
import enums.ModoUsuario;
import enums.PermisoArchivo;
import enums.TipoNodoArchivo;
import enums.TipoOperacionES;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import java.util.concurrent.Semaphore;
import modelo.PCB;

/**
 * Sistema de archivos jerárquico de ÁvilaOS con asignación encadenada sobre
 * {@link DiscoSimulado}.
 *
 * <p>Implementa {@link ServicioArchivos} para validar permisos (Administrador vs.
 * Usuario), reservar bloques antes de encolar operaciones de creación y aplicar
 * los cambios lógicos en el árbol de directorios cuando el disco concluye la
 * transferencia física. Todas sus estructuras usan {@link ListaEnlazada} propia
 * (sin colecciones de {@code java.util}) y están protegidas por un {@link Semaphore}
 * binario justo.</p>
 */
public class SistemaArchivos implements ServicioArchivos {

    private static final String[] PALETA_COLORES = {
        "#3498DB", "#2ECC71", "#9B59B6", "#1ABC9C",
        "#E84393", "#00CEC9", "#6C5CE7", "#27AE60",
        "#FD79A8", "#0984E3", "#A29BFE", "#16A085",
        "#B33771", "#3B3B98"
    };

    /** Registro interno de una operación CREAR en tránsito entre preparar() y completar(). */
    private static final class CreacionPendiente {
        final int idSolicitud;
        final String ruta;
        final TipoNodoArchivo tipo;
        final int primerBloque;
        final int tamanoBloques;
        final String dueno;
        final PermisoArchivo permiso;
        final String procesoCreador;
        final String colorHex;

        CreacionPendiente(int idSolicitud, String ruta, TipoNodoArchivo tipo,
                          int primerBloque, int tamanoBloques, String dueno,
                          PermisoArchivo permiso, String procesoCreador, String colorHex) {
            this.idSolicitud = idSolicitud;
            this.ruta = ruta;
            this.tipo = tipo;
            this.primerBloque = primerBloque;
            this.tamanoBloques = tamanoBloques;
            this.dueno = dueno;
            this.permiso = permiso;
            this.procesoCreador = procesoCreador;
            this.colorHex = colorHex;
        }
    }

    private final DiscoSimulado disco;
    private final NodoArchivo raiz;
    private final Semaphore mutex = new Semaphore(1, true);
    private final ListaEnlazada<CreacionPendiente> creacionesPendientes = new ListaEnlazada<>();
    private int contadorColores = 0;

    /**
     * Crea un sistema de archivos vacío (solo con el directorio raíz {@code "/"})
     * respaldado por el disco indicado.
     *
     * @param disco disco simulado donde residirán los bloques de los archivos.
     */
    public SistemaArchivos(DiscoSimulado disco) {
        if (disco == null) {
            throw new IllegalArgumentException("El disco simulado no puede ser nulo.");
        }
        this.disco = disco;
        this.raiz = new NodoArchivo("/", "/", TipoNodoArchivo.DIRECTORIO,
                0, NodoArchivo.SIN_BLOQUE, "admin", PermisoArchivo.PUBLICO, "Sistema", "");
    }

    // =========================================================================
    // CONTRATO ServicioArchivos (CONEXIÓN CON COMPUTADOR / PROCESOS E/S)
    // =========================================================================

    @Override
    public SolicitudES preparar(PCB proceso, long cicloActual) throws OperacionRechazadaException {
        if (proceso == null) {
            throw new OperacionRechazadaException("El proceso solicitante no puede ser nulo.");
        }
        TipoOperacionES op = proceso.getOperacion();
        if (op == null || !op.esOperacionArchivo()) {
            throw new OperacionRechazadaException("Operación de E/S no válida para el sistema de archivos: " + op);
        }

        String ruta = normalizarRuta(proceso.getRutaObjetivo());
        ModoUsuario modo = (proceso.getModoSolicitante() != null)
                ? proceso.getModoSolicitante()
                : ModoUsuario.ADMINISTRADOR;
        String usuario = (proceso.getUsuario() != null && !proceso.getUsuario().isBlank())
                ? proceso.getUsuario().trim()
                : "admin";

        mutex.acquireUninterruptibly();
        try {
            return switch (op) {
                case CREAR -> prepararCrearLocked(proceso, ruta, modo, usuario, cicloActual);
                case LEER -> prepararLeerLocked(proceso, ruta, modo, usuario, cicloActual);
                case ACTUALIZAR -> prepararActualizarLocked(proceso, ruta, modo, usuario, cicloActual);
                case ELIMINAR -> prepararEliminarLocked(proceso, ruta, modo, usuario, cicloActual);
                default -> throw new OperacionRechazadaException("Operación no soportada: " + op);
            };
        } finally {
            mutex.release();
        }
    }

    @Override
    public void completar(SolicitudES solicitud, boolean exito) {
        if (solicitud == null || solicitud.getOperacion() == null || !solicitud.getOperacion().esOperacionArchivo()) {
            return;
        }

        mutex.acquireUninterruptibly();
        try {
            switch (solicitud.getOperacion()) {
                case CREAR -> completarCrearLocked(solicitud, exito);
                case ACTUALIZAR -> completarActualizarLocked(solicitud, exito);
                case ELIMINAR -> completarEliminarLocked(solicitud, exito);
                case LEER -> {
                    // La lectura no modifica la estructura del árbol ni los bloques.
                }
                default -> { }
            }
        } finally {
            mutex.release();
        }
    }

    // =========================================================================
    // IMPLEMENTACIÓN DE PREPARACIÓN (VALIDACIÓN + RESERVA DE BLOQUES)
    // =========================================================================

    private SolicitudES prepararCrearLocked(PCB proceso, String ruta, ModoUsuario modo,
                                            String usuario, long cicloActual) {
        if ("/".equals(ruta)) {
            throw new OperacionRechazadaException("El directorio raíz '/' ya existe.");
        }
        if (buscarPorRutaLocked(ruta) != null || existeCreacionPendienteLocked(ruta)) {
            throw new OperacionRechazadaException("Ya existe una entrada o creación en curso en '" + ruta + "'.");
        }

        String rutaPadre = obtenerRutaPadre(ruta);
        NodoArchivo padre = buscarPorRutaLocked(rutaPadre);
        if (padre == null || !padre.esDirectorio()) {
            throw new OperacionRechazadaException("El directorio padre '" + rutaPadre + "' no existe.");
        }

        int tamano = proceso.getTamanoArchivoBloques();
        boolean esDirectorio = (tamano <= 0);

        if (!modo.esAdministrador()) {
            if (esDirectorio) {
                throw new OperacionRechazadaException(
                        "Permiso denegado: en modo Usuario solo el Administrador puede crear directorios.");
            }
            if (!padre.getDueno().equalsIgnoreCase(usuario)) {
                throw new OperacionRechazadaException(
                        "Permiso denegado: el usuario '" + usuario
                                + "' solo puede crear archivos dentro de sus propios directorios ('"
                                + padre.getRuta() + "' pertenece a '" + padre.getDueno() + "').");
            }
        }

        PermisoArchivo permiso = (modo.esAdministrador() && proceso.getPermisoSolicitado() != null)
                ? proceso.getPermisoSolicitado()
                : (modo.esAdministrador() ? PermisoArchivo.PUBLICO : PermisoArchivo.PRIVADO);

        if (esDirectorio) {
            SolicitudES sol = new SolicitudES(proceso, TipoOperacionES.CREAR, ruta, "",
                    0, 1, cicloActual, false);
            creacionesPendientes.agregar(new CreacionPendiente(
                    sol.getId(), ruta, TipoNodoArchivo.DIRECTORIO,
                    NodoArchivo.SIN_BLOQUE, 0, usuario, permiso, proceso.getNombre(), ""));
            return sol;
        }

        String color = siguienteColor();
        int primerBloque = disco.asignarBloquesArchivo(ruta, tamano, color);
        if (primerBloque < 0) {
            throw new OperacionRechazadaException(
                    "Espacio insuficiente en disco para crear '" + ruta + "' (" + tamano
                            + " bloques requeridos, " + disco.getBloquesLibresDatos() + " disponibles).");
        }

        SolicitudES sol = new SolicitudES(proceso, TipoOperacionES.CREAR, ruta, "",
                primerBloque, tamano, cicloActual, false);
        creacionesPendientes.agregar(new CreacionPendiente(
                sol.getId(), ruta, TipoNodoArchivo.ARCHIVO,
                primerBloque, tamano, usuario, permiso, proceso.getNombre(), color));
        return sol;
    }

    private SolicitudES prepararLeerLocked(PCB proceso, String ruta, ModoUsuario modo,
                                           String usuario, long cicloActual) {
        NodoArchivo nodo = buscarPorRutaLocked(ruta);
        if (nodo == null) {
            throw new OperacionRechazadaException("El archivo '" + ruta + "' no existe.");
        }
        if (nodo.esDirectorio()) {
            throw new OperacionRechazadaException("No se puede leer un directorio como archivo: '" + ruta + "'.");
        }
        if (!modo.esAdministrador() && !nodo.esPublico() && !nodo.getDueno().equalsIgnoreCase(usuario)) {
            throw new OperacionRechazadaException(
                    "Acceso denegado: el archivo '" + ruta + "' es privado y pertenece a '" + nodo.getDueno() + "'.");
        }
        int bloque = Math.max(0, nodo.getPrimerBloque());
        int cant = Math.max(1, nodo.getTamanoBloques());
        return new SolicitudES(proceso, TipoOperacionES.LEER, ruta, "", bloque, cant, cicloActual, false);
    }

    private SolicitudES prepararActualizarLocked(PCB proceso, String ruta, ModoUsuario modo,
                                                 String usuario, long cicloActual) {
        if ("/".equals(ruta)) {
            throw new OperacionRechazadaException("No se puede modificar el directorio raíz '/'.");
        }
        NodoArchivo nodo = buscarPorRutaLocked(ruta);
        if (nodo == null) {
            throw new OperacionRechazadaException("La ruta '" + ruta + "' no existe.");
        }

        if (!modo.esAdministrador()) {
            if (nodo.esDirectorio()) {
                throw new OperacionRechazadaException(
                        "Permiso denegado: solo el Administrador puede modificar o renombrar directorios.");
            }
            if (!nodo.getDueno().equalsIgnoreCase(usuario)) {
                throw new OperacionRechazadaException(
                        "Permiso denegado: el usuario '" + usuario
                                + "' no puede modificar archivos del sistema ni de otros usuarios (dueño: '"
                                + nodo.getDueno() + "').");
            }
        }

        String nuevoNombreLimpio = "";
        String crudo = proceso.getNuevoNombre();
        if (crudo != null && !crudo.trim().isEmpty()) {
            nuevoNombreLimpio = extraerNombreRenombrado(crudo.trim(), obtenerRutaPadre(ruta));
            if (!nuevoNombreLimpio.equals(nodo.getNombre())
                    && nodo.getPadre() != null
                    && nodo.getPadre().buscarHijo(nuevoNombreLimpio) != null) {
                throw new OperacionRechazadaException(
                        "Ya existe una entrada llamada '" + nuevoNombreLimpio + "' en '"
                                + nodo.getPadre().getRuta() + "'.");
            }
        }

        int bloque = nodo.esArchivo() ? Math.max(0, nodo.getPrimerBloque()) : buscarPrimerBloqueEnSubarbol(nodo);
        int cant = nodo.esArchivo() ? Math.max(1, nodo.getTamanoBloques()) : 1;
        return new SolicitudES(proceso, TipoOperacionES.ACTUALIZAR, ruta, nuevoNombreLimpio,
                bloque, cant, cicloActual, false);
    }

    private SolicitudES prepararEliminarLocked(PCB proceso, String ruta, ModoUsuario modo,
                                               String usuario, long cicloActual) {
        if ("/".equals(ruta)) {
            throw new OperacionRechazadaException("No se puede eliminar el directorio raíz '/'.");
        }
        NodoArchivo nodo = buscarPorRutaLocked(ruta);
        if (nodo == null) {
            throw new OperacionRechazadaException("La ruta '" + ruta + "' no existe.");
        }

        if (!modo.esAdministrador()) {
            if (nodo.esDirectorio()) {
                throw new OperacionRechazadaException(
                        "Permiso denegado: solo el Administrador puede eliminar directorios.");
            }
            if (!nodo.getDueno().equalsIgnoreCase(usuario)) {
                throw new OperacionRechazadaException(
                        "Permiso denegado: el usuario '" + usuario
                                + "' no puede eliminar archivos del sistema ni de otros usuarios (dueño: '"
                                + nodo.getDueno() + "').");
            }
        }

        int bloque = nodo.esArchivo() ? Math.max(0, nodo.getPrimerBloque()) : buscarPrimerBloqueEnSubarbol(nodo);
        return new SolicitudES(proceso, TipoOperacionES.ELIMINAR, ruta, "", bloque, 1, cicloActual, false);
    }

    // =========================================================================
    // IMPLEMENTACIÓN DE FINALIZACIÓN (APLICACIÓN EN ÁRBOL Y DISCO)
    // =========================================================================

    private void completarCrearLocked(SolicitudES sol, boolean exito) {
        CreacionPendiente pendiente = extraerPendienteLocked(sol.getId());
        if (pendiente == null) {
            return;
        }
        if (!exito) {
            if (pendiente.primerBloque != NodoArchivo.SIN_BLOQUE) {
                disco.liberarCadenaBloques(pendiente.primerBloque);
            }
            return;
        }

        String rutaPadre = obtenerRutaPadre(pendiente.ruta);
        NodoArchivo padre = buscarPorRutaLocked(rutaPadre);
        if (padre == null || !padre.esDirectorio() || buscarPorRutaLocked(pendiente.ruta) != null) {
            if (pendiente.primerBloque != NodoArchivo.SIN_BLOQUE) {
                disco.liberarCadenaBloques(pendiente.primerBloque);
            }
            return;
        }

        String nombre = obtenerNombreBase(pendiente.ruta);
        NodoArchivo nuevo = new NodoArchivo(nombre, pendiente.ruta, pendiente.tipo,
                pendiente.tamanoBloques, pendiente.primerBloque, pendiente.dueno,
                pendiente.permiso, pendiente.procesoCreador, pendiente.colorHex);
        padre.agregarHijo(nuevo);
    }

    private void completarActualizarLocked(SolicitudES sol, boolean exito) {
        if (!exito) {
            return;
        }
        NodoArchivo nodo = buscarPorRutaLocked(sol.getRuta());
        if (nodo == null || nodo == raiz) {
            return;
        }
        String nuevoNombre = sol.getNuevoNombre();
        if (nuevoNombre == null || nuevoNombre.isEmpty() || nuevoNombre.equals(nodo.getNombre())) {
            return;
        }
        if (nodo.getPadre() != null && nodo.getPadre().buscarHijo(nuevoNombre) != null) {
            return;
        }
        nodo.setNombre(nuevoNombre);
        recalcularRutasYDiscoRecursivoLocked(nodo);
    }

    private void completarEliminarLocked(SolicitudES sol, boolean exito) {
        if (!exito) {
            return;
        }
        NodoArchivo nodo = buscarPorRutaLocked(sol.getRuta());
        if (nodo == null || nodo == raiz) {
            return;
        }
        if (nodo.getPadre() != null) {
            nodo.getPadre().eliminarHijo(nodo);
        }
        liberarBloquesRecursivoLocked(nodo);
    }

    private void liberarBloquesRecursivoLocked(NodoArchivo nodo) {
        if (nodo == null) {
            return;
        }
        if (nodo.esArchivo()) {
            if (nodo.getPrimerBloque() >= 0) {
                disco.liberarCadenaBloques(nodo.getPrimerBloque());
            }
            return;
        }
        Iterador<NodoArchivo> it = nodo.iteradorHijos();
        while (it.haySiguiente()) {
            liberarBloquesRecursivoLocked(it.siguiente());
        }
    }

    private void recalcularRutasYDiscoRecursivoLocked(NodoArchivo nodo) {
        NodoArchivo padre = nodo.getPadre();
        String nuevaRuta = (padre == null || "/".equals(padre.getRuta()))
                ? ("/" + nodo.getNombre())
                : (padre.getRuta() + "/" + nodo.getNombre());
        nodo.setRuta(nuevaRuta);

        if (nodo.esArchivo() && nodo.getPrimerBloque() >= 0) {
            disco.renombrarPropietarioCadena(nodo.getPrimerBloque(), nuevaRuta);
        }
        Iterador<NodoArchivo> it = nodo.iteradorHijos();
        while (it.haySiguiente()) {
            recalcularRutasYDiscoRecursivoLocked(it.siguiente());
        }
    }

    // =========================================================================
    // PRECARGA (JSON DEL SISTEMA DE ARCHIVOS) Y CONSULTAS SEGURAS PARA GUI
    // =========================================================================

    /**
     * Precarga una entrada de archivo o directorio directamente en el árbol y el disco
     * (usado al deserializar el JSON inicial del sistema de archivos).
     *
     * @param ruta          ruta absoluta (ej. "/sistema/boot.bin").
     * @param tipo          {@link TipoNodoArchivo#ARCHIVO} o {@link TipoNodoArchivo#DIRECTORIO}.
     * @param startBlock    bloque inicial en disco (ignorado si es directorio).
     * @param blocks        cantidad de bloques (ignorado si es directorio).
     * @param dueno         propietario de la entrada.
     * @param permiso       visibilidad ({@link PermisoArchivo}).
     */
    public void cargarEntradaPrecargada(String ruta, TipoNodoArchivo tipo,
                                        int startBlock, int blocks,
                                        String dueno, PermisoArchivo permiso) {
        String rutaNorm = normalizarRuta(ruta);
        if ("/".equals(rutaNorm)) {
            return;
        }
        if (tipo == null) {
            throw new IllegalArgumentException("El tipo de entrada no puede ser nulo.");
        }

        mutex.acquireUninterruptibly();
        try {
            if (buscarPorRutaLocked(rutaNorm) != null) {
                throw new IllegalStateException("Ya existe una entrada en la ruta '" + rutaNorm + "'.");
            }
            NodoArchivo padre = asegurarDirectoriosPadreLocked(obtenerRutaPadre(rutaNorm), dueno);
            String nombre = obtenerNombreBase(rutaNorm);

            if (tipo == TipoNodoArchivo.DIRECTORIO) {
                NodoArchivo dir = new NodoArchivo(nombre, rutaNorm, TipoNodoArchivo.DIRECTORIO,
                        0, NodoArchivo.SIN_BLOQUE, dueno, permiso, "Precarga JSON", "");
                padre.agregarHijo(dir);
            } else {
                if (blocks <= 0) {
                    throw new IllegalArgumentException("El archivo '" + rutaNorm + "' debe tener al menos 1 bloque.");
                }
                String color = siguienteColor();
                boolean ocupado = disco.ocuparBloquesContiguosArchivo(rutaNorm, startBlock, blocks, color);
                if (!ocupado) {
                    throw new IllegalStateException(
                            "No se pudieron asignar los bloques [" + startBlock + ".." + (startBlock + blocks - 1)
                                    + "] para '" + rutaNorm + "' (fuera de rango u ocupados).");
                }
                NodoArchivo arch = new NodoArchivo(nombre, rutaNorm, TipoNodoArchivo.ARCHIVO,
                        blocks, startBlock, dueno, permiso, "Precarga JSON", color);
                padre.agregarHijo(arch);
            }
        } finally {
            mutex.release();
        }
    }

    /** Devuelve una copia profunda y segura de todo el árbol desde {@code "/"} para el {@code JTree}. */
    public NodoArchivo obtenerCopiaArbol() {
        mutex.acquireUninterruptibly();
        try {
            return raiz.copiarSubarbol(null);
        } finally {
            mutex.release();
        }
    }

    /** Busca una entrada por su ruta y devuelve una copia segura (sin hijos) o {@code null}. */
    public NodoArchivo buscarPorRuta(String ruta) {
        String norm;
        try {
            norm = normalizarRuta(ruta);
        } catch (OperacionRechazadaException ex) {
            return null;
        }
        mutex.acquireUninterruptibly();
        try {
            NodoArchivo encontrado = buscarPorRutaLocked(norm);
            if (encontrado == null) {
                return null;
            }
            return new NodoArchivo(encontrado.getNombre(), encontrado.getRuta(), encontrado.getTipo(),
                    encontrado.getTamanoBloques(), encontrado.getPrimerBloque(),
                    encontrado.getDueno(), encontrado.getPermiso(),
                    encontrado.getProcesoCreador(), encontrado.getColorHex());
        } finally {
            mutex.release();
        }
    }

    /** Devuelve todos los archivos regulares del sistema (en preorden) para la {@code JTable}. */
    public ListaEnlazada<NodoArchivo> listarTodosLosArchivos() {
        ListaEnlazada<NodoArchivo> archivos = new ListaEnlazada<>();
        mutex.acquireUninterruptibly();
        try {
            recolectarLocked(raiz, archivos, false, true);
        } finally {
            mutex.release();
        }
        return archivos;
    }

    /** Devuelve todas las entradas (directorios y archivos, excluyendo la raíz) para persistencia JSON. */
    public ListaEnlazada<NodoArchivo> listarEntradasSinRaiz() {
        ListaEnlazada<NodoArchivo> entradas = new ListaEnlazada<>();
        mutex.acquireUninterruptibly();
        try {
            recolectarLocked(raiz, entradas, true, false);
        } finally {
            mutex.release();
        }
        return entradas;
    }

    public int getCantidadArchivos() {
        return listarTodosLosArchivos().getTamano();
    }

    public int getCantidadDirectorios() {
        ListaEnlazada<NodoArchivo> dirs = new ListaEnlazada<>();
        mutex.acquireUninterruptibly();
        try {
            recolectarDirectoriosLocked(raiz, dirs);
        } finally {
            mutex.release();
        }
        return dirs.getTamano();
    }

    public DiscoSimulado getDisco() {
        return disco;
    }

    // =========================================================================
    // UTILIDADES INTERNAS DE ÁRBOL Y RUTAS
    // =========================================================================

    private NodoArchivo buscarPorRutaLocked(String rutaNormalizada) {
        if ("/".equals(rutaNormalizada)) {
            return raiz;
        }
        String sinBarra = rutaNormalizada.substring(1);
        String[] partes = sinBarra.split("/");
        NodoArchivo actual = raiz;
        for (int i = 0; i < partes.length; i++) {
            if (actual == null || !actual.esDirectorio()) {
                return null;
            }
            actual = actual.buscarHijo(partes[i]);
        }
        return actual;
    }

    private NodoArchivo asegurarDirectoriosPadreLocked(String rutaDir, String duenoPorDefecto) {
        if ("/".equals(rutaDir)) {
            return raiz;
        }
        String[] partes = rutaDir.substring(1).split("/");
        NodoArchivo actual = raiz;
        StringBuilder acum = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            acum.append('/').append(partes[i]);
            NodoArchivo hijo = actual.buscarHijo(partes[i]);
            if (hijo == null) {
                hijo = new NodoArchivo(partes[i], acum.toString(), TipoNodoArchivo.DIRECTORIO,
                        0, NodoArchivo.SIN_BLOQUE, duenoPorDefecto, PermisoArchivo.PUBLICO,
                        "Precarga JSON", "");
                actual.agregarHijo(hijo);
            } else if (!hijo.esDirectorio()) {
                throw new IllegalStateException("La ruta intermedia '" + acum + "' es un archivo, no un directorio.");
            }
            actual = hijo;
        }
        return actual;
    }

    private void recolectarLocked(NodoArchivo nodo, ListaEnlazada<NodoArchivo> destino,
                                  boolean incluirDirectorios, boolean soloArchivos) {
        if (nodo != raiz) {
            if (nodo.esArchivo() || (incluirDirectorios && !soloArchivos)) {
                destino.agregar(new NodoArchivo(nodo.getNombre(), nodo.getRuta(), nodo.getTipo(),
                        nodo.getTamanoBloques(), nodo.getPrimerBloque(), nodo.getDueno(),
                        nodo.getPermiso(), nodo.getProcesoCreador(), nodo.getColorHex()));
            }
        }
        Iterador<NodoArchivo> it = nodo.iteradorHijos();
        while (it.haySiguiente()) {
            recolectarLocked(it.siguiente(), destino, incluirDirectorios, soloArchivos);
        }
    }

    private void recolectarDirectoriosLocked(NodoArchivo nodo, ListaEnlazada<NodoArchivo> destino) {
        if (nodo.esDirectorio()) {
            destino.agregar(nodo);
            Iterador<NodoArchivo> it = nodo.iteradorHijos();
            while (it.haySiguiente()) {
                recolectarDirectoriosLocked(it.siguiente(), destino);
            }
        }
    }

    private int buscarPrimerBloqueEnSubarbol(NodoArchivo nodo) {
        if (nodo.esArchivo() && nodo.getPrimerBloque() >= 0) {
            return nodo.getPrimerBloque();
        }
        Iterador<NodoArchivo> it = nodo.iteradorHijos();
        while (it.haySiguiente()) {
            int b = buscarPrimerBloqueEnSubarbol(it.siguiente());
            if (b >= 0) {
                return b;
            }
        }
        return 0;
    }

    private boolean existeCreacionPendienteLocked(String ruta) {
        Iterador<CreacionPendiente> it = creacionesPendientes.iterador();
        while (it.haySiguiente()) {
            if (it.siguiente().ruta.equals(ruta)) {
                return true;
            }
        }
        return false;
    }

    private CreacionPendiente extraerPendienteLocked(int idSolicitud) {
        for (int i = 0; i < creacionesPendientes.getTamano(); i++) {
            CreacionPendiente cp = creacionesPendientes.obtener(i);
            if (cp.idSolicitud == idSolicitud) {
                return creacionesPendientes.eliminarEn(i);
            }
        }
        return null;
    }

    private String siguienteColor() {
        String color = PALETA_COLORES[contadorColores % PALETA_COLORES.length];
        contadorColores++;
        return color;
    }

    /**
     * Normaliza una ruta absoluta al formato {@code "/dir/archivo"}.
     *
     * @param ruta cruda ingresada por usuario o JSON.
     * @return ruta limpia.
     * @throws OperacionRechazadaException si es nula, vacía o contiene segmentos inválidos.
     */
    public static String normalizarRuta(String ruta) {
        if (ruta == null || ruta.trim().isEmpty()) {
            throw new OperacionRechazadaException("La ruta no puede estar vacía.");
        }
        String r = ruta.trim().replace('\\', '/');
        if (!r.startsWith("/")) {
            r = "/" + r;
        }
        while (r.contains("//")) {
            r = r.replace("//", "/");
        }
        if (r.length() > 1 && r.endsWith("/")) {
            r = r.substring(0, r.length() - 1);
        }
        if (!"/".equals(r)) {
            String[] partes = r.substring(1).split("/");
            for (int i = 0; i < partes.length; i++) {
                String seg = partes[i];
                if (seg.isEmpty() || ".".equals(seg) || "..".equals(seg)) {
                    throw new OperacionRechazadaException("Ruta inválida: '" + ruta + "'.");
                }
            }
        }
        return r;
    }

    private static String obtenerRutaPadre(String rutaNormalizada) {
        if ("/".equals(rutaNormalizada)) {
            return "/";
        }
        int ultimoSlash = rutaNormalizada.lastIndexOf('/');
        if (ultimoSlash <= 0) {
            return "/";
        }
        return rutaNormalizada.substring(0, ultimoSlash);
    }

    private static String obtenerNombreBase(String rutaNormalizada) {
        if ("/".equals(rutaNormalizada)) {
            return "/";
        }
        int ultimoSlash = rutaNormalizada.lastIndexOf('/');
        return (ultimoSlash < 0) ? rutaNormalizada : rutaNormalizada.substring(ultimoSlash + 1);
    }

    private static String extraerNombreRenombrado(String nuevoNombreCrudo, String rutaPadreEsperada) {
        if (!nuevoNombreCrudo.contains("/")) {
            return nuevoNombreCrudo;
        }
        String norm = normalizarRuta(nuevoNombreCrudo);
        String padreNuevo = obtenerRutaPadre(norm);
        if (!padreNuevo.equals(rutaPadreEsperada)) {
            throw new OperacionRechazadaException(
                    "Para renombrar no se puede mover a otro directorio ('" + norm + "').");
        }
        return obtenerNombreBase(norm);
    }
}
