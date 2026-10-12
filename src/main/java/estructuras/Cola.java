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

    // Verifica si la cola esta vacia
    public boolean estaVacia() {
        return this.frente == null;
    }

    // Obtiene la cantidad de elementos en la cola
    public int getTamano() {
        return this.tamano;
    }
}