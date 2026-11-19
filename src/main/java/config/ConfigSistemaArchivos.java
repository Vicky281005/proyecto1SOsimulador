package config;

import archivos.NodoArchivo;
import archivos.SistemaArchivos;
import disco.DiscoSimulado;
import enums.PermisoArchivo;
import enums.TipoNodoArchivo;
import estructuras.Iterador;
import estructuras.ListaEnlazada;

/**
 * Modelo de configuración del <b>JSON 1 (Sistema de Archivos y Disco)</b> para ÁvilaOS.
 *
 * <p>Corresponde exactamente a la estructura especificada en el enunciado:
 * {@code diskConfig} ({@code totalBlocks}, {@code initialHead}) y {@code fileSystem}
 * ({@code path}, {@code type}, {@code startBlock}, {@code blocks}, {@code owner},
 * {@code permissions}).</p>
 */
public class ConfigSistemaArchivos {

    public static class DiskConfig {
        private int totalBlocks;
        private int initialHead;

        public DiskConfig() {
            this(200, 50);
        }

        public DiskConfig(int totalBlocks, int initialHead) {
            this.totalBlocks = totalBlocks;
            this.initialHead = initialHead;
        }

        public int getTotalBlocks() {
            return totalBlocks;
        }

        public void setTotalBlocks(int totalBlocks) {
            this.totalBlocks = totalBlocks;
        }

        public int getInitialHead() {
            return initialHead;
        }

        public void setInitialHead(int initialHead) {
            this.initialHead = initialHead;
        }
    }

    public static class EntradaArchivo {
        private String path;
        private String type;
        private Integer startBlock;
        private Integer blocks;
        private String owner;
        private String permissions;

        public EntradaArchivo() {
        }

