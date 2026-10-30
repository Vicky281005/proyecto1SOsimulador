# ÁvilaOS — Simulador de sistema operativo distribuido

Proyecto 1 de Sistemas Operativos (Unimet, trimestre 2627-1).
Autoras: María Almonte y Alejandra Oporto.

ÁvilaOS simula varios computadores independientes (cada uno con su CPU, su RAM y su política de planificación) que avanzan al ritmo de un reloj global y cooperan mediante semáforos y buffers acotados (productor/consumidor), incluso cuando el buffer está en otro computador (con latencia de red configurable).

## Características
- Modelo de cinco estados: nuevo, listo, ejecución, bloqueado y terminado.
- Políticas de planificación intercambiables en ejecución: FCFS, Round Robin (quantum configurable), Prioridades apropiativas y EDF apropiativa.
- Semáforos propios con cola FIFO y buffers acotados con el esquema vacíos / llenos / mutex.
- Acceso remoto a buffers con latencia de red y bloqueo del proceso.
- Reloj global con un hilo por componente y una barrera de sincronización.
- Configuración y persistencia en JSON y CSV, bitácora de eventos, gráfica de utilización de CPU y métricas.
- Estructuras de datos propias (sin colecciones de java.util).

## Requisitos
- JDK 21
- Maven (o NetBeans)
- Dependencias (las descarga Maven): jfreechart 1.5.5 y gson 2.11.0

## Cómo ejecutarlo
1. Abrir el proyecto en NetBeans, hacer Clean and Build y ejecutar la clase com.mycompany.proyecto1sosimulador.Proyecto1SOsimulador. Se abre la interfaz gráfica con un escenario de demostración.
2. Con el argumento --consola se ejecuta el escenario de demostración y se imprime el reporte por consola.

## Cómo probar
Las pruebas son programas con main() en src/test/java. Después de compilar, desde la raíz del proyecto (reemplazar RUTA por la carpeta donde está gson-2.11.0.jar, normalmente dentro de .m2/repository/com/google/code/gson/gson/2.11.0):

    java -cp "target/classes;target/test-classes;RUTA/gson-2.11.0.jar" simulacion.EscenariosSincronizacionPrueba
    java -cp "target/classes;target/test-classes;RUTA/gson-2.11.0.jar" config.PersistenciaPrueba
    java -cp "target/classes;target/test-classes;RUTA/gson-2.11.0.jar" estructuras.EscaneoColeccionesProhibidasPrueba

En Linux o macOS se usa : en lugar de ; como separador del classpath. Otras pruebas disponibles: simulacion.MetricasPrueba, simulacion.SimuladorControlPrueba, simulacion.HistorialUtilizacionPrueba, hardware.RelojPrueba y gui.ValidadorProcesoPrueba.

## Ejemplos
En la carpeta ejemplos/ hay una configuración JSON de ejemplo que se puede cargar desde la ventana de configuración.

## Informe
https://docs.google.com/document/d/1cdSB2gOKM-RMue0dC088aRc9dJCAq8c4xHOr6vEPkOU/edit?usp=sharing
