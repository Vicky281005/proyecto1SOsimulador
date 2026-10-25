package modelo;

import enums.EstadoProceso;
import enums.MotivoBloqueo;
import enums.TipoProceso;
import estructuras.ListaEnlazada;
import java.util.concurrent.atomic.AtomicInteger;

public class PCB {
    private static final AtomicInteger generadorId = new AtomicInteger(1);

    /** Valor usado mientras ÁvilaOS todavía no ha asignado el proceso a un computador. */
    public static final int SIN_ASIGNAR = -1;

    // Atributos Principales
    private final int id;
    private String nombre;
    private int computadorAsignado;
    private EstadoProceso estado;
    private MotivoBloqueo motivoBloqueo;
    private TipoProceso tipo;
    private int prioridad;
    private long memoriaAsignada;

    // Programa e Instrucciones (Única fuente de verdad de la ejecución)
    private ListaEnlazada<Instruccion> programa;

    // Tiempos y Deadline Absoluto
    private final long deadlineAbsoluto;
    private long tiempoRestante;
    private long tiempoEjecucionTotal;

    // Registros Hardware Simulado
    private int pc;
    private int mar;

    // Sincronización y Bloqueo Fino
    private String bufferBloqueadoId;
    private long ciclosBloqueoRestantes;

    // Acceso remoto pendiente (latencia de red)
    private enums.TipoInstruccion operacionRemotaPendiente;
    private String bufferRemotoPendienteId;
    private boolean mutexRemotoRetenido;

    // Bandera de recurso previamente adquirido (vacíos/llenos) en protocolo Productor-Consumidor (Stallings)
    private boolean recursoSemaforoAdquirido;

    // Configuración Inicial Productor / Consumidor (Metadatos)
    private String idBufferTarget;
    private int elementosRequeridos;
    private int cadaCuantoCiclos;

    // Métricas de Rendimiento
    private long cicloCreacion;
    private long cicloInicio;
    private long cicloFin;
    private long tiempoBloqueadoAcumulado;
    private long ciclosCpuConsumidos;
    private boolean deadlineCumplido;

    // Productor/Consumidor
    private int elementosProducidos;
    private int elementosConsumidos;

    public PCB(String nombre, int computadorAsignado, TipoProceso tipo, int prioridad, 
               long memoriaAsignada, long deadlineRelativo, 
               String idBufferTarget, int elementosRequeridos, int cadaCuantoCiclos, 
               ListaEnlazada<Instruccion> programa, long cicloActual) {
        
        this.id = generadorId.getAndIncrement();
        this.nombre = nombre;
        this.computadorAsignado = computadorAsignado;
        this.estado = EstadoProceso.NUEVO;
        this.motivoBloqueo = MotivoBloqueo.NINGUNO;
        this.tipo = tipo;
        this.prioridad = prioridad;
        this.memoriaAsignada = memoriaAsignada;

        this.deadlineAbsoluto = cicloActual + deadlineRelativo;

        this.bufferBloqueadoId = "";
        this.ciclosBloqueoRestantes = 0;

        // Metadatos de formulario Vista 1
        this.idBufferTarget = idBufferTarget;
        this.elementosRequeridos = elementosRequeridos;
        this.cadaCuantoCiclos = cadaCuantoCiclos;

        this.cicloCreacion = cicloActual;

        // Inicialización unificada de programa y métricas
        cargarProgramaEInicializar(programa);
    }

    // Método helper privado para unificar la carga de programa e inicialización de registros/métricas
    private void cargarProgramaEInicializar(ListaEnlazada<Instruccion> programa) {
        if (programa == null || programa.estaVacia()) {
            throw new IllegalArgumentException("El programa de un proceso no puede ser nulo ni vacío.");
        }
        // Se copia el programa para evitar aliasing entre PCBs que compartan la misma lista.
        this.programa = copiarPrograma(programa);
        this.tiempoRestante = this.programa.getTamano();
        this.tiempoEjecucionTotal = this.tiempoRestante;
        this.pc = 0;
        this.mar = 0;
        
        // Inicialización de métricas
        this.cicloInicio = -1;
        this.cicloFin = -1;
        this.ciclosCpuConsumidos = 0;
        this.tiempoBloqueadoAcumulado = 0;
        this.deadlineCumplido = true;
    }

