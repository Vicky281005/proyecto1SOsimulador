package disco;

import enums.PoliticaDisco;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import hardware.ComponenteReloj;
import java.util.concurrent.Semaphore;
import simulacion.LogEventos;

/**
 * Disco Simulado (SD) local de ÁvilaOS.
 *
 * <p>Implementa {@link ComponenteReloj} para avanzar exactamente un ciclo de trabajo
 * por cada pulso del reloj global. Administra:</p>
 * <ul>
 *   <li>El mapa de bloques numerados con <b>asignación encadenada</b>.</li>
 *   <li>El área de intercambio (<b>Swap</b>) reservada para procesos suspendidos.</li>
 *   <li>La <b>cola única de E/S</b> protegida con {@link Semaphore} de Java.</li>
 *   <li>El cabezal del disco (posición, dirección, velocidad configurable) y sus métricas.</li>
 * </ul>
 */
public class DiscoSimulado implements ComponenteReloj {

    public static final int TOTAL_BLOQUES_POR_DEFECTO = 200;
    /** Mínimo de bloques que admite el enunciado para el disco. */
    public static final int MIN_BLOQUES_DISCO = 100;
    /** Máximo de bloques que admite el enunciado para el disco. */
    public static final int MAX_BLOQUES_DISCO = 500;
    public static final int POSICION_CABEZAL_POR_DEFECTO = 50;
    public static final int VELOCIDAD_CABEZAL_POR_DEFECTO = 10;

    private final String nombre;
    private final Semaphore mutex = new Semaphore(1, true);

    private BloqueDisco[] bloques;
    private int totalBloquesDatos;
    private int tamanoSwap;
    private int inicioAreaSwap;

    private int posicionCabezal;
    private boolean direccionAscendente;
    private int velocidadCabezal;

    private final PlanificadorDisco planificador = new PlanificadorDisco(PoliticaDisco.FIFO);
    private SolicitudES solicitudActual;
    private int metaExtremoPendiente = -1;

    private PoliticaDisco politicaActual = PoliticaDisco.FIFO;

    private EscuchaFinES escuchaFinES;
    private LogEventos logEventos;

    // Métricas del disco (expuestas para reportes y gráfica JFreeChart)
    private long desplazamientoTotal;
    private long totalSolicitudesAtendidas;
    private long sumaTiemposEspera;
    private final long[] solicitudesPorPolitica = new long[PoliticaDisco.values().length];
    private final ListaEnlazada<Long> historialDesplazamiento = new ListaEnlazada<>();

    /**
     * Crea un disco con valores por defecto (200 bloques, cabezal en el bloque 50,
     * sin área Swap inicial).
     */
    public DiscoSimulado() {
        this("Disco-SD", TOTAL_BLOQUES_POR_DEFECTO, POSICION_CABEZAL_POR_DEFECTO, 0);
    }

    /**
     * Crea un disco simulado configurable.
     *
     * @param nombre          nombre legible del componente para GUI y logs.
     * @param totalBloques    cantidad de bloques de datos/sistema de archivos.
     * @param cabezalInicial  posición inicial del cabezal (0 .. totalBloques - 1).
     * @param tamanoSwap      cantidad de bloques reservados para el área de intercambio (Swap).
     */
    public DiscoSimulado(String nombre, int totalBloques, int cabezalInicial, int tamanoSwap) {
        if (totalBloques <= 0) {
            throw new IllegalArgumentException("El número de bloques del disco debe ser mayor que cero.");
        }
        if (tamanoSwap < 0) {
            throw new IllegalArgumentException("El tamaño del área de intercambio (swap) no puede ser negativo.");
        }
        this.nombre = (nombre != null && !nombre.trim().isEmpty()) ? nombre.trim() : "Disco-SD";
        this.totalBloquesDatos = totalBloques;
        this.tamanoSwap = 0;
        this.inicioAreaSwap = totalBloques;
        this.bloques = new BloqueDisco[totalBloques];
        for (int i = 0; i < totalBloques; i++) {
            this.bloques[i] = new BloqueDisco(i);
        }
        if (tamanoSwap > 0) {
            configurarAreaSwapInternal(tamanoSwap);
        }

        int limite = Math.max(0, this.totalBloquesDatos - 1);
        this.posicionCabezal = Math.max(0, Math.min(cabezalInicial, limite));
        this.direccionAscendente = true;
        this.velocidadCabezal = VELOCIDAD_CABEZAL_POR_DEFECTO;
    }