        public EntradaArchivo(String path, String type, Integer startBlock, Integer blocks,
                              String owner, String permissions) {
            this.path = path;
            this.type = type;
            this.startBlock = startBlock;
            this.blocks = blocks;
            this.owner = owner;
            this.permissions = permissions;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Integer getStartBlock() {
            return startBlock;
        }

        public void setStartBlock(Integer startBlock) {
            this.startBlock = startBlock;
        }

        public Integer getBlocks() {
            return blocks;
        }

        public void setBlocks(Integer blocks) {
            this.blocks = blocks;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public String getPermissions() {
            return permissions;
        }

        public void setPermissions(String permissions) {
            this.permissions = permissions;
        }
    }

    private DiskConfig diskConfig;
    private ListaEnlazada<EntradaArchivo> fileSystem;

    public ConfigSistemaArchivos() {
        // Dejar en null para detectar si faltan claves al deserializar con Gson
        this.diskConfig = null;
        this.fileSystem = null;
    }

    public ConfigSistemaArchivos(DiskConfig diskConfig, ListaEnlazada<EntradaArchivo> fileSystem) {
        this.diskConfig = (diskConfig != null) ? diskConfig : new DiskConfig(200, 50);
        this.fileSystem = (fileSystem != null) ? fileSystem : new ListaEnlazada<>();
    }

    public DiskConfig getDiskConfig() {
        return diskConfig;
    }

    public void setDiskConfig(DiskConfig diskConfig) {
        this.diskConfig = diskConfig;
    }

    public ListaEnlazada<EntradaArchivo> getFileSystem() {
        return fileSystem;
    }

    public void setFileSystem(ListaEnlazada<EntradaArchivo> fileSystem) {
        this.fileSystem = (fileSystem != null) ? fileSystem : new ListaEnlazada<>();
    }

    /** Valida la estructura del disco y las entradas del sistema de archivos sin usar {@code java.util}. */
    public ListaEnlazada<String> validar() {
        ListaEnlazada<String> errores = new ListaEnlazada<>();

        if (diskConfig == null) {
            errores.agregar("Falta la sección 'diskConfig'.");
            return errores;
        }
        int totalBloques = diskConfig.getTotalBlocks();
        int cabezal = diskConfig.getInitialHead();
        if (totalBloques <= 0) {
            errores.agregar("'diskConfig.totalBlocks' debe ser mayor que cero (actual: " + totalBloques + ").");
        }
        if (cabezal < 0 || (totalBloques > 0 && cabezal >= totalBloques)) {
            errores.agregar("'diskConfig.initialHead' (" + cabezal
                    + ") debe estar entre 0 y " + (totalBloques - 1) + ".");
        }

        if (fileSystem == null) {
            errores.agregar("Falta el arreglo 'fileSystem'.");
            return errores;
        }

        boolean[] bloquesUsados = (totalBloques > 0) ? new boolean[totalBloques] : new boolean[0];
        ListaEnlazada<String> rutasVistas = new ListaEnlazada<>();

        Iterador<EntradaArchivo> it = fileSystem.iterador();
        int idx = 0;
        while (it.haySiguiente()) {
            EntradaArchivo e = it.siguiente();
            idx++;
            if (e == null) {
                errores.agregar("La entrada #" + idx + " de 'fileSystem' es nula.");
                continue;
            }
            if (e.getPath() == null || e.getPath().trim().isEmpty()) {
                errores.agregar("La entrada #" + idx + " de 'fileSystem' no tiene 'path'.");
                continue;
            }

            String norm;
            try {
                norm = SistemaArchivos.normalizarRuta(e.getPath());
            } catch (RuntimeException ex) {
                errores.agregar("Ruta inválida en 'fileSystem': " + e.getPath());
                continue;
            }

            if (contieneTexto(rutasVistas, norm)) {
                errores.agregar("Ruta duplicada en 'fileSystem': '" + norm + "'.");
            } else {
                rutasVistas.agregar(norm);
            }

            TipoNodoArchivo tipo = TipoNodoArchivo.desdeTexto(e.getType());
            if (tipo == TipoNodoArchivo.ARCHIVO) {
                if (e.getStartBlock() == null || e.getBlocks() == null) {
                    errores.agregar("El archivo '" + norm + "' debe indicar 'startBlock' y 'blocks'.");
                    continue;
                }
                int inicio = e.getStartBlock();
                int cant = e.getBlocks();
                if (cant <= 0) {
                    errores.agregar("El archivo '" + norm + "' debe ocupar al menos 1 bloque.");
                } else if (inicio < 0 || (totalBloques > 0 && inicio + cant > totalBloques)) {
                    errores.agregar("El archivo '" + norm + "' [" + inicio + ".." + (inicio + cant - 1)
                            + "] excede 'totalBlocks' (" + totalBloques + ").");
                } else {
                    for (int b = inicio; b < inicio + cant; b++) {
                        if (bloquesUsados[b]) {
                            errores.agregar("Solapamiento de bloques en el bloque " + b
                                    + " al validar el archivo '" + norm + "'.");
                            break;
                        }
                        bloquesUsados[b] = true;
                    }
                }
            }
        }

        return errores;
    }

    public void validarEstricto() {
        ListaEnlazada<String> errs = validar();
        if (!errs.estaVacia()) {
            StringBuilder sb = new StringBuilder("JSON de Sistema de Archivos inválido:\n");
            Iterador<String> it = errs.iterador();
            while (it.haySiguiente()) {
                sb.append(" • ").append(it.siguiente()).append("\n");
            }
            throw new IllegalArgumentException(sb.toString().trim());
        }
    }

    /**
     * Aplica todas las entradas del JSON al {@link DiscoSimulado} y {@link SistemaArchivos}.
     * Carga primero los directorios para respetar sus dueños y permisos antes de crear los archivos.
     */
    public void aplicarADiscoYSistema(DiscoSimulado disco, SistemaArchivos sa) {
        if (disco == null || sa == null) {
            throw new IllegalArgumentException("Disco y SistemaArchivos no pueden ser nulos.");
        }
        validarEstricto();

        // 1. Directorios primero
        Iterador<EntradaArchivo> itDir = fileSystem.iterador();
        while (itDir.haySiguiente()) {
            EntradaArchivo e = itDir.siguiente();
            if (TipoNodoArchivo.desdeTexto(e.getType()) == TipoNodoArchivo.DIRECTORIO) {
                sa.cargarEntradaPrecargada(
                        e.getPath(),
                        TipoNodoArchivo.DIRECTORIO,
                        0,
                        0,
                        (e.getOwner() != null && !e.getOwner().isBlank()) ? e.getOwner() : "admin",
                        PermisoArchivo.desdeTexto(e.getPermissions()));
            }
        }

        // 2. Archivos después
        Iterador<EntradaArchivo> itArch = fileSystem.iterador();
        while (itArch.haySiguiente()) {
            EntradaArchivo e = itArch.siguiente();
            if (TipoNodoArchivo.desdeTexto(e.getType()) == TipoNodoArchivo.ARCHIVO) {
                sa.cargarEntradaPrecargada(
                        e.getPath(),
                        TipoNodoArchivo.ARCHIVO,
                        e.getStartBlock(),
                        e.getBlocks(),
                        (e.getOwner() != null && !e.getOwner().isBlank()) ? e.getOwner() : "admin",
                        PermisoArchivo.desdeTexto(e.getPermissions()));
            }
        }
    }

    /**
     * Extrae el estado actual de un {@link SistemaArchivos} y su {@link DiscoSimulado}
     * para poder guardarlo en formato JSON y recargarlo en futuras ejecuciones.
     */
    public static ConfigSistemaArchivos desdeSistemaArchivos(SistemaArchivos sa) {
        if (sa == null || sa.getDisco() == null) {
            throw new IllegalArgumentException("El SistemaArchivos y su DiscoSimulado no pueden ser nulos.");
        }
        DiscoSimulado disco = sa.getDisco();
        DiskConfig dc = new DiskConfig(disco.getTotalBloquesDatos(), disco.getPosicionCabezal());
        ListaEnlazada<EntradaArchivo> entradas = new ListaEnlazada<>();

        ListaEnlazada<NodoArchivo> nodos = sa.listarEntradasSinRaiz();
        Iterador<NodoArchivo> it = nodos.iterador();
        while (it.haySiguiente()) {
            NodoArchivo n = it.siguiente();
            if (n.esDirectorio()) {
                entradas.agregar(new EntradaArchivo(
                        n.getRuta(),
                        TipoNodoArchivo.DIRECTORIO.getCodigoJson(),
                        null,
                        null,
                        n.getDueno(),
                        n.getPermiso().getCodigoJson()));
            } else {
                entradas.agregar(new EntradaArchivo(
                        n.getRuta(),
                        TipoNodoArchivo.ARCHIVO.getCodigoJson(),
                        n.getPrimerBloque(),
                        n.getTamanoBloques(),
                        n.getDueno(),
                        n.getPermiso().getCodigoJson()));
            }
        }
        return new ConfigSistemaArchivos(dc, entradas);
    }

    private static boolean contieneTexto(ListaEnlazada<String> lista, String objetivo) {
        Iterador<String> it = lista.iterador();
        while (it.haySiguiente()) {
            if (objetivo.equals(it.siguiente())) {
                return true;
            }
        }
        return false;
    }
}
