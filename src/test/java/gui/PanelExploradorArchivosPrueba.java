package gui;

import archivos.SistemaArchivos;
import config.ConfigProcesosDisco;
import config.ConfigSistemaArchivos;
import config.GestorPersistencia;
import enums.ModoUsuario;
import enums.TipoOperacionES;
import java.awt.Color;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import simulacion.Simulador;

/**
 * Pruebas unitarias y de componente Swing para {@link PanelExploradorArchivos}
 * y {@link ModeloTablaArchivos} (cero colecciones {@code java.util}).
 */
public class PanelExploradorArchivosPrueba {

    private static int ok = 0;
    private static int fallos = 0;

    private static void verificar(boolean c, String msg) {
        if (c) {
            ok++;
            System.out.println("  OK    " + msg);
        } else {
            fallos++;
            System.err.println("  FALLA " + msg);
        }
    }

    private static String jsonFS() {
        return """
                {
                  "diskConfig": { "totalBlocks": 200, "initialHead": 50 },
                  "fileSystem": [
                    { "path": "/mbr.bin", "type": "FILE", "startBlock": 0, "blocks": 11, "owner": "admin", "permissions": "public" },
                    { "path": "/sistema", "type": "DIRECTORY", "owner": "admin", "permissions": "public" },
                    { "path": "/usuarios", "type": "DIRECTORY", "owner": "admin", "permissions": "public" },
                    { "path": "/sistema/boot.bin", "type": "FILE", "startBlock": 11, "blocks": 23, "owner": "admin", "permissions": "public" },
                    { "path": "/usuarios/notas.txt", "type": "FILE", "startBlock": 34, "blocks": 28, "owner": "user1", "permissions": "private" },
                    { "path": "/sistema/script.py", "type": "FILE", "startBlock": 62, "blocks": 2, "owner": "admin", "permissions": "public" },
                    { "path": "/sistema/core.dll", "type": "FILE", "startBlock": 64, "blocks": 31, "owner": "admin", "permissions": "public" },
                    { "path": "/sistema/config.sys", "type": "FILE", "startBlock": 95, "blocks": 24, "owner": "admin", "permissions": "public" },
                    { "path": "/usuarios/img1.png", "type": "FILE", "startBlock": 119, "blocks": 4, "owner": "user1", "permissions": "private" },
                    { "path": "/usuarios/data.csv", "type": "FILE", "startBlock": 123, "blocks": 57, "owner": "user2", "permissions": "public" },
                    { "path": "/usuarios/video.mp4", "type": "FILE", "startBlock": 180, "blocks": 20, "owner": "user1", "permissions": "public" }
                  ]
                }
                """;
    }

