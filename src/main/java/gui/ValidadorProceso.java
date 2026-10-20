package gui;

import enums.TipoInstruccion;
import enums.TipoProceso;
import estructuras.ListaEnlazada;
import modelo.Instruccion;
import modelo.PCB;
import nucleo.Computador;

/**
 * Validación y construcción de procesos a partir de lo que el usuario escribe
 * en el formulario de la Vista 1. No usa Swing: recibe los textos tal cual los
 * escribió el usuario, de modo que se puede probar sin abrir ninguna ventana.
 *
 * <p>Cada campo tiene su propio mensaje de error (o {@code null} si es válido),
 * para que el formulario pueda marcar exactamente los campos incorrectos.</p>
 */
public final class ValidadorProceso {

    // Índices de los campos, para leer/escribir el arreglo de errores.
    public static final int CAMPO_NOMBRE = 0;
    public static final int CAMPO_INSTRUCCIONES = 1;
    public static final int CAMPO_MEMORIA = 2;
    public static final int CAMPO_PRIORIDAD = 3;
    public static final int CAMPO_TIPO = 4;
    public static final int CAMPO_DEADLINE = 5;
    public static final int CAMPO_COMPUTADOR = 6;
    public static final int CAMPO_PERIODO_ES = 7;
    public static final int CANTIDAD_CAMPOS = 8;

    // Rangos aceptados.
    public static final int MAX_LONGITUD_NOMBRE = 30;
    public static final int MIN_INSTRUCCIONES = 1;
    public static final int MAX_INSTRUCCIONES = 1000;
    public static final int PRIORIDAD_MIN = 1;
    public static final int PRIORIDAD_MAX = 10;
    public static final int MIN_DEADLINE = 1;
    public static final int MAX_DEADLINE = 100_000;

    private ValidadorProceso() {
        // Clase de utilidad: no se instancia.
    }

