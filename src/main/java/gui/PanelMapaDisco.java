package gui;

import disco.BloqueDisco;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;

/**
 * Mapa del disco: una celda por bloque, en filas de {@value #COLUMNAS}. Colorea los
 * bloques de archivo con el color de su archivo, distingue el área swap (libre u
 * ocupada), marca la posición del cabezal (borde rojo), el destino de la solicitud
 * en curso (borde azul) y los destinos de las solicitudes en espera (punto negro).
 *
 * <p>Solo dibuja la copia que se le entrega con {@link #actualizar}; no toca el disco.</p>
 */
public class PanelMapaDisco extends JPanel {

    public static final int COLUMNAS = 20;
    private static final int CELDA = 28;

    static final Color COLOR_LIBRE = new Color(238, 238, 238);
    static final Color COLOR_SWAP_LIBRE = new Color(255, 236, 200);
    static final Color COLOR_SWAP_OCUPADO = new Color(230, 126, 34);
    static final Color COLOR_ARCHIVO_DEFECTO = new Color(52, 152, 219);
    static final Color COLOR_CABEZAL = new Color(200, 30, 30);
    static final Color COLOR_DESTINO = new Color(20, 80, 220);

    private BloqueDisco[] bloques = new BloqueDisco[0];
    private int cabezal = -1;
    private int destinoActual = -1;
    private int cantidadActual = 0;
    private boolean[] enEspera = new boolean[0];

    public PanelMapaDisco() {
        setBackground(Color.WHITE);
        setToolTipText("");
    }

    /**
     * @param copia         copia instantánea de los bloques.
     * @param cabezal       bloque donde está el cabezal.
     * @param destinoActual bloque destino de la solicitud en curso, o -1.
     * @param cantidadActual cantidad de bloques de esa solicitud.
     * @param destinosEspera bloques destino de las solicitudes en espera (puede ser null).
     */
    public void actualizar(ListaEnlazada<BloqueDisco> copia, int cabezal, int destinoActual,
                           int cantidadActual, int[] destinosEspera) {
        BloqueDisco[] nuevos = new BloqueDisco[copia.getTamano()];
        int i = 0;
        Iterador<BloqueDisco> it = copia.iterador();
        while (it.haySiguiente()) {
            nuevos[i++] = it.siguiente();
        }
        boolean[] marcas = new boolean[nuevos.length];
        if (destinosEspera != null) {
            for (int d : destinosEspera) {
                if (d >= 0 && d < marcas.length) {
                    marcas[d] = true;
                }
            }
        }
        if (nuevos.length != bloques.length) {
            revalidate();
        }
        this.bloques = nuevos;
        this.cabezal = cabezal;
        this.destinoActual = destinoActual;
        this.cantidadActual = cantidadActual;
        this.enEspera = marcas;
        repaint();
    }

    private int filas() {
        return Math.max(1, (bloques.length + COLUMNAS - 1) / COLUMNAS);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(COLUMNAS * CELDA + 1, filas() * CELDA + 1);
    }

    /** Color de relleno de un bloque (visible para las pruebas). */
    static Color colorDe(BloqueDisco b) {
        if (b.isAreaSwap()) {
            return b.isOcupadoPorSwap() ? COLOR_SWAP_OCUPADO : COLOR_SWAP_LIBRE;
        }
        if (!b.isOcupado()) {
            return COLOR_LIBRE;
        }
        String hex = b.getColorHex();
        if (hex != null && !hex.isEmpty()) {
            try {
                return Color.decode(hex.startsWith("#") ? hex : "#" + hex);
            } catch (NumberFormatException ex) {
                // se usa el color por defecto
            }
        }
        return COLOR_ARCHIVO_DEFECTO;
    }

    private int celdaActual() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 640 || bloques.length == 0) {
            return CELDA;
        }
        int porAncho = (w - 2) / COLUMNAS;
        int porAlto = (h - 2) / filas();
        return Math.max(CELDA, Math.min(porAncho, porAlto));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int celda = celdaActual();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(9, celda / 3)));
        for (int n = 0; n < bloques.length; n++) {
            int x = (n % COLUMNAS) * celda;
            int y = (n / COLUMNAS) * celda;
            g2.setColor(colorDe(bloques[n]));
            g2.fillRect(x + 1, y + 1, celda - 2, celda - 2);
            g2.setColor(new Color(0, 0, 0, 70));
            g2.drawRect(x + 1, y + 1, celda - 3, celda - 3);
            g2.setColor(bloques[n].isOcupado() ? Color.WHITE : Color.DARK_GRAY);
            g2.drawString(String.valueOf(n), x + 4, y + celda - 5);
            if (enEspera[n]) {
                g2.setColor(Color.BLACK);
                g2.fillOval(x + celda - 10, y + 4, 6, 6);
            }
        }
        if (destinoActual >= 0) {
            g2.setColor(COLOR_DESTINO);
            g2.setStroke(new BasicStroke(3f));
            for (int k = 0; k < Math.max(1, cantidadActual); k++) {
                int n = destinoActual + k;
                if (n < bloques.length) {
                    g2.drawRect((n % COLUMNAS) * celda + 2, (n / COLUMNAS) * celda + 2, celda - 4, celda - 4);
                }
            }
        }
        if (cabezal >= 0 && cabezal < bloques.length) {
            g2.setColor(COLOR_CABEZAL);
            g2.setStroke(new BasicStroke(4f));
            g2.drawRect((cabezal % COLUMNAS) * celda + 2, (cabezal / COLUMNAS) * celda + 2, celda - 4, celda - 4);
        }
        g2.dispose();
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int celda = celdaActual();
        int col = e.getX() / celda;
        int fila = e.getY() / celda;
        int n = fila * COLUMNAS + col;
        if (col >= COLUMNAS || n < 0 || n >= bloques.length) {
            return null;
        }
        BloqueDisco b = bloques[n];
        String estado = b.isAreaSwap()
                ? (b.isOcupadoPorSwap() ? "swap ocupado por " + b.getPropietario() : "swap libre")
                : (b.isOcupado() ? "archivo " + b.getPropietario() : "libre");
        String sig = b.isOcupado() && b.getSiguienteBloque() != BloqueDisco.FIN_CADENA
                ? ", siguiente: " + b.getSiguienteBloque() : "";
        return "Bloque " + n + ": " + estado + sig;
    }
}