    // =========================================================================
    // CONFIGURACIÓN DE SWAP Y COMPONENTES EXTERNOS
    // =========================================================================

    /**
     * Reserva o ajusta el área de intercambio (Swap) para procesos suspendidos.
     *
     * <p>Si los últimos {@code nuevoTamanoSwap} bloques de datos están libres, se
     * reservan dentro de la capacidad actual del disco; si ya están ocupados por
     * archivos precargados (por ejemplo desde el JSON del sistema de archivos),
     * la zona Swap se anexa contiguamente para no sobrescribir archivos válidos.</p>
     *
     * @param nuevoTamanoSwap cantidad de bloques para Swap (>= 0).
     */
    public void configurarAreaSwap(int nuevoTamanoSwap) {
        if (nuevoTamanoSwap < 0) {
            throw new IllegalArgumentException("El tamaño del área Swap no puede ser negativo.");
        }
        mutex.acquireUninterruptibly();
        try {
            configurarAreaSwapInternal(nuevoTamanoSwap);
        } finally {
            mutex.release();
        }
    }

    private void configurarAreaSwapInternal(int nuevoTamanoSwap) {
        // Limpiar marca previa si existía un área Swap vacía
        for (int i = 0; i < bloques.length; i++) {
            if (bloques[i].isAreaSwap() && bloques[i].isLibre()) {
                bloques[i].setAreaSwap(false);
            }
        }
        this.tamanoSwap = nuevoTamanoSwap;
        if (nuevoTamanoSwap == 0) {
            this.inicioAreaSwap = totalBloquesDatos;
            return;
        }

        // Verificar si caben al final del arreglo actual sin pisar archivos existentes
        boolean cabeAlFinalSinPisar = nuevoTamanoSwap < bloques.length;
        if (cabeAlFinalSinPisar) {
            int inicioTentativo = bloques.length - nuevoTamanoSwap;
            for (int i = inicioTentativo; i < bloques.length; i++) {
                if (bloques[i].isOcupado() && !bloques[i].isOcupadoPorSwap()) {
                    cabeAlFinalSinPisar = false;
                    break;
                }
            }
            if (cabeAlFinalSinPisar) {
                this.inicioAreaSwap = inicioTentativo;
                this.totalBloquesDatos = inicioTentativo;
                for (int i = inicioTentativo; i < bloques.length; i++) {
                    bloques[i].setAreaSwap(true);
                }
                return;
            }
        }

        // Expandir contiguamente después de los bloques de datos
        int nuevaLongitud = totalBloquesDatos + nuevoTamanoSwap;
        BloqueDisco[] nuevoArreglo = new BloqueDisco[nuevaLongitud];
        for (int i = 0; i < totalBloquesDatos; i++) {
            nuevoArreglo[i] = bloques[i];
        }
        for (int i = totalBloquesDatos; i < nuevaLongitud; i++) {
            BloqueDisco b = new BloqueDisco(i);
            b.setAreaSwap(true);
            nuevoArreglo[i] = b;
        }
        this.bloques = nuevoArreglo;
        this.inicioAreaSwap = totalBloquesDatos;
    }

    public void setEscuchaFinES(EscuchaFinES escuchaFinES) {
        mutex.acquireUninterruptibly();
        try {
            this.escuchaFinES = escuchaFinES;
        } finally {
            mutex.release();
        }
    }

    public void setLogEventos(LogEventos logEventos) {
        mutex.acquireUninterruptibly();
        try {
            this.logEventos = logEventos;
        } finally {
            mutex.release();
        }
    }

