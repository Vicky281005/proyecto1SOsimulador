# ÁvilaOS — Simulador de sistema operativo con sistema de archivos

Proyecto 2 de Sistemas Operativos (Unimet, trimestre 2627-1): «ÁvilaOS con sistema de archivos. Planificación de disco y suspensión de procesos».
Autoras: María Almonte y Alejandra Oporto.

ÁvilaOS simula un computador con CPU, RAM y un disco con sistema de archivos que avanzan al ritmo de un reloj global. Los procesos hacen operaciones de E/S sobre archivos (crear, leer, actualizar, eliminar), el disco las atiende según una política de planificación configurable y, cuando falta memoria, el planificador de mediano plazo suspende procesos bloqueados al área de intercambio (swap).

## Características
- Modelo de siete estados: nuevo, listo, ejecución, bloqueado, terminado, listo suspendido y bloqueado suspendido.
- Planificador de mediano plazo: suspende un proceso bloqueado al swap cuando falta RAM y reactiva procesos listo suspendido cuando hay espacio.
- Planificación de CPU intercambiable en ejecución: FCFS, Round Robin, Prioridades apropiativas y EDF apropiativa.
- Disco simulado con cuatro políticas de planificación, intercambiables en caliente: FIFO, SSTF, SCAN y C-SCAN (1 ciclo por bloque, velocidad configurable).
- Sistema de archivos jerárquico con dueño y permisos (público / privado); un proceso de otro usuario que intenta acceder a un archivo privado es rechazado y el hecho queda en la bitácora.
- Explorador de archivos, mapa del disco con la posición del cabezal y gráfica del desplazamiento del cabezal.
- Dos JSON de entrada: sistema de archivos (disco de 100 a 500 bloques) y configuración de procesos (ejemplos en `ejemplos/proyecto2/`).
- Bitácora de eventos (incluye disco, suspensión, reactivación y rechazos de E/S) y métricas de CPU, memoria y disco.
- Estructuras de datos propias (sin colecciones de java.util).

## Requisitos
- JDK 21
- Maven (o NetBeans)
- Dependencias (las descarga Maven): jfreechart 1.5.5 y gson 2.11.0

## Cómo ejecutarlo
1. Abrir el proyecto en NetBeans, hacer Clean and Build y ejecutar la clase `com.mycompany.proyecto1sosimulador.Proyecto1SOsimulador`.
2. En la ventana de configuración cargar los dos JSON (`ejemplos/proyecto2/sistema_archivos.json` y `ejemplos/proyecto2/procesos_config.json`) y pulsar «Aplicar ambos JSON y Abrir Simulación», o usar la configuración manual. El reloj no arranca hasta tener ambos JSON.
3. Con el argumento `--consola` se ejecuta el escenario de demostración y se imprime el reporte por consola.

## Cómo probar
Las pruebas son programas con `main()` en `src/test/java`. Después de compilar (Clean and Build), desde la raíz del proyecto (reemplazar RUTA por la carpeta donde está gson-2.11.0.jar, normalmente dentro de `.m2/repository/com/google/code/gson/gson/2.11.0`):

    java -cp "target/classes;target/test-classes;RUTA/gson-2.11.0.jar" integracion.IntegracionProyecto2Prueba
    java -cp "target/classes;target/test-classes;RUTA/gson-2.11.0.jar" nucleo.SuspensionSwapPrueba
    java -cp "target/classes;target/test-classes;RUTA/gson-2.11.0.jar" nucleo.ProcesoESPrueba
    java -cp "target/classes;target/test-classes;RUTA/gson-2.11.0.jar" nucleo.LogMetricasSuspensionPrueba
    java -cp "target/classes;target/test-classes;RUTA/gson-2.11.0.jar" config.PersistenciaPrueba
    java -cp "target/classes;target/test-classes;RUTA/gson-2.11.0.jar" config.ValidacionDiscoPrueba
    java -cp "target/classes;target/test-classes;RUTA/gson-2.11.0.jar" simulacion.GraficaDiscoPrueba
    java -cp "target/classes;target/test-classes;RUTA/gson-2.11.0.jar" estructuras.EscaneoColeccionesProhibidasPrueba

`IntegracionProyecto2Prueba` debe ejecutarse desde la raíz del proyecto porque lee los JSON de `ejemplos/proyecto2/`. Verifica, entre otras cosas, los desplazamientos totales del anexo del enunciado (FIFO 518, SSTF 208, SCAN 332, C-SCAN 192).

En Linux o macOS se usa `:` en lugar de `;` como separador del classpath. Las pruebas `gui.VentanaDiscoPrueba`, `gui.PanelExploradorArchivosPrueba` y `gui.VentanaMetricasPrueba` necesitan entorno gráfico. Otras pruebas disponibles: simulacion.MetricasPrueba, simulacion.SimuladorControlPrueba, simulacion.HistorialUtilizacionPrueba, hardware.RelojPrueba y gui.ValidadorProcesoPrueba.

## Ejemplos
En `ejemplos/proyecto2/` están los dos JSON del enunciado (sistema de archivos y procesos).

## Informe
Informe del Proyecto 1: https://docs.google.com/document/d/1cdSB2gOKM-RMue0dC088aRc9dJCAq8c4xHOr6vEPkOU/edit?usp=sharing

Informe del Proyecto 2: https://docs.google.com/document/d/1FdyxvBacxGL_nZE4qOoRQPHx11gwcFUSY5aRqBcSja0/edit?usp=sharing