    private static String jsonProc() {
        return """
                {
                  "globalConfig": { "cycleDurationMs": 50, "swapSizeBlocks": 100, "initialDiskPolicy": "FIFO" },
                  "computer": { "ramCapacity": 1024, "policy": "FCFS", "quantum": 0 },
                  "processes": [
                    { "name": "Proc_119", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/usuarios/img1.png", "operation": "DELETE" }
                  ]
                }
                """;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== PRUEBAS DE PanelExploradorArchivos (gui-archivos-tabla) ===");

        ConfigSistemaArchivos fsCfg = GestorPersistencia.cargarSistemaArchivosDesdeTexto(jsonFS());
        ConfigProcesosDisco procCfg = GestorPersistencia.cargarProcesosDesdeTexto(jsonProc());
        Simulador simVacio = new Simulador();
        Simulador simActivo = GestorPersistencia.construirSimuladorDesdeDosJSON(fsCfg, procCfg);

        SwingUtilities.invokeAndWait(() -> {
            PanelExploradorArchivos panelVacio = new PanelExploradorArchivos(simVacio);
            verificar(panelVacio.getTablaArchivos().getRowCount() == 0, "Sin FS cargado la tabla está vacía");

            PanelExploradorArchivos panel = new PanelExploradorArchivos(simActivo);
            JTable tabla = panel.getTablaArchivos();
            JTree arbol = panel.getArbolArchivos();

            verificar(tabla.getRowCount() == 9, "Tabla muestra exactamente los 9 archivos cargados del JSON");
            verificar(tabla.getColumnCount() == 7 && "Proceso creador".equals(tabla.getColumnName(6)),
                    "Tabla incluye la columna requerida 'Proceso creador'");
            verificar("Precarga JSON".equals(tabla.getValueAt(0, 6)),
                    "Columna 'Proceso creador' muestra el creador real del archivo");
            verificar(tabla.getValueAt(0, 0) instanceof Color, "Columna Color entrega el Color real del bloque en disco");
            DefaultMutableTreeNode raizSwing = (DefaultMutableTreeNode) arbol.getModel().getRoot();
            verificar(raizSwing.getChildCount() >= 3, "JTree muestra las entradas raíz (/mbr.bin, /sistema, /usuarios)");

            // Cambiar entre Modo Administrador y Modo Usuario
            panel.getComboModo().setSelectedItem(ModoUsuario.USUARIO);
            verificar(panel.getModoSeleccionado() == ModoUsuario.USUARIO, "Selector cambia a Modo Usuario");
            verificar(panel.getTxtUsuario().isEditable() && "user1".equals(panel.getUsuarioActivo()),
                    "Al cambiar a Modo Usuario permite editar el usuario activo (default user1)");

            panel.getComboModo().setSelectedItem(ModoUsuario.ADMINISTRADOR);
            verificar("admin".equals(panel.getUsuarioActivo()) && !panel.getTxtUsuario().isEditable(),
                    "Al volver a Administrador fija 'admin' con acceso completo");

            // Sincronización tabla -> árbol
            tabla.setRowSelectionInterval(0, 0);
            Object ultimoSel = arbol.getLastSelectedPathComponent();
            verificar(ultimoSel instanceof DefaultMutableTreeNode dm && dm.getUserObject() != null,
                    "Seleccionar fila en JTable selecciona su nodo en el JTree");
        });

        // Validaciones de permisos Admin vs Usuario
        SistemaArchivos sa = (SistemaArchivos) simActivo.getServicioArchivos();
        verificar(PanelExploradorArchivos.validarOperacionGUI(
                sa, TipoOperacionES.CREAR, "/nuevoDir", "", 0, ModoUsuario.USUARIO, "user1", true) != null,
                "Modo Usuario rechaza crear directorios");
        verificar(PanelExploradorArchivos.validarOperacionGUI(
                sa, TipoOperacionES.CREAR, "/sistema/miarchivo.txt", "", 2, ModoUsuario.USUARIO, "user1", false) != null,
                "Modo Usuario rechaza crear archivo dentro de directorio de admin");
        verificar(PanelExploradorArchivos.validarOperacionGUI(
                sa, TipoOperacionES.LEER, "/usuarios/notas.txt", "", 0, ModoUsuario.USUARIO, "user2", false) != null,
                "Modo Usuario (user2) rechaza leer archivo privado de user1");
        verificar(PanelExploradorArchivos.validarOperacionGUI(
                sa, TipoOperacionES.LEER, "/usuarios/notas.txt", "", 0, ModoUsuario.USUARIO, "user1", false) == null,
                "Modo Usuario (user1) SÍ puede leer su propio archivo privado");
        verificar(PanelExploradorArchivos.validarOperacionGUI(
                sa, TipoOperacionES.LEER, "/usuarios/notas.txt", "", 0, ModoUsuario.ADMINISTRADOR, "admin", false) == null,
                "Modo Administrador SÍ puede leer archivo privado de cualquier usuario");
        verificar(PanelExploradorArchivos.validarOperacionGUI(
                sa, TipoOperacionES.ELIMINAR, "/sistema/boot.bin", "", 0, ModoUsuario.USUARIO, "user1", false) != null,
                "Modo Usuario rechaza eliminar archivo perteneciente a admin");
        verificar(PanelExploradorArchivos.validarOperacionGUI(
                sa, TipoOperacionES.ELIMINAR, "/sistema", "", 0, ModoUsuario.USUARIO, "user1", true) != null,
                "Modo Usuario rechaza eliminar directorios");
        verificar(PanelExploradorArchivos.validarOperacionGUI(
                sa, TipoOperacionES.ACTUALIZAR, "/usuarios/notas.txt", "notas_v2.txt", 0, ModoUsuario.USUARIO, "user1", false) == null,
                "Modo Usuario (user1) SÍ puede renombrar su propio archivo");
        verificar(PanelExploradorArchivos.validarOperacionGUI(
                sa, TipoOperacionES.ACTUALIZAR, "/usuarios/notas.txt", "a/b.txt", 0, ModoUsuario.USUARIO, "user1", false) != null,
                "Validación rechaza '/' dentro del nuevo nombre al renombrar");
        verificar(PanelExploradorArchivos.validarOperacionGUI(
                sa, TipoOperacionES.ELIMINAR, "/", "", 0, ModoUsuario.ADMINISTRADOR, "admin", true) != null,
                "Validación protege la raíz '/' contra eliminación");

        // Actualización en tiempo real tras ejecutar DELETE
        simActivo.ejecutar();
        SwingUtilities.invokeAndWait(() -> {
            PanelExploradorArchivos panelTrasSim = new PanelExploradorArchivos(simActivo);
            verificar(panelTrasSim.getTablaArchivos().getRowCount() == 8,
                    "Tras ejecutar DELETE de /usuarios/img1.png, la JTable pasa en tiempo real a 8 archivos");
        });

        // Admin asigna un directorio a 'user1' con permiso PRIVADO, y luego 'user1' puede crear archivos dentro
        simActivo.solicitarOperacionES(1, "AdminCreaDirUser1", TipoOperacionES.CREAR,
                "/usuarios/carpeta_u1", "", 0, ModoUsuario.ADMINISTRADOR, "admin",
                enums.PermisoArchivo.PUBLICO); // primero probamos crear asignado a user1 abajo
        simActivo.solicitarOperacionES(1, "AdminAsignaDirUser1", TipoOperacionES.CREAR,
                "/usuarios/u1", "", 0, ModoUsuario.ADMINISTRADOR, "user1",
                enums.PermisoArchivo.PRIVADO);
        simActivo.ejecutar();
        archivos.NodoArchivo dirU1 = sa.buscarPorRuta("/usuarios/u1");
        verificar(dirU1 != null && dirU1.esDirectorio() && "user1".equals(dirU1.getDueno())
                && dirU1.getPermiso() == enums.PermisoArchivo.PRIVADO,
                "Admin puede crear directorio asignado a 'user1' con permiso Privado");
        verificar(PanelExploradorArchivos.validarOperacionGUI(
                sa, TipoOperacionES.CREAR, "/usuarios/u1/mi_doc.txt", "", 2, ModoUsuario.USUARIO, "user1", false) == null,
                "Luego 'user1' en Modo Usuario SÍ puede crear archivo dentro de su directorio asignado");
        verificar(PanelExploradorArchivos.validarOperacionGUI(
                sa, TipoOperacionES.CREAR, "/usuarios/u1/intruso.txt", "", 2, ModoUsuario.USUARIO, "user2", false) != null,
                "'user2' en Modo Usuario NO puede crear archivo dentro del directorio de 'user1'");

        SwingUtilities.invokeAndWait(() -> {
            PanelExploradorArchivos panelTrasSim = new PanelExploradorArchivos(simActivo);

            try {
                java.lang.reflect.Field fBtnAct = PanelExploradorArchivos.class.getDeclaredField("btnRenombrar");
                fBtnAct.setAccessible(true);
                javax.swing.JButton btnAct = (javax.swing.JButton) fBtnAct.get(panelTrasSim);
                verificar("Actualizar…".equals(btnAct.getText()),
                        "El botón de operación UPDATE dice literalmente 'Actualizar…'");

                nucleo.Computador pc = simActivo.buscarComputador(1);
                PanelComputador pPc = new PanelComputador(pc, simActivo);
                java.lang.reflect.Field fPolPc = PanelComputador.class.getDeclaredField("comboPolitica");
                fPolPc.setAccessible(true);
                javax.swing.JComboBox<?> comboPc = (javax.swing.JComboBox<?>) fPolPc.get(pPc);
                pPc.setModoUsuario(ModoUsuario.USUARIO);
                verificar(!comboPc.isEnabled(), "En Modo Usuario se bloquea el cambio de política de CPU");
                pPc.setModoUsuario(ModoUsuario.ADMINISTRADOR);
                verificar(comboPc.isEnabled(), "En Modo Administrador se habilita el cambio de política de CPU");

                if (!java.awt.GraphicsEnvironment.isHeadless()) {
                    VentanaDisco vd = new VentanaDisco(null, simActivo);
                    java.lang.reflect.Field fPolDisco = VentanaDisco.class.getDeclaredField("comboPolitica");
                    fPolDisco.setAccessible(true);
                    java.lang.reflect.Field fVelDisco = VentanaDisco.class.getDeclaredField("spinVelocidad");
                    fVelDisco.setAccessible(true);
                    javax.swing.JComboBox<?> comboDisco = (javax.swing.JComboBox<?>) fPolDisco.get(vd);
                    javax.swing.JSpinner spinDisco = (javax.swing.JSpinner) fVelDisco.get(vd);
                    vd.setModoUsuario(ModoUsuario.USUARIO);
                    verificar(!comboDisco.isEnabled() && !spinDisco.isEnabled(),
                            "En Modo Usuario se bloquea el cambio de política y velocidad de disco");
                    vd.setModoUsuario(ModoUsuario.ADMINISTRADOR);
                    verificar(comboDisco.isEnabled() && spinDisco.isEnabled(),
                            "En Modo Administrador se habilita el cambio de política y velocidad de disco");
                    vd.dispose();
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });

        System.out.println("\n  Total: " + (ok + fallos) + " | Exitosas: " + ok + " | Fallidas: " + fallos);
        if (fallos > 0) {
            System.exit(1);
        }
    }
}
