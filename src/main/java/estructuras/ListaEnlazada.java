package estructuras;

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
    public void agregar(T dato) {
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
    public void insertar(int indice, T dato) {
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
        } else {
            Nodo<T> anterior = nodoEn(indice - 1);
            nuevoNodo.setSiguiente(anterior.getSiguiente());
            anterior.setSiguiente(nuevoNodo);
        }
        tamano++;
    }

    // Elimina la primera ocurrencia de un dato. Devuelve true si lo elimino.
    public boolean eliminar(T dato) {
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
    public T eliminarEn(int indice) {
        if (indice < 0 || indice >= tamano) {
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
    public T eliminarPrimero() {
        if (estaVacia()) {
            return null;
        }
        return eliminarEn(0);
    }

    public boolean contiene(T dato) {
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
    public T obtener(int indice) {
        if (indice < 0 || indice >= tamano || cabeza == null) {
            return null;
        }
        return nodoEn(indice).getDato();
    }

    public void vaciar() {
        cabeza = null;
        ultimo = null;
        tamano = 0;
    }

    public boolean estaVacia() {
        return cabeza == null;
    }

    public int getTamano() {
        return tamano;
    }

    // Iterador propio: recorrido O(n) sin exponer los nodos internos
    public Iterador<T> iterador() {
        return new Iterador<T>() {
            private Nodo<T> actual = cabeza;

            @Override
            public boolean haySiguiente() {
                return actual != null;
            }

            @Override
            public T siguiente() {
                T dato = actual.getDato();
                actual = actual.getSiguiente();
                return dato;
            }
        };
    }

    // Navega hasta el nodo de una posicion valida (0 <= indice < tamano)
    private Nodo<T> nodoEn(int indice) {
        Nodo<T> actual = cabeza;
        for (int i = 0; i < indice; i++) {
            actual = actual.getSiguiente();
        }
        return actual;
    }

    private boolean sonIguales(T a, T b) {
        return (a == null) ? (b == null) : a.equals(b);
    }
}
