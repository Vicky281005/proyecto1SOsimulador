package modelo;

import enums.EstadoProceso;
import enums.MotivoBloqueo;
import enums.SemaforoBuffer;
import enums.TipoProceso;
import estructuras.ListaEnlazada;
import java.util.concurrent.atomic.AtomicInteger;

public class PCB {
    private static final AtomicInteger generadorId = new AtomicInteger(1);

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
    private long deadlineAbsoluto;
    private long tiempoRestante;
    private long tiempoEjecucionTotal;

    // Registros Hardware Simulado
    private int pc;
    private int mar;

    // Sincronización y Bloqueo Fino
    private String bufferBloqueadoId;
    private SemaforoBuffer semaforoBloqueado;
    private long ciclosBloqueoRestantes;

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
        this.semaforoBloqueado = SemaforoBuffer.NINGUNO;
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
        this.programa = (programa != null) ? programa : new ListaEnlazada<>();
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
        }
    }

    // Verificación exacta de Deadline (>=)
    public void verificarDeadline(long cicloActual) {
        if (cicloActual >= this.deadlineAbsoluto && this.tiempoRestante > 0) {
            this.deadlineCumplido = false;
        }
    }

    public void incrementarTiempoBloqueado() {
        this.tiempoBloqueadoAcumulado++;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
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
    public void setComputadorAsignado(int computadorAsignado) { this.computadorAsignado = computadorAsignado; }
    
    public EstadoProceso getEstado() { return estado; }
    public void setEstado(EstadoProceso estado) { 
        this.estado = estado; 
        if (estado != EstadoProceso.BLOQUEADO) {
            this.motivoBloqueo = MotivoBloqueo.NINGUNO;
            this.bufferBloqueadoId = "";
            this.semaforoBloqueado = SemaforoBuffer.NINGUNO;
            this.ciclosBloqueoRestantes = 0;
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
    public void setTiempoRestante(long tiempoRestante) { this.tiempoRestante = tiempoRestante; }
    
    public long getTiempoEjecucionTotal() { return tiempoEjecucionTotal; }
    
    public ListaEnlazada<Instruccion> getPrograma() { return programa; }
    
    public void setPrograma(ListaEnlazada<Instruccion> programa) { 
        cargarProgramaEInicializar(programa);
    }

    public int getPc() { return pc; }
    public void setPc(int pc) { this.pc = pc; }
    
    public int getMar() { return mar; }
    public void setMar(int mar) { this.mar = mar; }

    public String getBufferBloqueadoId() { return bufferBloqueadoId; }
    public void setBufferBloqueadoId(String bufferBloqueadoId) { this.bufferBloqueadoId = bufferBloqueadoId; }

    public SemaforoBuffer getSemaforoBloqueado() { return semaforoBloqueado; }
    public void setSemaforoBloqueado(SemaforoBuffer semaforoBloqueado) { this.semaforoBloqueado = semaforoBloqueado; }

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
}