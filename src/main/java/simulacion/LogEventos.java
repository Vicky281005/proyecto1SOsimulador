package simulacion;

import estructuras.Iterador;
import estructuras.ListaEnlazada;

/**
 * Log de eventos con estructura propia (sin java.util collections).
 */
public class LogEventos {

    public enum TipoEvento {
        DESPACHO,
        BLOQUEO,
        DESBLOQUEO,
        CAMBIO_POLITICA,
        DEADLINE_VENCIDO,
        ACCESO_REMOTO,
        ADMISION,
        TERMINADO,
        DESALOJO,
        BUFFER,
        CREACION,
        OTRO
    }

    public static class Evento {
        private final long ciclo;
        private final TipoEvento tipo;
        private final String mensaje;
        private final long timestamp;

        public Evento(long ciclo, TipoEvento tipo, String mensaje) {
            this.ciclo = ciclo;
            this.tipo = tipo;
            this.mensaje = mensaje;
            this.timestamp = System.currentTimeMillis();
        }

        public long getCiclo() {
            return ciclo;
        }

        public TipoEvento getTipo() {
            return tipo;
        }

        public String getMensaje() {
            return mensaje;
        }

        @Override
        public String toString() {
            return "[ciclo " + ciclo + "] " + tipo + " - " + mensaje;
        }
    }

    private final ListaEnlazada<Evento> eventos = new ListaEnlazada<>();
    private final int maxEventos = 1000;
    private final Simulador simulador;

    public LogEventos(Simulador simulador) {
        this.simulador = simulador;
    }

    public synchronized void registrar(TipoEvento tipo, String mensaje) {
        long ciclo = 0;
        if (simulador != null) {
            ciclo = simulador.getCicloActual();
        }
        Evento ev = new Evento(ciclo, tipo, mensaje);
        eventos.agregar(ev);
        while (eventos.getTamano() > maxEventos) {
            eventos.eliminarEn(0);
        }
    }

    public synchronized ListaEnlazada<Evento> getUltimos(int n) {
        ListaEnlazada<Evento> res = new ListaEnlazada<>();
        int tam = eventos.getTamano();
        int desde = Math.max(0, tam - n);
        for (int i = desde; i < tam; i++) {
            res.agregar(eventos.obtener(i));
        }
        return res;
    }

    public synchronized Iterador<Evento> iterador() {
        return eventos.iterador();
    }
}