    // Copia defensiva del programa. Las instrucciones son inmutables, por lo que
    // basta con copiar la estructura (nodos) y no los objetos Instruccion.
    private static ListaEnlazada<Instruccion> copiarPrograma(ListaEnlazada<Instruccion> original) {
        ListaEnlazada<Instruccion> copia = new ListaEnlazada<>();
        for (int i = 0; i < original.getTamano(); i++) {
            copia.agregar(original.obtener(i));
        }
        return copia;
    }

    // Obtener instrucción actual con validación de rango
    public Instruccion getInstruccionActual() {
        if (programa != null && pc >= 0 && pc < programa.getTamano()) {
            return programa.obtener(pc);
        }
        return null;
    }

    // Método de apoyo exclusivo cuando la CPU avanza 1 paso
    public void avanzarPasoCpu(long cicloActual) {
        if (this.tiempoRestante > 0) {
            if (this.cicloInicio == -1) {
                this.cicloInicio = cicloActual;
            }
            this.tiempoRestante--;
            this.ciclosCpuConsumidos++;
            this.pc++;
            this.mar++;
            if (this.tiempoRestante == 0) {
                // El proceso terminó: cumple el deadline si lo hizo dentro del plazo.
                this.deadlineCumplido = cicloActual <= this.deadlineAbsoluto;
            }
        }
    }

    /**
     * Marca el incumplimiento del deadline si el proceso sigue en ejecución
     * (le quedan instrucciones) al alcanzar o superar su deadline absoluto.
     *
     * @return true si el proceso venció su deadline y debe ser terminado por el sistema.
     */
    public boolean verificarDeadline(long cicloActual) {
        if (this.tiempoRestante > 0 && cicloActual >= this.deadlineAbsoluto) {
            this.deadlineCumplido = false;
            return true;
        }
        return false;
    }

    public void incrementarTiempoBloqueado() {
        this.tiempoBloqueadoAcumulado++;
    }

    /**
     * Coloca al proceso en estado BLOQUEADO dejando registrados de forma
     * coherente el motivo y el buffer implicado. Centraliza la transición
     * para no depender del orden en que el llamador invoque los setters.
     */
    public void bloquear(MotivoBloqueo motivo, String bufferId) {
        this.estado = EstadoProceso.BLOQUEADO;
        this.motivoBloqueo = (motivo != null) ? motivo : MotivoBloqueo.NINGUNO;
        this.bufferBloqueadoId = (bufferId != null) ? bufferId : "";
    }

    public enums.TipoInstruccion getOperacionRemotaPendiente() {
        return operacionRemotaPendiente;
    }

    public void setOperacionRemotaPendiente(enums.TipoInstruccion operacionRemotaPendiente) {
        this.operacionRemotaPendiente = operacionRemotaPendiente;
    }

    public String getBufferRemotoPendienteId() {
        return bufferRemotoPendienteId;
    }

    public void setBufferRemotoPendienteId(String bufferRemotoPendienteId) {
        this.bufferRemotoPendienteId = bufferRemotoPendienteId;
    }

    public boolean isMutexRemotoRetenido() {
        return mutexRemotoRetenido;
    }

    public void setMutexRemotoRetenido(boolean mutexRemotoRetenido) {
        this.mutexRemotoRetenido = mutexRemotoRetenido;
    }

    public void limpiarOperacionRemotaPendiente() {
        this.operacionRemotaPendiente = null;
        this.bufferRemotoPendienteId = null;
        this.mutexRemotoRetenido = false;
    }

    /**
     * Asigna el proceso a un computador. Solo se permite mientras el proceso
     * no haya sido admitido (estado NUEVO), de modo que un proceso ya aceptado
     * no pueda migrar de computador.
     */
    public void asignarComputador(int computadorAsignado) {
        if (this.estado != EstadoProceso.NUEVO) {
            throw new IllegalStateException("Un proceso ya admitido no puede cambiar de computador.");
        }
        this.computadorAsignado = computadorAsignado;
    }

    /**
     * Reinicia el contador global de IDs. Pensado para reiniciar la simulación
     * desde cero; no debe invocarse mientras existan procesos vivos.
     */
    public static void reiniciarGeneradorId() {
        generadorId.set(1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PCB)) return false;
        PCB pcb = (PCB) o;
        return id == pcb.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    // Getters y Setters
    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    
    public int getComputadorAsignado() { return computadorAsignado; }
    
