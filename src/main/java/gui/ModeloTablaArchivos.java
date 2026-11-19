package gui;

import archivos.NodoArchivo;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Modelo de tabla para la asignación en tiempo real de archivos del {@link archivos.SistemaArchivos}.
 * Utiliza exclusivamente {@link ListaEnlazada} (cero colecciones {@code java.util}).
 */
public class ModeloTablaArchivos extends AbstractTableModel {

    private static final String[] COLUMNAS = {
        "Color", "Archivo / Ruta", "Bloques", "Primer bloque", "Dueño", "Permisos"
    };

    private ListaEnlazada<NodoArchivo> filas = new ListaEnlazada<>();

    /** Reemplaza las filas mostradas cuando cambia la instantánea del sistema de archivos. */
    public void actualizarFilas(ListaEnlazada<NodoArchivo> nuevasFilas) {
        this.filas = (nuevasFilas != null) ? nuevasFilas : new ListaEnlazada<>();
        fireTableDataChanged();
    }

    /** Devuelve el {@link NodoArchivo} correspondiente a una fila de la tabla, o {@code null}. */
    public NodoArchivo obtenerEnFila(int fila) {
        if (fila < 0 || fila >= filas.getTamano()) {
            return null;
        }
        return filas.obtener(fila);
    }

    /** Busca el índice de fila correspondiente a una ruta de archivo, o {@code -1}. */
    public int buscarFilaPorRuta(String ruta) {
        if (ruta == null) {
            return -1;
        }
        int idx = 0;
        Iterador<NodoArchivo> it = filas.iterador();
        while (it.haySiguiente()) {
            NodoArchivo n = it.siguiente();
            if (n != null && ruta.equals(n.getRuta())) {
                return idx;
            }
            idx++;
        }
        return -1;
    }

    @Override
    public int getRowCount() {
        return filas.getTamano();
    }

    @Override
    public int getColumnCount() {
        return COLUMNAS.length;
    }

    @Override
    public String getColumnName(int col) {
        return COLUMNAS[col];
    }

    @Override
    public Class<?> getColumnClass(int col) {
        if (col == 0) {
            return Color.class;
        }
        if (col == 2 || col == 3) {
            return Integer.class;
        }
        return String.class;
    }

    @Override
    public Object getValueAt(int fila, int col) {
        NodoArchivo n = filas.obtener(fila);
        return switch (col) {
            case 0 -> decodificarColor(n.getColorHex());
            case 1 -> n.getRuta();
            case 2 -> n.getTamanoBloques();
            case 3 -> n.getPrimerBloque();
            case 4 -> n.getDueno();
            case 5 -> n.getPermiso() != null ? n.getPermiso().getEtiqueta() : "-";
            default -> "";
        };
    }

    /** Decodifica un color hexadecimal del disco con respaldo por defecto. */
    public static Color decodificarColor(String hex) {
        if (hex != null && !hex.trim().isEmpty()) {
            String limpio = hex.trim();
            try {
                return Color.decode(limpio.startsWith("#") ? limpio : "#" + limpio);
            } catch (NumberFormatException ignored) {
                // Usa el color por defecto si el formato no es válido
            }
        }
        return PanelMapaDisco.COLOR_ARCHIVO_DEFECTO;
    }

    /** Icono cuadrado relleno del color asignado al archivo en el mapa de bloques del disco. */
    public static Icon crearIconoColor(Color color, int ancho, int alto) {
        final Color c = (color != null) ? color : PanelMapaDisco.COLOR_ARCHIVO_DEFECTO;
        return new Icon() {
            @Override
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c);
                g2.fillRoundRect(x, y, ancho - 1, alto - 1, 4, 4);
                g2.setColor(new Color(60, 60, 60));
                g2.drawRoundRect(x, y, ancho - 1, alto - 1, 4, 4);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return ancho;
            }

            @Override
            public int getIconHeight() {
                return alto;
            }
        };
    }

    /** Renderizador centrado para la columna de color de archivo. */
    public static final class RenderizadorColor extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                      boolean hasFocus, int row, int column) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(
                    table, "", isSelected, hasFocus, row, column);
            lbl.setHorizontalAlignment(SwingConstants.CENTER);
            Color color = (value instanceof Color c) ? c : PanelMapaDisco.COLOR_ARCHIVO_DEFECTO;
            lbl.setIcon(crearIconoColor(color, 18, 13));
            lbl.setToolTipText(String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue()));
            return lbl;
        }
    }
}
