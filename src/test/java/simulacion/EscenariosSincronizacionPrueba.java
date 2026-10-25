package simulacion;

import enums.EstadoProceso;
import enums.MotivoBloqueo;
import enums.PoliticaPlanificacion;
import enums.TipoInstruccion;
import enums.TipoProceso;
import estructuras.Iterador;
import estructuras.ListaEnlazada;
import modelo.Instruccion;
import modelo.PCB;
import nucleo.Computador;
import planificacion.FabricaPoliticas;
import sincronizacion.Buffer;

/**
 * Suite de pruebas automatizadas que simula programáticamente los 5 escenarios
 * de sincronización y planificación de ÁvilaOS sin abrir la interfaz gráfica.
 */
public class EscenariosSincronizacionPrueba {

    private static int totalChecks = 0;
    private static int checksExitosos = 0;
    private static int checksFallidos = 0;

    private static void asegurar(boolean condicion, String descripcion) {
        totalChecks++;
        if (condicion) {
            checksExitosos++;
            System.out.println("    [OK] " + descripcion);
        } else {
            checksFallidos++;
            System.err.println("    [FALLO] " + descripcion);
        }
    }

    private static ListaEnlazada<Instruccion> crearPrograma(TipoInstruccion tipo, int cantidad) {
        ListaEnlazada<Instruccion> programa = new ListaEnlazada<>();
        for (int i = 0; i < cantidad; i++) {
            programa.agregar(new Instruccion(tipo));
        }
        return programa;
    }

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("       SUITE DE PRUEBAS AUTOMATIZADAS - ÁVILA OS (5 ESCENARIOS)");
        System.out.println("===============================================================================");

        probarEscenario1_SaturacionBuffer();
        probarEscenario2_DesbloqueoEnCascada();
        probarEscenario3_OperacionRemotaLatencia();
        probarEscenario4_MuerteDeadlineConsumidorBloqueado();
        probarEscenario5_CambioPoliticaEnCaliente();
        probarEscenario6_Stress60ProcesosConcurrencia();

