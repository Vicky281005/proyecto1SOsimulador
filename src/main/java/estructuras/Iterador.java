package estructuras;

/**
 * Iterador propio de ÁvilaOS para recorrer las estructuras enlazadas en un
 * único paso O(n), sin exponer los nodos internos y sin depender de las
 * colecciones de java.util.
 */
public interface Iterador<T> {
    boolean haySiguiente();
    T siguiente();
}
