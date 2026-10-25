# AGENTS.md

## Build / Run
- **Java**: 21 (`maven.compiler.release=21`), UTF-8
- **Build tool**: Maven
- **Main class**: `com.mycompany.proyecto1sosimulador.Proyecto1SOsimulador`
  - Opens GUI by default (`gui.LanzadorVista2`); `--consola` runs console mode using `config.EscenarioDemo`
- **External dep**: `org.jfree:jfreechart:1.5.5` only (besides JDK)

**Commands (from repo root):**
```bash
mvn compile
mvn exec:java
mvn exec:java -Dexec.args="--consola"
```

**Run tests manually (no JUnit in repo):**
```bash
# compile everything including tests
javac -d /tmp/out -classpath "/Users/vicky2805/.m2/repository/org/jfree/jfreechart/1.5.5/jfreechart-1.5.5.jar" $(find src/main/java -name '*.java') $(find src/test/java -name '*Prueba.java')

# run
java -cp "/tmp/out:/Users/vicky2805/.m2/repository/org/jfree/jfreechart/1.5.5/jfreechart-1.5.5.jar" simulacion.SimuladorControlPrueba
java -cp "/tmp/out:/Users/vicky2805/.m2/repository/org/jfree/jfreechart/1.5.5/jfreechart-1.5.5.jar" simulacion.HistorialUtilizacionPrueba
java -cp "/tmp/out:/Users/vicky2805/.m2/repository/org/jfree/jfreechart/1.5.5/jfreechart-1.5.5.jar" hardware.RelojPrueba
java -cp "/tmp/out:/Users/vicky2805/.m2/repository/org/jfree/jfreechart/1.5.5/jfreechart-1.5.5.jar" gui.ValidadorProcesoPrueba
```

## Architecture (repo-specific)
- **Packages**: `com.mycompany.proyecto1sosimulador`, `config`, `enums`, `estructuras` (own TDA: `ListaEnlazada`, iterators - **no `java.util` collections**), `gui`, `hardware`, `modelo` (`PCB`, `Instruccion`), `nucleo` (`Computador`, kernel), `planificacion` (policies + factory), `simulacion` (`Simulador`, `Reloj`, `HistorialUtilizacion`), `sincronizacion` (simulated semaphores + `Buffer`)
- **Concurrency model**: `Simulador` uses a clock + mutex/semaphores; GUI reads state via `Simulador.leerEstado(Runnable)`. **Simulated semaphores do not block Java threads** - processes move between states via kernel.
- **Scheduling/preemption**: Policies FCFS/RR/EDF/Prioridades. Real preemption (RR by quantum, priority/deadline). Deadline miss -> `TERMINADO` with `deadlineCumplido=false`, RAM/CPU freed.
- **Sync/IPC**: Producer-consumer uses 3 semaphores (`vacios/llenos/mutex`). `Buffer` in `sincronizacion/` reserves host RAM on creation. Access local/remote with `LATENCIA_RED` blocking.
- **GUI required**: Must open on `Run Project` (main defaults to GUI). Vista 1 creates processes (CPU_BOUND/IO_BOUND/PRODUCTOR/CONSUMIDOR), Vista 2 monitors.

## Constraints / Gotchas
- **No `java.util` collections** - use `estructuras.ListaEnlazada` and iterators
- **Simulated semaphores only** - never use Java blocking locks to block processes; use kernel queues
- **Stallings order** for producer: `wait(vacios) -> wait(mutex) -> produce -> signal(mutex) -> signal(llenos)` (inverse for consumer)
- **No process migration**
- **Spanish Javadoc** style
- **Branches**: features to `develop` via PR, `main` untouched until end
- **Tests are manual `*Prueba.java` with `main`** (no JUnit)
- **Swing warnings** (`serial`, `this-escape`) are pre-existing