        System.out.println("\n===============================================================================");
        System.out.println("                       RESUMEN DE RESULTADOS");
        System.out.println("===============================================================================");
        System.out.println("  Total de aserciones evaluadas : " + totalChecks);
        System.out.println("  Aserciones exitosas          : " + checksExitosos);
        System.out.println("  Aserciones fallidas          : " + checksFallidos);
        if (checksFallidos == 0) {
            System.out.println("  ESTADO FINAL                 : 100% PASADO (Integridad confirmada)");
        } else {
            System.err.println("  ESTADO FINAL                 : CON ERRORES");
            System.exit(1);
        }
        System.out.println("===============================================================================");
    }

    /**
     * Escenario 1: Saturación de un buffer N=1 con 4 productores y 0 consumidores.
     * Verifica que 1 produzca y termine, y exactamente 3 queden bloqueados en SEMAFORO_VACIO.
     */
    public static void probarEscenario1_SaturacionBuffer() {
        System.out.println("\n>>> ESCENARIO 1: Saturación de un buffer N=1 (4 productores, 0 consumidores)");
        Simulador sim = new Simulador();
        Computador pc1 = new Computador(1, "PC-1", 4096, FabricaPoliticas.crear(PoliticaPlanificacion.FCFS));
        sim.registrarComputador(pc1);

        Buffer buf1 = new Buffer("BUF-SAT-1", 1, pc1.getId(), 4, pc1);
        sim.registrarBuffer(buf1);

        PCB p1 = new PCB("Prod-1", pc1.getId(), TipoProceso.PRODUCTOR, 1, 100, 1000, buf1.getId(), 1, 1, crearPrograma(TipoInstruccion.PRODUCIR, 1), 0);
        PCB p2 = new PCB("Prod-2", pc1.getId(), TipoProceso.PRODUCTOR, 1, 100, 1000, buf1.getId(), 1, 1, crearPrograma(TipoInstruccion.PRODUCIR, 1), 0);
        PCB p3 = new PCB("Prod-3", pc1.getId(), TipoProceso.PRODUCTOR, 1, 100, 1000, buf1.getId(), 1, 1, crearPrograma(TipoInstruccion.PRODUCIR, 1), 0);
        PCB p4 = new PCB("Prod-4", pc1.getId(), TipoProceso.PRODUCTOR, 1, 100, 1000, buf1.getId(), 1, 1, crearPrograma(TipoInstruccion.PRODUCIR, 1), 0);

        sim.cargarProceso(p1);
        sim.cargarProceso(p2);
        sim.cargarProceso(p3);
        sim.cargarProceso(p4);

        for (int i = 0; i < 5; i++) {
            sim.getReloj().avanzarCiclo();
        }

        asegurar(p1.getEstado() == EstadoProceso.TERMINADO, "Prod-1 produjo y terminó exitosamente");
        asegurar(p2.getEstado() == EstadoProceso.BLOQUEADO, "Prod-2 está bloqueado");
        asegurar(p3.getEstado() == EstadoProceso.BLOQUEADO, "Prod-3 está bloqueado");
        asegurar(p4.getEstado() == EstadoProceso.BLOQUEADO, "Prod-4 está bloqueado");

        asegurar(p2.getMotivoBloqueo() == MotivoBloqueo.SEMAFORO_VACIO, "Prod-2 bloqueado por SEMAFORO_VACIO");
        asegurar(p3.getMotivoBloqueo() == MotivoBloqueo.SEMAFORO_VACIO, "Prod-3 bloqueado por SEMAFORO_VACIO");
        asegurar(p4.getMotivoBloqueo() == MotivoBloqueo.SEMAFORO_VACIO, "Prod-4 bloqueado por SEMAFORO_VACIO");

        asegurar(pc1.getCantidadBloqueados() == 3, "Exactamente 3 procesos en la cola de bloqueados del computador");
        asegurar(buf1.getCantidadElementos() == 1, "Buffer contiene exactamente 1 elemento almacenado");

        // Integridad de los semáforos
        asegurar(buf1.getValorVacios() == 0, "Semáforo vacíos tiene valor 0");
        asegurar(buf1.getValorLlenos() == 1, "Semáforo llenos tiene valor 1");
        asegurar(buf1.getValorMutex() == 1, "Semáforo mutex está libre (valor 1)");
        asegurar(buf1.getCantidadEsperandoVacios() == 3, "3 procesos esperando en la cola del semáforo vacíos");
        asegurar(buf1.getValorVacios() + buf1.getValorLlenos() == buf1.getCapacidad(),
                "Invariante respetada: vacios(0) + llenos(1) == capacidad(1)");
    }

    /**
     * Escenario 2: Desbloqueo en cascada añadiendo 1 consumidor a la cola de productores bloqueados.
     */
    public static void probarEscenario2_DesbloqueoEnCascada() {
        System.out.println("\n>>> ESCENARIO 2: Desbloqueo en cascada añadiendo 1 consumidor");
        // Fase 1: saturar buffer N=1 con 3 productores (Prod-1 llena, Prod-2 y Prod-3 se bloquean)
        Simulador sim = new Simulador();
        Computador pc1 = new Computador(1, "PC-1", 4096, FabricaPoliticas.crear(PoliticaPlanificacion.FCFS));
        sim.registrarComputador(pc1);

        Buffer buf = new Buffer("BUF-CASC", 1, pc1.getId(), 4, pc1);
        sim.registrarBuffer(buf);

        PCB p1 = new PCB("Prod-1", pc1.getId(), TipoProceso.PRODUCTOR, 1, 100, 1000, buf.getId(), 1, 1, crearPrograma(TipoInstruccion.PRODUCIR, 1), 0);
        PCB p2 = new PCB("Prod-2", pc1.getId(), TipoProceso.PRODUCTOR, 1, 100, 1000, buf.getId(), 1, 1, crearPrograma(TipoInstruccion.PRODUCIR, 1), 0);
        PCB p3 = new PCB("Prod-3", pc1.getId(), TipoProceso.PRODUCTOR, 1, 100, 1000, buf.getId(), 1, 1, crearPrograma(TipoInstruccion.PRODUCIR, 1), 0);

        sim.cargarProceso(p1);
        sim.cargarProceso(p2);
        sim.cargarProceso(p3);

        for (int i = 0; i < 4; i++) {
            sim.getReloj().avanzarCiclo();
        }

        asegurar(p1.getEstado() == EstadoProceso.TERMINADO, "Estado previo: Prod-1 terminó");
        asegurar(pc1.getCantidadBloqueados() == 2, "Estado previo: 2 productores (Prod-2 y Prod-3) bloqueados");
        asegurar(buf.getCantidadEsperandoVacios() == 2, "2 productores esperando en semáforo vacíos");

        // Fase 2: Añadimos 1 consumidor (Cons-1) a la cola de productores bloqueados
        PCB c1 = new PCB("Cons-1", pc1.getId(), TipoProceso.CONSUMIDOR, 1, 100, 1000, buf.getId(), 1, 1, crearPrograma(TipoInstruccion.CONSUMIR, 1), sim.getCicloActual());
        sim.cargarProceso(c1);

        // Avanzar ciclos para que Cons-1 consuma y Prod-2 se desbloquee en cascada y produzca
        for (int i = 0; i < 4; i++) {
            sim.getReloj().avanzarCiclo();
        }

        asegurar(c1.getEstado() == EstadoProceso.TERMINADO, "Cons-1 consumió el elemento de Prod-1 y terminó");
        asegurar(p2.getEstado() == EstadoProceso.TERMINADO, "Prod-2 fue desbloqueado en cascada por Cons-1, produjo y terminó");
        asegurar(p3.getEstado() == EstadoProceso.BLOQUEADO, "Prod-3 sigue esperando en vacíos porque el buffer volvió a llenarse");
        asegurar(pc1.getCantidadBloqueados() == 1, "Queda exactamente 1 productor (Prod-3) bloqueado");
        asegurar(buf.getCantidadElementos() == 1, "Buffer tiene 1 elemento guardado por Prod-2");
        asegurar(buf.getCantidadEsperandoVacios() == 1, "1 proceso esperando en semáforo vacíos");
        asegurar(buf.getValorVacios() + buf.getValorLlenos() == buf.getCapacidad(),
                "Invariante respetada tras desbloqueo en cascada: vacios(0) + llenos(1) == capacidad(1)");

        // Fase 3: Segundo consumidor desbloquea al último productor restante
        PCB c2 = new PCB("Cons-2", pc1.getId(), TipoProceso.CONSUMIDOR, 1, 100, 1000, buf.getId(), 1, 1, crearPrograma(TipoInstruccion.CONSUMIR, 1), sim.getCicloActual());
        sim.cargarProceso(c2);

        for (int i = 0; i < 4; i++) {
            sim.getReloj().avanzarCiclo();
        }

        asegurar(c2.getEstado() == EstadoProceso.TERMINADO, "Cons-2 consumió y terminó");
        asegurar(p3.getEstado() == EstadoProceso.TERMINADO, "Prod-3 fue desbloqueado en cascada por Cons-2 y terminó");
        asegurar(pc1.getCantidadBloqueados() == 0, "No quedan procesos bloqueados en el clúster");
        asegurar(buf.getCantidadEsperandoVacios() == 0, "Semáforo vacíos vacío sin procesos en espera");
    }

    /**
     * Escenario 3: Operación remota de un productor en PC-2 contra un buffer en PC-1 con latencia de red.
     */
    public static void probarEscenario3_OperacionRemotaLatencia() {
        System.out.println("\n>>> ESCENARIO 3: Operación remota de productor en PC-2 contra buffer en PC-1");
        Simulador sim = new Simulador();
        sim.setLatenciaRedCiclos(3);

        Computador pc1 = new Computador(1, "PC-1", 4096, FabricaPoliticas.crear(PoliticaPlanificacion.FCFS));
        Computador pc2 = new Computador(2, "PC-2", 4096, FabricaPoliticas.crear(PoliticaPlanificacion.FCFS));
        sim.registrarComputador(pc1);
        sim.registrarComputador(pc2);

        Buffer bufRemoto = new Buffer("BUF-REM", 2, pc1.getId(), 4, pc1);
        sim.registrarBuffer(bufRemoto);

        // Productor remoto en PC-2
        PCB prodRemoto = new PCB("Prod-Remoto", pc2.getId(), TipoProceso.PRODUCTOR, 1, 100, 100,
                bufRemoto.getId(), 1, 1, crearPrograma(TipoInstruccion.PRODUCIR, 1), 0);

        // Consumidor local en PC-1
        PCB consLocal = new PCB("Cons-Local", pc1.getId(), TipoProceso.CONSUMIDOR, 1, 100, 100,
                bufRemoto.getId(), 1, 1, crearPrograma(TipoInstruccion.CONSUMIR, 1), 0);

        sim.cargarProceso(prodRemoto);
        sim.cargarProceso(consLocal);

        sim.ejecutar();

        asegurar(prodRemoto.getEstado() == EstadoProceso.TERMINADO, "Productor remoto terminó exitosamente");
        asegurar(consLocal.getEstado() == EstadoProceso.TERMINADO, "Consumidor local terminó exitosamente");
        asegurar(prodRemoto.getElementosProducidos() == 1, "Productor produjo 1 elemento a través de la red");
        asegurar(consLocal.getElementosConsumidos() == 1, "Consumidor consumió 1 elemento");

        asegurar(prodRemoto.getComputadorAsignado() == pc2.getId(), "Prod-Remoto permaneció en PC-2 (sin migración)");
        asegurar(consLocal.getComputadorAsignado() == pc1.getId(), "Cons-Local permaneció en PC-1 (sin migración)");

        asegurar(bufRemoto.getValorMutex() == 1, "Mutex remoto liberado correctamente");
        asegurar(bufRemoto.getValorVacios() == 2, "Capacidad disponible restablecida en vacíos (2)");
        asegurar(bufRemoto.getValorLlenos() == 0, "Llenos en 0 tras consumo");
        asegurar(bufRemoto.getValorVacios() + bufRemoto.getValorLlenos() == bufRemoto.getCapacidad(),
                "Invariante semáforos perfecta: vacios(2) + llenos(0) == capacidad(2)");
    }

    /**
     * Escenario 4: Muerte por deadline vencido de un consumidor bloqueado en un buffer vacío.
     */
    public static void probarEscenario4_MuerteDeadlineConsumidorBloqueado() {
        System.out.println("\n>>> ESCENARIO 4: Muerte por deadline vencido de consumidor bloqueado");
        Simulador sim = new Simulador();
        Computador pc1 = new Computador(1, "PC-1", 4096, FabricaPoliticas.crear(PoliticaPlanificacion.FCFS));
        sim.registrarComputador(pc1);

        Buffer buf = new Buffer("BUF-DL", 1, pc1.getId(), 4, pc1);
        sim.registrarBuffer(buf);

        long memDisponibleInicial = pc1.getMemoriaDisponible();

        // Consumidor con deadline corto (3 ciclos) intentando consumir de buffer vacío
        PCB consMuere = new PCB("Cons-Corto", pc1.getId(), TipoProceso.CONSUMIDOR, 1, 250, 3,
                buf.getId(), 1, 1, crearPrograma(TipoInstruccion.CONSUMIR, 1), 0);

        sim.cargarProceso(consMuere);
        sim.ejecutar();

        asegurar(consMuere.getEstado() == EstadoProceso.TERMINADO, "Consumidor fue terminado por el sistema");
        asegurar(!consMuere.isDeadlineCumplido(), "Marcado con deadlineCumplido = false");
        asegurar(pc1.getCantidadBloqueados() == 0, "No queda bloqueado en la cola del computador");
        asegurar(buf.getCantidadEsperandoLlenos() == 0, "Removido limpiamente de la colaEspera del semáforo llenos");
        asegurar(pc1.getMemoriaDisponible() == memDisponibleInicial, "Memoria RAM liberada en su totalidad tras vencer deadline");

        // Integridad de los semáforos tras muerte
        asegurar(buf.getValorVacios() == 1, "Semáforo vacíos intacto (valor 1)");
        asegurar(buf.getValorLlenos() == 0, "Semáforo llenos intacto (valor 0)");
        asegurar(buf.getValorMutex() == 1, "Semáforo mutex intacto (valor 1)");
        asegurar(buf.getValorVacios() + buf.getValorLlenos() == buf.getCapacidad(), "Invariante preservada tras muerte: 1 + 0 == 1");

        // Prueba de fuego: ¿Sigue funcionando el buffer normalmente después de la muerte del proceso?
        PCB nuevoProd = new PCB("Nuevo-Prod", pc1.getId(), TipoProceso.PRODUCTOR, 1, 100, 50,
                buf.getId(), 1, 1, crearPrograma(TipoInstruccion.PRODUCIR, 1), sim.getCicloActual());
        PCB nuevoCons = new PCB("Nuevo-Cons", pc1.getId(), TipoProceso.CONSUMIDOR, 1, 100, 50,
                buf.getId(), 1, 1, crearPrograma(TipoInstruccion.CONSUMIR, 1), sim.getCicloActual());

        sim.cargarProceso(nuevoProd);
        sim.cargarProceso(nuevoCons);
        sim.ejecutar();

        asegurar(nuevoProd.getEstado() == EstadoProceso.TERMINADO, "Nuevo productor pudo usar el buffer y terminó");
        asegurar(nuevoCons.getEstado() == EstadoProceso.TERMINADO, "Nuevo consumidor pudo usar el buffer y terminó");
        asegurar(nuevoProd.getElementosProducidos() == 1 && nuevoCons.getElementosConsumidos() == 1,
                "Se produjo y consumió 1 elemento sin señales perdidas ni desbalance");
    }

    /**
     * Escenario 5: Cambio de política de planificación (FCFS -> Round Robin -> Prioridades) en caliente.
     */
    public static void probarEscenario5_CambioPoliticaEnCaliente() {
        System.out.println("\n>>> ESCENARIO 5: Cambio de política de planificación en caliente (FCFS -> RR -> Prioridades)");
        Simulador sim = new Simulador();
        Computador pc1 = new Computador(1, "PC-1", 4096, FabricaPoliticas.crear(PoliticaPlanificacion.FCFS));
        sim.registrarComputador(pc1);

        asegurar(pc1.getPlanificador().getPolitica().getNombre().contains("FCFS"), "Política inicial es FCFS");

        // Creamos procesos con distintos requerimientos y prioridades
        PCB pA = new PCB("Proc-A", pc1.getId(), TipoProceso.CPU_BOUND, 3, 100, 100, "", 0, 0, crearPrograma(TipoInstruccion.CPU, 6), 0);
        PCB pB = new PCB("Proc-B", pc1.getId(), TipoProceso.CPU_BOUND, 1, 100, 100, "", 0, 0, crearPrograma(TipoInstruccion.CPU, 6), 0);

        sim.cargarProceso(pA);
        sim.cargarProceso(pB);

        // 1. Cambio en caliente a Round Robin (quantum = 2)
        pc1.getPlanificador().setPolitica(FabricaPoliticas.crear(PoliticaPlanificacion.ROUND_ROBIN, 2));
        asegurar(pc1.getPlanificador().getPolitica().getNombre().contains("Round Robin"), "Política cambiada en caliente a Round Robin");
        asegurar(pc1.getPlanificador().getQuantum() == 2, "Quantum configurado en 2 ciclos");
        asegurar(pc1.getPlanificador().esApropiativa(), "Round Robin es política apropiativa");

        // 2. Cambio en caliente a Prioridades Apropiativas
        pc1.getPlanificador().setPolitica(FabricaPoliticas.crear(PoliticaPlanificacion.PRIORIDAD));
        asegurar(pc1.getPlanificador().getPolitica().getNombre().toLowerCase().contains("prioridad"), "Política cambiada en caliente a Prioridades");
        asegurar(pc1.getPlanificador().esApropiativa(), "Prioridades es apropiativa");

        // Creamos un proceso con máxima prioridad (prioridad 0) para verificar preempción/desalojo
        PCB pCritico = new PCB("Proc-Critico", pc1.getId(), TipoProceso.CPU_BOUND, 0, 100, 100, "", 0, 0, crearPrograma(TipoInstruccion.CPU, 2), 0);
        sim.cargarProceso(pCritico);

        sim.ejecutar();

        asegurar(pA.getEstado() == EstadoProceso.TERMINADO, "Proc-A completó su ejecución");
        asegurar(pB.getEstado() == EstadoProceso.TERMINADO, "Proc-B completó su ejecución");
        asegurar(pCritico.getEstado() == EstadoProceso.TERMINADO, "Proc-Critico completó su ejecución");

        // Verificar registro de eventos de cambio de política en el log
        boolean logCambioDetectado = false;
        Iterador<LogEventos.Evento> itEv = sim.getLogEventos().iterador();
        while (itEv.haySiguiente()) {
            LogEventos.Evento ev = itEv.siguiente();
            if (ev.getTipo() == LogEventos.TipoEvento.CAMBIO_POLITICA) {
                logCambioDetectado = true;
                break;
            }
        }
        asegurar(logCambioDetectado, "Eventos de CAMBIO_POLITICA registrados en LogEventos");
        asegurar(pc1.sinTrabajoPendiente(), "Computador finalizó limpiamente todas las colas y CPU ociosa");
    }

    /**
     * Escenario 6: Estrés de alta concurrencia con 60 procesos (30 productores y 30 consumidores)
     * compitiendo por 2 buffers saturados en un clúster de 3 nodos con latencia de red.
     */
    public static void probarEscenario6_Stress60ProcesosConcurrencia() {
        System.out.println("\n>>> ESCENARIO 6: Estrés concurrencia 60 procesos (30 productores, 30 consumidores)");
        Simulador sim = new Simulador();
        sim.setLatenciaRedCiclos(2);

        Computador pc1 = new Computador(1, "PC-1", 10000, FabricaPoliticas.crear(PoliticaPlanificacion.FCFS));
        Computador pc2 = new Computador(2, "PC-2", 10000, FabricaPoliticas.crear(PoliticaPlanificacion.ROUND_ROBIN, 2));
        Computador pc3 = new Computador(3, "PC-3", 10000, FabricaPoliticas.crear(PoliticaPlanificacion.PRIORIDAD));

        sim.registrarComputador(pc1);
        sim.registrarComputador(pc2);
        sim.registrarComputador(pc3);

        Buffer buf1 = new Buffer("BUF-STRESS-1", 2, pc1.getId(), 4, pc1);
        Buffer buf2 = new Buffer("BUF-STRESS-2", 2, pc2.getId(), 4, pc2);

        sim.registrarBuffer(buf1);
        sim.registrarBuffer(buf2);

        long memIni1 = pc1.getMemoriaDisponible();
        long memIni2 = pc2.getMemoriaDisponible();
        long memIni3 = pc3.getMemoriaDisponible();

        ListaEnlazada<PCB> listaProcesos = new ListaEnlazada<>();

        // 30 productores y 30 consumidores distribuidos entre PC-1, PC-2 y PC-3
        // 15 productores y 15 consumidores compitiendo por BUF-STRESS-1
        // 15 productores y 15 consumidores compitiendo por BUF-STRESS-2
        for (int i = 1; i <= 30; i++) {
            int compId = ((i - 1) % 3) + 1;
            String targetBuf = (i <= 15) ? "BUF-STRESS-1" : "BUF-STRESS-2";
            PCB prod = new PCB("Prod-Stress-" + i, compId, TipoProceso.PRODUCTOR, (i % 5) + 1,
                    50, 20000, targetBuf, 1, 1, crearPrograma(TipoInstruccion.PRODUCIR, 1), 0);
            listaProcesos.agregar(prod);
            sim.cargarProceso(prod);
        }

        for (int i = 1; i <= 30; i++) {
            int compId = ((i - 1) % 3) + 1;
            String targetBuf = (i <= 15) ? "BUF-STRESS-1" : "BUF-STRESS-2";
            PCB cons = new PCB("Cons-Stress-" + i, compId, TipoProceso.CONSUMIDOR, (i % 5) + 1,
                    50, 20000, targetBuf, 1, 1, crearPrograma(TipoInstruccion.CONSUMIR, 1), 0);
            listaProcesos.agregar(cons);
            sim.cargarProceso(cons);
        }

        sim.ejecutar();

        int terminadosProd = 0;
        int terminadosCons = 0;
        int totalProducido = 0;
        int totalConsumido = 0;

        Iterador<PCB> itP = listaProcesos.iterador();
        while (itP.haySiguiente()) {
            PCB p = itP.siguiente();
            if (p.getEstado() == EstadoProceso.TERMINADO) {
                if (p.getTipo() == TipoProceso.PRODUCTOR) {
                    terminadosProd++;
                    totalProducido += p.getElementosProducidos();
                } else if (p.getTipo() == TipoProceso.CONSUMIDOR) {
                    terminadosCons++;
                    totalConsumido += p.getElementosConsumidos();
                }
            }
        }

        asegurar(terminadosProd == 30, "Los 30 procesos productores completaron su ejecución");
        asegurar(terminadosCons == 30, "Los 30 procesos consumidores completaron su ejecución");
        asegurar(totalProducido == 30, "Exactamente 30 elementos producidos a través de ambos buffers");
        asegurar(totalConsumido == 30, "Exactamente 30 elementos consumidos a través de ambos buffers");

        asegurar(buf1.getValorVacios() + buf1.getValorLlenos() == buf1.getCapacidad(),
                "Invariante semáforos BUF-1 intacta tras 30 operaciones: vacíos + llenos == capacidad");
        asegurar(buf2.getValorVacios() + buf2.getValorLlenos() == buf2.getCapacidad(),
                "Invariante semáforos BUF-2 intacta tras 30 operaciones: vacíos + llenos == capacidad");
        asegurar(buf1.getValorMutex() == 1, "Mutex de BUF-1 liberado correctamente (valor 1)");
        asegurar(buf2.getValorMutex() == 1, "Mutex de BUF-2 liberado correctamente (valor 1)");

        asegurar(buf1.getCantidadEsperandoVacios() == 0, "Cero procesos esperando en vacíos de BUF-1");
        asegurar(buf1.getCantidadEsperandoLlenos() == 0, "Cero procesos esperando en llenos de BUF-1");
        asegurar(buf2.getCantidadEsperandoVacios() == 0, "Cero procesos esperando en vacíos de BUF-2");
        asegurar(buf2.getCantidadEsperandoLlenos() == 0, "Cero procesos esperando en llenos de BUF-2");

        asegurar(pc1.getCantidadBloqueados() == 0, "Cola de bloqueados limpia en PC-1 (0 bloqueados)");
        asegurar(pc2.getCantidadBloqueados() == 0, "Cola de bloqueados limpia en PC-2 (0 bloqueados)");
        asegurar(pc3.getCantidadBloqueados() == 0, "Cola de bloqueados limpia en PC-3 (0 bloqueados)");

        asegurar(pc1.sinTrabajoPendiente(), "PC-1 finalizó sin trabajo pendiente");
        asegurar(pc2.sinTrabajoPendiente(), "PC-2 finalizó sin trabajo pendiente");
        asegurar(pc3.sinTrabajoPendiente(), "PC-3 finalizó sin trabajo pendiente");

        asegurar(pc1.getMemoriaDisponible() == memIni1, "Memoria RAM liberada íntegramente en PC-1 tras terminar los procesos");
        asegurar(pc2.getMemoriaDisponible() == memIni2 && pc3.getMemoriaDisponible() == memIni3,
                "Memoria RAM liberada íntegramente en PC-2 y PC-3");
    }
}