    public EstadoProceso getEstado() { return estado; }
    public void setEstado(EstadoProceso estado) { 
        this.estado = estado; 
        if (estado != EstadoProceso.BLOQUEADO) {
            this.motivoBloqueo = MotivoBloqueo.NINGUNO;
            this.bufferBloqueadoId = "";
            this.ciclosBloqueoRestantes = 0;
            limpiarOperacionRemotaPendiente();
        }
    }
    
    public MotivoBloqueo getMotivoBloqueo() { return motivoBloqueo; }
    public void setMotivoBloqueo(MotivoBloqueo motivoBloqueo) { this.motivoBloqueo = motivoBloqueo; }
    
    public TipoProceso getTipo() { return tipo; }
    public int getPrioridad() { return prioridad; }
    public void setPrioridad(int prioridad) { this.prioridad = prioridad; }
    
    public long getMemoriaAsignada() { return memoriaAsignada; }
    public long getDeadlineAbsoluto() { return deadlineAbsoluto; }
    
    public long getTiempoRestante() { return tiempoRestante; }
    
    public long getTiempoEjecucionTotal() { return tiempoEjecucionTotal; }
    
    public ListaEnlazada<Instruccion> getPrograma() { return programa; }
    
    /**
     * Reemplaza el programa del proceso. Solo se permite mientras el proceso
     * no haya iniciado su ejecución, para no alterar su estado ni sus métricas.
     */
    public void setPrograma(ListaEnlazada<Instruccion> programa) { 
        if (this.estado != EstadoProceso.NUEVO || this.cicloInicio != -1) {
            throw new IllegalStateException(
                    "No se puede reemplazar el programa de un proceso que ya inició ejecución.");
        }
        cargarProgramaEInicializar(programa);
    }

    public int getPc() { return pc; }
    
    public int getMar() { return mar; }

    public String getBufferBloqueadoId() { return bufferBloqueadoId; }
    public void setBufferBloqueadoId(String bufferBloqueadoId) { this.bufferBloqueadoId = bufferBloqueadoId; }

    public long getCiclosBloqueoRestantes() { return ciclosBloqueoRestantes; }
    public void setCiclosBloqueoRestantes(long ciclosBloqueoRestantes) { this.ciclosBloqueoRestantes = ciclosBloqueoRestantes; }

    public String getIdBufferTarget() { return idBufferTarget; }
    public int getElementosRequeridos() { return elementosRequeridos; }
    public int getCadaCuantoCiclos() { return cadaCuantoCiclos; }

    public long getCicloCreacion() { return cicloCreacion; }
    public long getCicloInicio() { return cicloInicio; }
    public long getCicloFin() { return cicloFin; }
    public void setCicloFin(long cicloFin) { this.cicloFin = cicloFin; }

    public long getTiempoBloqueadoAcumulado() { return tiempoBloqueadoAcumulado; }
    public long getCiclosCpuConsumidos() { return ciclosCpuConsumidos; }
    public boolean isDeadlineCumplido() { return deadlineCumplido; }
    public void setDeadlineCumplido(boolean deadlineCumplido) { this.deadlineCumplido = deadlineCumplido; }

    public int getElementosProducidos() { return elementosProducidos; }
    public void incrementarElementosProducidos() { this.elementosProducidos++; }

    public int getElementosConsumidos() { return elementosConsumidos; }
    public void incrementarElementosConsumidos() { this.elementosConsumidos++; }

    public boolean isRecursoSemaforoAdquirido() { return recursoSemaforoAdquirido; }
    public void setRecursoSemaforoAdquirido(boolean recursoSemaforoAdquirido) { this.recursoSemaforoAdquirido = recursoSemaforoAdquirido; }

    private boolean creacionRegistradaEnLog;

    public boolean isCreacionRegistradaEnLog() {
        return creacionRegistradaEnLog;
    }

    public void setCreacionRegistradaEnLog(boolean creacionRegistradaEnLog) {
        this.creacionRegistradaEnLog = creacionRegistradaEnLog;
    }

    public long getMemoriaRequerida() {
        return memoriaAsignada;
    }
}
