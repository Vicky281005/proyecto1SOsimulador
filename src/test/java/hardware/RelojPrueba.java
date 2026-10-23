package hardware;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pruebas del reloj concurrente (issue #30). Se ejecutan como programa normal
 * (clic derecho → Run File en NetBeans) y terminan con código 1 si algo falla.
 * No requieren JUnit.
 */
public class RelojPrueba {

    private static int fallos = 0;

    private static void comprobar(boolean condicion, String mensaje) {
        if (condicion) {
            System.out.println("  OK    " + mensaje);
        } else {
            fallos++;
            System.out.println("  FALLO " + mensaje);
        }
    }

    /** Componente de prueba que vigila el lockstep respecto a los demás. */
    private static class Contador implements ComponenteReloj {
        private final String nombre;
        private final long retardoMaxMs;
        final AtomicLong tics = new AtomicLong(0);
        final AtomicInteger violaciones = new AtomicInteger(0);
        volatile Thread hiloQueEjecuto;
        Contador[] otros = new Contador[0];

        Contador(String nombre, long retardoMaxMs) {
            this.nombre = nombre;
            this.retardoMaxMs = retardoMaxMs;
        }

        @Override
        public void tick(long ciclo) {
            hiloQueEjecuto = Thread.currentThread();
            // Mi ciclo debe ser exactamente el siguiente al último que procesé.
            if (ciclo != tics.get() + 1) {
                violaciones.incrementAndGet();
            }
            // Al empezar el ciclo N, ningún otro puede llevar menos de N-1 ni más de N ciclos.
            for (Contador o : otros) {
                long t = o.tics.get();
                if (t != ciclo - 1 && t != ciclo) {
                    violaciones.incrementAndGet();
                }
            }
            if (retardoMaxMs > 0) {
                try {
                    Thread.sleep((long) (Math.random() * retardoMaxMs));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            tics.incrementAndGet();
        }

        @Override
        public String getNombre() {
            return nombre;
        }
    }

    private static long hilosDelRelojVivos() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && t.getName().startsWith("Hilo-"))
                .count();
    }

    public static void main(String[] args) {
        System.out.println("1) Tres componentes (uno lento y aleatorio) avanzan en lockstep");
        Reloj reloj = new Reloj();
        Contador a = new Contador("A", 0);
        Contador b = new Contador("B", 0);
        Contador lento = new Contador("Lento", 6);
        a.otros = new Contador[]{b, lento};
        b.otros = new Contador[]{a, lento};
        lento.otros = new Contador[]{a, b};
        reloj.registrar(a);
        reloj.registrar(b);
        reloj.registrar(lento);
        reloj.registrar(a); // duplicado: se ignora
        comprobar(reloj.getCantidadComponentes() == 3, "hay 3 componentes (el duplicado no cuenta)");

        boolean sincronizados = true;
        for (int i = 1; i <= 200; i++) {
            reloj.avanzarCiclo();
            // Al retornar avanzarCiclo(), TODOS deben haber completado exactamente el ciclo actual.
            if (a.tics.get() != i || b.tics.get() != i || lento.tics.get() != i) {
                sincronizados = false;
            }
        }
        comprobar(reloj.getCicloActual() == 200, "el reloj llegó al ciclo 200");
        comprobar(sincronizados, "tras cada avanzarCiclo() los 3 habían ejecutado exactamente ese ciclo");
        comprobar(a.violaciones.get() == 0 && b.violaciones.get() == 0 && lento.violaciones.get() == 0,
                "0 violaciones de lockstep (a=" + a.violaciones + ", b=" + b.violaciones
                + ", lento=" + lento.violaciones + ")");

        System.out.println("2) Cada componente corre en su propio hilo");
        Thread principal = Thread.currentThread();
        comprobar(a.hiloQueEjecuto != principal && b.hiloQueEjecuto != principal
                && lento.hiloQueEjecuto != principal, "ningún tick corrió en el hilo que llama al reloj");
        comprobar(a.hiloQueEjecuto != b.hiloQueEjecuto && b.hiloQueEjecuto != lento.hiloQueEjecuto
                && a.hiloQueEjecuto != lento.hiloQueEjecuto, "los 3 componentes usan hilos distintos");

        System.out.println("3) desregistrar quita al componente y termina su hilo");
        long antes = lento.tics.get();
        reloj.desregistrar(lento);
        reloj.avanzarCiclo();
        reloj.avanzarCiclo();
        comprobar(reloj.getCantidadComponentes() == 2, "quedan 2 componentes");
        comprobar(lento.tics.get() == antes, "el componente quitado ya no recibe tics");
        comprobar(a.tics.get() == 202 && b.tics.get() == 202, "los demás siguen avanzando");

        System.out.println("4) Un error en un componente se relanza desde avanzarCiclo()");
        Reloj r2 = new Reloj();
        AtomicInteger llamadas = new AtomicInteger(0);
        r2.registrar(new ComponenteReloj() {
            @Override
            public void tick(long ciclo) {
                llamadas.incrementAndGet();
                if (ciclo == 3) {
                    throw new IllegalStateException("fallo de prueba");
                }
            }

            @Override
            public String getNombre() {
                return "Defectuoso";
            }
        });
        r2.avanzarCiclo();
        r2.avanzarCiclo();
        boolean lanzo = false;
        try {
            r2.avanzarCiclo();
        } catch (IllegalStateException e) {
            lanzo = "fallo de prueba".equals(e.getMessage());
        }
        comprobar(lanzo, "la excepción del ciclo 3 llegó a quien llamó a avanzarCiclo()");
        r2.avanzarCiclo();
        comprobar(llamadas.get() == 4 && r2.getCicloActual() == 4,
                "el reloj sigue funcionando después del error (ciclo " + r2.getCicloActual() + ")");
        r2.detener();

        System.out.println("5) detener libera todos los hilos");
        reloj.detener();
        r2.detener(); // idempotente
        comprobar(hilosDelRelojVivos() == 0, "no quedan hilos del reloj vivos");
        comprobar(reloj.getCantidadComponentes() == 0, "no quedan componentes registrados");
        lanzo = false;
        try {
            reloj.avanzarCiclo();
        } catch (IllegalStateException e) {
            lanzo = true;
        }
        comprobar(lanzo, "avanzarCiclo() tras detener lanza IllegalStateException");

        System.out.println("6) Validaciones y reinicio");
        Reloj r3 = new Reloj();
        lanzo = false;
        try {
            r3.registrar(null);
        } catch (IllegalArgumentException e) {
            lanzo = true;
        }
        comprobar(lanzo, "registrar(null) lanza IllegalArgumentException");
        Contador c = new Contador("C", 0);
        r3.registrar(c);
        r3.avanzarCiclo();
        r3.avanzarCiclo();
        r3.reiniciar();
        comprobar(r3.getCicloActual() == 0, "reiniciar() deja el ciclo en 0");
        r3.detener();
        comprobar(hilosDelRelojVivos() == 0, "sin hilos vivos al final");

        System.out.println();
        System.out.println(fallos == 0 ? "TODAS LAS PRUEBAS PASARON"
                : "FALLARON " + fallos + " COMPROBACIONES");
        System.exit(fallos == 0 ? 0 : 1);
    }
}