    /**
     * Valida todos los campos del formulario.
     *
     * @return arreglo de {@link #CANTIDAD_CAMPOS} posiciones con el mensaje de error de cada
     *         campo, o {@code null} en los campos válidos.
     */
    public static String[] validar(String nombre, String instrucciones, String memoria,
                                   String prioridad, TipoProceso tipo, String deadline,
                                   Computador destino, String periodoEs) {
        String[] errores = new String[CANTIDAD_CAMPOS];

        // Nombre
        String n = nombre == null ? "" : nombre.trim();
        if (n.isEmpty()) {
            errores[CAMPO_NOMBRE] = "El nombre es obligatorio.";
        } else if (n.length() > MAX_LONGITUD_NOMBRE) {
            errores[CAMPO_NOMBRE] = "El nombre no puede pasar de " + MAX_LONGITUD_NOMBRE + " caracteres.";
        }

        // Tipo y computador (listas desplegables: solo pueden venir vacías por un error de programa)
        if (tipo == null) {
            errores[CAMPO_TIPO] = "Elige el tipo de proceso.";
        } else if (tipo != TipoProceso.CPU_BOUND && tipo != TipoProceso.IO_BOUND) {
            errores[CAMPO_TIPO] = "Por ahora solo se pueden crear procesos CPU Bound e I/O Bound.";
        }
        if (destino == null) {
            errores[CAMPO_COMPUTADOR] = "Elige el computador destino.";
        }

        // Instrucciones
        Long numInstrucciones = entero(instrucciones);
        if (numInstrucciones == null) {
            errores[CAMPO_INSTRUCCIONES] = mensajeNoEntero("El número de instrucciones");
        } else if (numInstrucciones < MIN_INSTRUCCIONES || numInstrucciones > MAX_INSTRUCCIONES) {
            errores[CAMPO_INSTRUCCIONES] = "Las instrucciones deben estar entre "
                    + MIN_INSTRUCCIONES + " y " + MAX_INSTRUCCIONES + ".";
        }

        // Memoria: no puede superar la RAM total del computador, o el proceso nunca sería admitido.
        Long kb = entero(memoria);
        if (kb == null) {
            errores[CAMPO_MEMORIA] = mensajeNoEntero("La memoria (KB)");
        } else if (kb < 1) {
            errores[CAMPO_MEMORIA] = "La memoria debe ser de al menos 1 KB.";
        } else if (destino != null && kb > destino.getMemoriaTotal()) {
            errores[CAMPO_MEMORIA] = "La memoria (" + kb + " KB) supera la RAM total de "
                    + destino.getNombre() + " (" + destino.getMemoriaTotal() + " KB).";
        }

        // Prioridad
        Long prio = entero(prioridad);
        if (prio == null) {
            errores[CAMPO_PRIORIDAD] = mensajeNoEntero("La prioridad");
        } else if (prio < PRIORIDAD_MIN || prio > PRIORIDAD_MAX) {
            errores[CAMPO_PRIORIDAD] = "La prioridad debe estar entre " + PRIORIDAD_MIN
                    + " (la más alta) y " + PRIORIDAD_MAX + ".";
        }

        // Deadline relativo, en ciclos
        Long dl = entero(deadline);
        if (dl == null) {
            errores[CAMPO_DEADLINE] = mensajeNoEntero("El deadline (ciclos)");
        } else if (dl < MIN_DEADLINE || dl > MAX_DEADLINE) {
            errores[CAMPO_DEADLINE] = "El deadline debe estar entre " + MIN_DEADLINE
                    + " y " + MAX_DEADLINE + " ciclos.";
        }

        // Periodo de E/S: solo aplica a los procesos I/O Bound.
        if (tipo == TipoProceso.IO_BOUND) {
            Long periodo = entero(periodoEs);
            if (periodo == null) {
                errores[CAMPO_PERIODO_ES] = mensajeNoEntero("El periodo de E/S");
            } else if (periodo < 1) {
                errores[CAMPO_PERIODO_ES] = "El periodo de E/S debe ser de al menos 1.";
            } else if (numInstrucciones != null && periodo > numInstrucciones) {
                errores[CAMPO_PERIODO_ES] = "El periodo de E/S no puede ser mayor que el número de instrucciones.";
            }
        }
        return errores;
    }

    /** Indica si el arreglo de errores no contiene ningún error. */
    public static boolean esValido(String[] errores) {
        for (String e : errores) {
            if (e != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Construye el proceso. Valida primero: si algún campo es inválido lanza
     * {@link IllegalArgumentException} con el primer error encontrado.
     *
     * @param cicloActual ciclo de la simulación en este momento (el deadline cuenta desde él).
     */
    public static PCB construir(String nombre, String instrucciones, String memoria,
                                String prioridad, TipoProceso tipo, String deadline,
                                Computador destino, String periodoEs, long cicloActual) {
        String[] errores = validar(nombre, instrucciones, memoria, prioridad, tipo, deadline,
                destino, periodoEs);
        for (String e : errores) {
            if (e != null) {
                throw new IllegalArgumentException(e);
            }
        }
        int n = entero(instrucciones).intValue();
        int periodo = tipo == TipoProceso.IO_BOUND ? entero(periodoEs).intValue() : 0;

        ListaEnlazada<Instruccion> programa = new ListaEnlazada<>();
        for (int i = 1; i <= n; i++) {
            boolean esES = periodo > 0 && i % periodo == 0;
            programa.agregar(new Instruccion(esES ? TipoInstruccion.READ_IO : TipoInstruccion.CPU));
        }
        return new PCB(nombre.trim(), destino.getId(), tipo,
                entero(prioridad).intValue(), entero(memoria), entero(deadline),
                "", 0, periodo, programa, cicloActual);
    }

    /** Convierte un texto en entero; devuelve {@code null} si no es un entero válido. */
    private static Long entero(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(Long.parseLong(t));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String mensajeNoEntero(String campo) {
        return campo + " debe ser un número entero (sin letras, decimales ni espacios en medio).";
    }
}
