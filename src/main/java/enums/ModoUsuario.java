package enums;

/**
 * Modo de operación de la sesión de usuario en ÁvilaOS.
 *
 * <p>En {@link #ADMINISTRADOR} se permiten todas las operaciones sobre archivos,
 * directorios, procesos y políticas. En {@link #USUARIO} solo se permite leer
 * archivos propios o públicos y crear procesos de E/S sobre archivos propios.</p>
 */
public enum ModoUsuario {
    ADMINISTRADOR("Administrador"),
    USUARIO("Usuario");

    private final String etiqueta;

    ModoUsuario(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public boolean esAdministrador() {
        return this == ADMINISTRADOR;
    }

    public static ModoUsuario desdeTexto(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return ADMINISTRADOR;
        }
        String limpio = texto.trim().toUpperCase();
        if (limpio.startsWith("USER") || limpio.startsWith("USU")) {
            return USUARIO;
        }
        return ADMINISTRADOR;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
