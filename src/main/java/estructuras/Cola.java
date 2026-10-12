package estructuras;

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
    public void encolar(T dato) {
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
    public T desencolar() {
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
    public T frente() {
        if (estaVacia()) {
            return null;
        }
        return this.frente.getDato();
    }

    // Observa el elemento en una posicion sin alterar la cola
    public T obtener(int indice) {
        if (indice < 0 || indice >= tamano) {
            return null;
        }
        Nodo<T> actual = this.frente;
        for (int i = 0; i < indice; i++) {
            actual = actual.getSiguiente();
        }
        return actual.getDato();
    }

    public boolean contiene(T dato) {
        Nodo<T> actual = this.frente;
        while (actual != null) {
            if (sonIguales(actual.getDato(), dato)) {
                return true;
            }
            actual = actual.getSiguiente();
        }
        return false;
    }

    public void vaciar() {
        this.frente = null;
        this.fin = null;
        this.tamano = 0;
    }

    // Verifica si la cola esta vacia
    public boolean estaVacia() {
        return this.frente == null;
    }

    // Obtiene la cantidad de elementos en la cola
    public int getTamano() {
        return this.tamano;
    }

    // Iterador propio para que la GUI recorra la cola en un solo paso O(n)
    public Iterador<T> iterador() {
        return new Iterador<T>() {
            private Nodo<T> actual = frente;

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

    private boolean sonIguales(T a, T b) {
        return (a == null) ? (b == null) : a.equals(b);
    }
}
