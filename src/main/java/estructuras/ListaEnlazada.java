package estructuras;

/**
 * Lista simplemente enlazada propia (sin colecciones de java.util).
 *
 * <p>Todos los métodos de acceso y modificación están sincronizados para
 * garantizar seguridad entre hilos concurrentes (simulación y GUI).</p>
 *
 * @param <T> tipo de dato contenido.
 */
public class ListaEnlazada<T> {
    private Nodo<T> cabeza;
    private Nodo<T> ultimo; // Puntero al final para agregar en O(1)
    private int tamano;

    public ListaEnlazada() {
        this.cabeza = null;
        this.ultimo = null;
        this.tamano = 0;
    }

    // Agrega un elemento al final de la lista en O(1)
    public synchronized void agregar(T dato) {
        Nodo<T> nuevoNodo = new Nodo<>(dato);
        if (cabeza == null) {
            cabeza = nuevoNodo;
        } else {
            ultimo.setSiguiente(nuevoNodo);
        }
        ultimo = nuevoNodo;
        tamano++;
    }

    // Inserta un elemento en una posicion especifica (0-indexed)
    public synchronized void insertar(int indice, T dato) {
        if (indice < 0 || indice > tamano) {
            throw new IndexOutOfBoundsException("Indice fuera de rango: " + indice);
        }
        if (indice == tamano) {
            agregar(dato);
            return;
        }
        Nodo<T> nuevoNodo = new Nodo<>(dato);
        if (indice == 0) {
            nuevoNodo.setSiguiente(cabeza);
            cabeza = nuevoNodo;
            if (ultimo == null) {
                ultimo = nuevoNodo;
            }
        } else {
            Nodo<T> anterior = nodoEn(indice - 1);
            if (anterior != null) {
                nuevoNodo.setSiguiente(anterior.getSiguiente());
                anterior.setSiguiente(nuevoNodo);
            } else {
                agregar(dato);
                return;
            }
        }
        tamano++;
    }

    // Elimina la primera ocurrencia de un dato. Devuelve true si lo elimino.
    public synchronized boolean eliminar(T dato) {
        if (cabeza == null) {
            return false;
        }
        if (sonIguales(cabeza.getDato(), dato)) {
            cabeza = cabeza.getSiguiente();
            if (cabeza == null) {
                ultimo = null;
            }
            tamano--;
            return true;
        }
        Nodo<T> actual = cabeza;
        while (actual.getSiguiente() != null) {
            if (sonIguales(actual.getSiguiente().getDato(), dato)) {
                if (actual.getSiguiente() == ultimo) {
                    ultimo = actual;
                }
                actual.setSiguiente(actual.getSiguiente().getSiguiente());
                tamano--;
                return true;
            }
            actual = actual.getSiguiente();
        }
        return false;
    }

    // Elimina el elemento en una posicion especifica y lo devuelve
    public synchronized T eliminarEn(int indice) {
        if (indice < 0 || indice >= tamano || cabeza == null) {
            throw new IndexOutOfBoundsException("Indice fuera de rango: " + indice);
        }
        T dato;
        if (indice == 0) {
            dato = cabeza.getDato();
            cabeza = cabeza.getSiguiente();
            if (cabeza == null) {
                ultimo = null;
            }
        } else {
            Nodo<T> anterior = nodoEn(indice - 1);
            if (anterior == null || anterior.getSiguiente() == null) {
                return null;
            }
            Nodo<T> objetivo = anterior.getSiguiente();
            dato = objetivo.getDato();
            anterior.setSiguiente(objetivo.getSiguiente());
            if (objetivo == ultimo) {
                ultimo = anterior;
            }
        }
        tamano--;
        return dato;
    }

    // Elimina y devuelve el primer elemento en O(1)
    public synchronized T eliminarPrimero() {
        if (estaVacia()) {
            return null;
        }
        return eliminarEn(0);
    }

    public synchronized boolean contiene(T dato) {
        Nodo<T> actual = cabeza;
        while (actual != null) {
            if (sonIguales(actual.getDato(), dato)) {
                return true;
            }
            actual = actual.getSiguiente();
        }
        return false;
    }

    // Obtiene el dato guardado en un indice especifico (0-indexed)
    public synchronized T obtener(int indice) {
        if (indice < 0 || indice >= tamano || cabeza == null) {
            return null;
        }
        Nodo<T> nodo = nodoEn(indice);
        return (nodo != null) ? nodo.getDato() : null;
    }

    /**
     * Obtiene el dato en el índice indicado, soportando tanto índice base 0 (0 <= indice < tamano)
     * como identificador/posición base 1 (1 <= indice <= tamano). Valida que el nodo exista
     * antes de invocar getDato() para prevenir NullPointerException.
     *
     * @param indice identificador o índice numérico.
     * @return el dato encontrado, o null si está fuera de rango o el nodo es nulo.
     */
    public synchronized T obtenerSeguro(int indice) {
        if (estaVacia()) {
            return null;
        }
        // 1. Probar índice directo base 0
        if (indice >= 0 && indice < tamano) {
            Nodo<T> n = nodoEn(indice);
            if (n != null) {
                return n.getDato();
            }
        }
        // 2. Probar base 1 (indice - 1)
        if (indice > 0 && (indice - 1) < tamano) {
            Nodo<T> n = nodoEn(indice - 1);
            if (n != null) {
                return n.getDato();
            }
        }
        return null;
    }

    /**
     * Devuelve el primer elemento sin eliminarlo.
     *
     * @return primer elemento, o {@code null} si la lista está vacía.
     */
    public synchronized T obtenerPrimero() {
        if (cabeza == null) {
            return null;
        }
        return cabeza.getDato();
    }

    public synchronized void vaciar() {
        cabeza = null;
        ultimo = null;
        tamano = 0;
    }

    public synchronized boolean estaVacia() {
        return cabeza == null || tamano == 0;
    }

    public synchronized int getTamano() {
        return tamano;
    }

    // Iterador propio con snapshot defensivo: recorrido O(n) seguro contra modificaciones concurrentes
    public synchronized Iterador<T> iterador() {
        final Object[] elementos = new Object[tamano];
        Nodo<T> act = cabeza;
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

    // Navega hasta el nodo de una posicion valida (0 <= indice < tamano)
    private synchronized Nodo<T> nodoEn(int indice) {
        if (indice < 0 || indice >= tamano || cabeza == null) {
            return null;
        }
        Nodo<T> actual = cabeza;
        for (int i = 0; i < indice && actual != null; i++) {
            actual = actual.getSiguiente();
        }
        return actual;
    }

    private boolean sonIguales(T a, T b) {
        return (a == null) ? (b == null) : a.equals(b);
    }
}