    public void setEstrategiaActiva(PoliticaPlanificacionDisco estrategia) {
        if (estrategia == null) {
            return;
        }
        mutex.acquireUninterruptibly();
        try {
            this.planificador.setEstrategia(estrategia);
            this.politicaActual = estrategia.getTipo();
            if (politicaActual != PoliticaDisco.SCAN && politicaActual != PoliticaDisco.C_SCAN) {
                this.metaExtremoPendiente = -1;
            }
        } finally {
            mutex.release();
        }
    }

    public void setPolitica(PoliticaDisco politica) {
        if (politica == null) {
            return;
        }
        PoliticaDisco anterior;
        int pendientes;
        LogEventos logCambio;
        mutex.acquireUninterruptibly();
        try {
            anterior = this.politicaActual;
            this.planificador.setPolitica(politica);
            this.politicaActual = politica;
            if (politica != PoliticaDisco.SCAN && politica != PoliticaDisco.C_SCAN) {
                this.metaExtremoPendiente = -1;
            }
            pendientes = planificador.getCantidadPendientes();
            logCambio = this.logEventos;
        } finally {
            mutex.release();
        }
        if (logCambio != null && anterior != politica) {
            logCambio.registrar(LogEventos.TipoEvento.CAMBIO_POLITICA,
                    "Política de disco: " + anterior.getCodigoJson() + " -> " + politica.getCodigoJson()
                            + " (en caliente, " + pendientes + " solicitudes en cola)");
        }
    }

    public PlanificadorDisco getPlanificador() {
        return planificador;
    }

    public PoliticaDisco getPoliticaActual() {
        mutex.acquireUninterruptibly();
        try {
            return politicaActual;
        } finally {
            mutex.release();
        }
    }

    public void setVelocidadCabezal(int velocidadCabezal) {
        if (velocidadCabezal <= 0) {
            throw new IllegalArgumentException("La velocidad del cabezal debe ser mayor que cero.");
        }
        mutex.acquireUninterruptibly();
        try {
            this.velocidadCabezal = velocidadCabezal;
        } finally {
            mutex.release();
        }
    }

    public int getVelocidadCabezal() {
        mutex.acquireUninterruptibly();
        try {
            return velocidadCabezal;
        } finally {
            mutex.release();
        }
    }

    // =========================================================================
    // ASIGNACIÓN ENCADENADA DE BLOQUES (ARCHIVOS Y SWAP)
    // =========================================================================

