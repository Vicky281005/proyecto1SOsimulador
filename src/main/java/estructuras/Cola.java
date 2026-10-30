package estructuras;

/**
 * Cola FIFO de ÁvilaOS con seguridad de hilos (thread-safe).
 *
 * <p>Todos los métodos de acceso y modificación están sincronizados mediante
 * monitores de instancia. El iterador realiza una captura defensiva (snapshot)
 * en un arreglo al instanciarse, permitiendo recorridos seguros en tiempo O(n)
 * inmunes a modificaciones concurrentes.</p>
 *
 * @param <T> tipo de elementos en la cola.
 */
public class Cola<T> {
    private Nodo<T> frente;
    private Nodo<T> fin;
    private int tamano;

    public Cola() {
        this.frente = null;
        this.fin = null;
        this.tamano = 0;
    }

    // Agrega un elemento al final de la cola
    public synchronized void encolar(T dato) {
        Nodo<T> nuevoNodo = new Nodo<>(dato);
        if (estaVacia()) {
            this.frente = nuevoNodo;
        } else {
            this.fin.setSiguiente(nuevoNodo);
        }
        this.fin = nuevoNodo;
        this.tamano++;
    }

    // Elimina y devuelve el elemento al frente de la cola
    public synchronized T desencolar() {
        if (estaVacia()) {
            return null;
        }
        T dato = this.frente.getDato();
        this.frente = this.frente.getSiguiente();
        if (this.frente == null) {
            this.fin = null;
        }
        this.tamano--;
        return dato;
    }

    // Muestra el elemento al frente sin eliminarlo
    public synchronized T frente() {
        if (estaVacia()) {
            return null;
        }
        return this.frente.getDato();
    }

    // Observa el elemento en una posicion sin alterar la cola
    public synchronized T obtener(int indice) {
        if (indice < 0 || indice >= tamano) {
            return null;
        }
        Nodo<T> actual = this.frente;
        for (int i = 0; i < indice; i++) {
            actual = actual.getSiguiente();
        }
        return actual.getDato();
    }

    public synchronized boolean contiene(T dato) {
        Nodo<T> actual = this.frente;
        while (actual != null) {
            if (sonIguales(actual.getDato(), dato)) {
                return true;
            }
            actual = actual.getSiguiente();
        }
        return false;
    }

    public synchronized void vaciar() {
        this.frente = null;
        this.fin = null;
        this.tamano = 0;
    }

    // Verifica si la cola esta vacia
    public synchronized boolean estaVacia() {
        return this.frente == null;
    }

    // Obtiene la cantidad de elementos en la cola
    public synchronized int getTamano() {
        return this.tamano;
    }

    // Iterador propio con snapshot defensivo: recorrido O(n) seguro contra modificaciones concurrentes
    public synchronized Iterador<T> iterador() {
        final Object[] elementos = new Object[tamano];
        Nodo<T> act = frente;
        int idx = 0;
        while (act != null && idx < tamano) {
            elementos[idx++] = act.getDato();
            act = act.getSiguiente();
        }
        final int total = idx;
        return new Iterador<T>() {
            private int cursor = 0;

            @Override
            public boolean haySiguiente() {
                return cursor < total;
            }

            @SuppressWarnings("unchecked")
            @Override
            public T siguiente() {
                if (!haySiguiente()) {
                    return null;
                }
                return (T) elementos[cursor++];
            }
        };
    }

    private boolean sonIguales(T a, T b) {
        return (a == null) ? (b == null) : a.equals(b);
    }
}