    /**
     * Reserva {@code cantidad} bloques libres de datos (fuera del área Swap)
     * enlazándolos mediante asignación encadenada.
     *
     * @param rutaArchivo ruta propietaria.
     * @param cantidad    número de bloques solicitados.
     * @param colorHex    color hexadecimal para la representación gráfica.
     * @return índice del primer bloque de la cadena, o {@code -1} si no hay espacio suficiente.
     */
    public int asignarBloquesArchivo(String rutaArchivo, int cantidad, String colorHex) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad de bloques debe ser mayor que cero.");
        }
        mutex.acquireUninterruptibly();
        try {
            ListaEnlazada<Integer> libres = new ListaEnlazada<>();
            for (int i = 0; i < totalBloquesDatos; i++) {
                if (bloques[i].isLibre() && !bloques[i].isAreaSwap()) {
                    libres.agregar(i);
                    if (libres.getTamano() == cantidad) {
                        break;
                    }
                }
            }
            if (libres.getTamano() < cantidad) {
                return -1;
            }
            for (int k = 0; k < libres.getTamano(); k++) {
                int actual = libres.obtener(k);
                int siguiente = (k + 1 < libres.getTamano())
                        ? libres.obtener(k + 1)
                        : BloqueDisco.FIN_CADENA;
                bloques[actual].ocuparParaArchivo(rutaArchivo, siguiente, colorHex);
            }
            return libres.obtener(0);
        } finally {
            mutex.release();
        }
    }

    /**
     * Marca una cadena contigua de bloques como ocupada por un archivo precargado
     * (por ejemplo, desde el archivo JSON del sistema de archivos).
     *
     * @return {@code true} si todos los bloques estaban dentro de rango y libres.
     */
    public boolean ocuparBloquesContiguosArchivo(String rutaArchivo, int startBlock, int cantidad, String colorHex) {
        if (cantidad <= 0 || startBlock < 0 || startBlock + cantidad > totalBloquesDatos) {
            return false;
        }
        mutex.acquireUninterruptibly();
        try {
            for (int i = startBlock; i < startBlock + cantidad; i++) {
                if (bloques[i].isOcupado() || bloques[i].isAreaSwap()) {
                    return false;
                }
            }
            for (int i = startBlock; i < startBlock + cantidad; i++) {
                int siguiente = (i + 1 < startBlock + cantidad) ? (i + 1) : BloqueDisco.FIN_CADENA;
                bloques[i].ocuparParaArchivo(rutaArchivo, siguiente, colorHex);
            }
            return true;
        } finally {
            mutex.release();
        }
    }

    /**
     * Recorre la cadena encadenada desde {@code primerBloque} y libera todos sus bloques.
     *
     * @param primerBloque primer bloque de la lista encadenada del archivo.
     * @return cantidad de bloques liberados.
     */
    public int liberarCadenaBloques(int primerBloque) {
        mutex.acquireUninterruptibly();
        try {
            return liberarCadenaInternal(primerBloque);
        } finally {
            mutex.release();
        }
    }

    private int liberarCadenaInternal(int primerBloque) {
        if (primerBloque < 0 || primerBloque >= bloques.length) {
            return 0;
        }
        int liberados = 0;
        int cursor = primerBloque;
        // Cota de seguridad ante posibles ciclos corruptos
        int pasos = 0;
        while (cursor != BloqueDisco.FIN_CADENA && cursor >= 0 && cursor < bloques.length && pasos < bloques.length) {
            int siguiente = bloques[cursor].getSiguienteBloque();
            bloques[cursor].liberar();
            liberados++;
            cursor = siguiente;
            pasos++;
        }
        return liberados;
    }

    /**
     * Actualiza el nombre de propietario (ruta) a lo largo de la cadena de bloques
     * cuando se renombra un archivo mediante operación {@code UPDATE}.
     */
    public void renombrarPropietarioCadena(int primerBloque, String nuevaRuta) {
        mutex.acquireUninterruptibly();
        try {
            int cursor = primerBloque;
            int pasos = 0;
            while (cursor != BloqueDisco.FIN_CADENA && cursor >= 0 && cursor < bloques.length && pasos < bloques.length) {
                bloques[cursor].setPropietario(nuevaRuta);
                cursor = bloques[cursor].getSiguienteBloque();
                pasos++;
            }
        } finally {
            mutex.release();
        }
    }

    /**
     * Devuelve la secuencia ordenada de números de bloque pertenecientes a un archivo.
     */
    public ListaEnlazada<Integer> obtenerCadenaBloques(int primerBloque) {
        ListaEnlazada<Integer> cadena = new ListaEnlazada<>();
        mutex.acquireUninterruptibly();
        try {
            int cursor = primerBloque;
            int pasos = 0;
            while (cursor != BloqueDisco.FIN_CADENA && cursor >= 0 && cursor < bloques.length && pasos < bloques.length) {
                cadena.agregar(cursor);
                cursor = bloques[cursor].getSiguienteBloque();
                pasos++;
            }
            return cadena;
        } finally {
            mutex.release();
        }
    }

    /**
     * Reserva bloques dentro del área de intercambio (Swap) para un proceso suspendido,
     * utilizando asignación encadenada.
     *
     * @param propietarioSwap nombre/PID del proceso suspendido.
     * @param cantidadBloques unidades de memoria a volcar en Swap.
     * @return índice del primer bloque de Swap asignado, o {@code -1} si no cabe.
     */
    public int asignarBloquesSwap(String propietarioSwap, int cantidadBloques) {
        if (cantidadBloques <= 0) {
            return -1;
        }
        mutex.acquireUninterruptibly();
        try {
            ListaEnlazada<Integer> libres = new ListaEnlazada<>();
            for (int i = inicioAreaSwap; i < bloques.length; i++) {
                if (bloques[i].isAreaSwap() && bloques[i].isLibre()) {
                    libres.agregar(i);
                    if (libres.getTamano() == cantidadBloques) {
                        break;
                    }
                }
            }
            if (libres.getTamano() < cantidadBloques) {
                return -1;
            }
            for (int k = 0; k < libres.getTamano(); k++) {
                int actual = libres.obtener(k);
                int siguiente = (k + 1 < libres.getTamano())
                        ? libres.obtener(k + 1)
                        : BloqueDisco.FIN_CADENA;
                bloques[actual].ocuparParaSwap(propietarioSwap, siguiente);
            }
            return libres.obtener(0);
        } finally {
            mutex.release();
        }
    }

    /** Libera los bloques de Swap ocupados por un proceso al reactivarse o terminar. */
    public int liberarBloquesSwap(int primerBloqueSwap) {
        mutex.acquireUninterruptibly();
        try {
            return liberarCadenaInternal(primerBloqueSwap);
        } finally {
            mutex.release();
        }
    }

    // =========================================================================
    // COLA DE SOLICITUDES E/S Y EJECUCIÓN POR CICLOS (ComponenteReloj)
    // =========================================================================

    /** Encola una nueva solicitud de E/S en la cola única del disco. */
    public void encolarSolicitud(SolicitudES solicitud) {
        if (solicitud == null) {
            throw new IllegalArgumentException("La solicitud de E/S no puede ser nula.");
        }
        mutex.acquireUninterruptibly();
        try {
            planificador.encolar(solicitud);
        } finally {
            mutex.release();
        }
    }

    @Override
    public void tick(long cicloActual) {
        SolicitudES completada = null;
        PoliticaDisco politicaUsada = null;
        EscuchaFinES escucha = null;
        LogEventos log = null;
        String eleccion = null;
        LogEventos logEleccion = null;

        mutex.acquireUninterruptibly();
        try {
            if (solicitudActual == null && !planificador.estaVacia()) {
                int pendientesAntes = planificador.getCantidadPendientes();
                int cabezalAntes = posicionCabezal;
                seleccionarSiguienteLocked(cicloActual);
                if (solicitudActual != null) {
                    eleccion = "Disco (" + politicaActual.getCodigoJson() + ") elige " + solicitudActual
                            + " con el cabezal en el bloque " + cabezalAntes
                            + (direccionAscendente ? " (ascendente)" : " (descendente)")
                            + ", distancia " + Math.abs(solicitudActual.getBloqueDestino() - cabezalAntes)
                            + ", " + (pendientesAntes - 1) + " solicitudes quedan en espera";
                    logEleccion = this.logEventos;
                }
            }

            if (solicitudActual != null) {
                if (metaExtremoPendiente != -1) {
                    avanzarCabezalHacia(metaExtremoPendiente);
                    if (posicionCabezal == metaExtremoPendiente) {
                        if (politicaActual == PoliticaDisco.C_SCAN) {
                            // En C-SCAN el salto de retorno al extremo opuesto no suma al desplazamiento
                            int limiteSuperior = calcularLimiteSuperiorSCAN();
                            posicionCabezal = (metaExtremoPendiente == limiteSuperior) ? 0 : limiteSuperior;
                        } else {
                            // En SCAN el cabezal rebota e invierte su dirección
                            direccionAscendente = !direccionAscendente;
                        }
                        metaExtremoPendiente = -1;
                    }
                } else if (posicionCabezal != solicitudActual.getBloqueDestino()) {
                    int destino = solicitudActual.getBloqueDestino();
                    direccionAscendente = destino > posicionCabezal;
                    avanzarCabezalHacia(destino);
                } else {
                    // Cabezal posicionado: cada bloque leído o escrito cuesta 1 ciclo
                    solicitudActual.consumirCicloTransferencia();
                    if (solicitudActual.getBloquesRestantesTransferencia() <= 0) {
                        solicitudActual.setCicloFinAtencion(cicloActual);
                        totalSolicitudesAtendidas++;
                        sumaTiemposEspera += solicitudActual.getTiempoEspera();
                        solicitudesPorPolitica[politicaActual.ordinal()]++;

                        completada = solicitudActual;
                        politicaUsada = politicaActual;
                        escucha = this.escuchaFinES;
                        log = this.logEventos;
                        solicitudActual = null;
                    }
                }
            }

            historialDesplazamiento.agregar(desplazamientoTotal);
        } finally {
            mutex.release();
        }

        if (eleccion != null && logEleccion != null) {
            logEleccion.registrar(LogEventos.TipoEvento.DISCO, eleccion);
        }

        if (completada != null) {
            String detalle = "El disco atendió " + completada.getOperacion().getCodigoJson()
                    + " de " + completada.getNombreProceso()
                    + " en el bloque " + completada.getBloqueDestino()
                    + " (" + politicaUsada.getCodigoJson() + ")";
            if (log != null) {
                // Cuando LogEventos incorpore el tipo específico de disco, reemplazar OTRO
                log.registrar(LogEventos.TipoEvento.DISCO, detalle);
            }
            if (escucha != null) {
                escucha.alTerminarSolicitud(completada, true, detalle);
            }
        }
    }

    private void seleccionarSiguienteLocked(long cicloActual) {
        int limiteSuperior = calcularLimiteSuperiorSCAN();
        SolicitudES elegida = planificador.seleccionarSiguiente(
                posicionCabezal, direccionAscendente, limiteSuperior + 1);
        if (elegida == null) {
            return;
        }
        elegida.setCicloInicioAtencion(cicloActual);
        solicitudActual = elegida;

        int destino = elegida.getBloqueDestino();
        if (politicaActual == PoliticaDisco.SCAN || politicaActual == PoliticaDisco.C_SCAN) {
            if (direccionAscendente && destino < posicionCabezal) {
                metaExtremoPendiente = limiteSuperior;
            } else if (!direccionAscendente && destino > posicionCabezal) {
                metaExtremoPendiente = 0;
            } else {
                metaExtremoPendiente = -1;
            }
        } else {
            metaExtremoPendiente = -1;
        }
    }

    private void avanzarCabezalHacia(int objetivo) {
        int distancia = Math.abs(objetivo - posicionCabezal);
        int paso = Math.min(velocidadCabezal, distancia);
        if (objetivo > posicionCabezal) {
            posicionCabezal += paso;
        } else if (objetivo < posicionCabezal) {
            posicionCabezal -= paso;
        }
        desplazamientoTotal += paso;
    }

    private int calcularLimiteSuperiorSCAN() {
        int limite = Math.max(0, totalBloquesDatos - 1);
        if (solicitudActual != null && solicitudActual.getBloqueDestino() > limite) {
            limite = bloques.length - 1;
        }
        Iterador<SolicitudES> it = planificador.iteradorCola();
        while (it.haySiguiente()) {
            SolicitudES s = it.siguiente();
            if (s != null && s.getBloqueDestino() > limite) {
                limite = bloques.length - 1;
                break;
            }
        }
        return limite;
    }

    // =========================================================================
    // CONSULTAS SEGURAS DE ESTADO Y MÉTRICAS (PARA GUI Y REPORTES)
    // =========================================================================

    @Override
    public String getNombre() {
        return nombre;
    }

    public int getPosicionCabezal() {
        mutex.acquireUninterruptibly();
        try {
            return posicionCabezal;
        } finally {
            mutex.release();
        }
    }

    public boolean isDireccionAscendente() {
        mutex.acquireUninterruptibly();
        try {
            return direccionAscendente;
        } finally {
            mutex.release();
        }
    }

    public int getTotalBloques() {
        mutex.acquireUninterruptibly();
        try {
            return bloques.length;
        } finally {
            mutex.release();
        }
    }

    public int getTotalBloquesDatos() {
        mutex.acquireUninterruptibly();
        try {
            return totalBloquesDatos;
        } finally {
            mutex.release();
        }
    }

    public int getTamanoSwap() {
        mutex.acquireUninterruptibly();
        try {
            return tamanoSwap;
        } finally {
            mutex.release();
        }
    }

    public int getBloquesLibresDatos() {
        mutex.acquireUninterruptibly();
        try {
            int libres = 0;
            for (int i = 0; i < totalBloquesDatos; i++) {
                if (bloques[i].isLibre() && !bloques[i].isAreaSwap()) {
                    libres++;
                }
            }
            return libres;
        } finally {
            mutex.release();
        }
    }

    public int getBloquesLibresSwap() {
        mutex.acquireUninterruptibly();
        try {
            int libres = 0;
            for (int i = inicioAreaSwap; i < bloques.length; i++) {
                if (bloques[i].isAreaSwap() && bloques[i].isLibre()) {
                    libres++;
                }
            }
            return libres;
        } finally {
            mutex.release();
        }
    }

    public SolicitudES getSolicitudActual() {
        mutex.acquireUninterruptibly();
        try {
            return solicitudActual;
        } finally {
            mutex.release();
        }
    }

    public int getCantidadEnColaES() {
        mutex.acquireUninterruptibly();
        try {
            return planificador.getCantidadPendientes();
        } finally {
            mutex.release();
        }
    }

    /**
     * Devuelve un iterador seguro sobre una copia instantánea de la cola de E/S pendiente.
     */
    public Iterador<SolicitudES> iteradorColaES() {
        mutex.acquireUninterruptibly();
        try {
            return planificador.iteradorCola();
        } finally {
            mutex.release();
        }
    }

    /**
     * Devuelve una copia de los bloques actuales para que la GUI dibuje el mapa sin riesgos.
     */
    public ListaEnlazada<BloqueDisco> copiarBloques() {
        ListaEnlazada<BloqueDisco> copia = new ListaEnlazada<>();
        mutex.acquireUninterruptibly();
        try {
            for (int i = 0; i < bloques.length; i++) {
                copia.agregar(new BloqueDisco(bloques[i]));
            }
        } finally {
            mutex.release();
        }
        return copia;
    }

    public boolean hayTrabajoPendiente() {
        mutex.acquireUninterruptibly();
        try {
            return solicitudActual != null || !planificador.estaVacia();
        } finally {
            mutex.release();
        }
    }

    public long getDesplazamientoTotal() {
        mutex.acquireUninterruptibly();
        try {
            return desplazamientoTotal;
        } finally {
            mutex.release();
        }
    }

    public long getTotalSolicitudesAtendidas() {
        mutex.acquireUninterruptibly();
        try {
            return totalSolicitudesAtendidas;
        } finally {
            mutex.release();
        }
    }

    public double getTiempoEsperaPromedio() {
        mutex.acquireUninterruptibly();
        try {
            if (totalSolicitudesAtendidas == 0) {
                return 0.0;
            }
            return (double) sumaTiemposEspera / totalSolicitudesAtendidas;
        } finally {
            mutex.release();
        }
    }

    public long getSolicitudesAtendidas(PoliticaDisco politica) {
        if (politica == null) {
            return 0;
        }
        mutex.acquireUninterruptibly();
        try {
            return solicitudesPorPolitica[politica.ordinal()];
        } finally {
            mutex.release();
        }
    }

    /** Historial ciclo a ciclo del desplazamiento acumulado del cabezal para la gráfica. */
    public ListaEnlazada<Long> copiarHistorialDesplazamiento() {
        ListaEnlazada<Long> copia = new ListaEnlazada<>();
        mutex.acquireUninterruptibly();
        try {
            Iterador<Long> it = historialDesplazamiento.iterador();
            while (it.haySiguiente()) {
                copia.agregar(it.siguiente());
            }
        } finally {
            mutex.release();
        }
        return copia;
    }
}
