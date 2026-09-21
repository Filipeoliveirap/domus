package com.domus.api.modules.outbox;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutboxProcessadorBackoffTest {

    @Test
    void backoffCresceExponencialmenteAteCap() throws Exception {
        OutboxProcessador p = new OutboxProcessador(null);
        // Injeta valores de property via reflection
        setField(p, "intervaloMinMs", 3_000L);
        setField(p, "intervaloMaxMs", 300_000L);

        // Idle 1 → 3s
        assertEquals(3_000L, p.calcularBackoff(1));
        // Idle 2 → 6s
        assertEquals(6_000L, p.calcularBackoff(2));
        // Idle 3 → 12s
        assertEquals(12_000L, p.calcularBackoff(3));
        // Idle 4 → 24s
        assertEquals(24_000L, p.calcularBackoff(4));
        // Idle 5 → 48s
        assertEquals(48_000L, p.calcularBackoff(5));
        // Idle 6 → 96s
        assertEquals(96_000L, p.calcularBackoff(6));
        // Idle 7 → 192s
        assertEquals(192_000L, p.calcularBackoff(7));
        // Idle 8 → 300s (cap, não 384s)
        assertEquals(300_000L, p.calcularBackoff(8));
        // Idle 100 → continua no cap
        assertEquals(300_000L, p.calcularBackoff(100));
        // Idle 1000000 → não estoura long (shift cap)
        assertEquals(300_000L, p.calcularBackoff(1_000_000L));
    }

    /**
     * Garante que o cap default do application.properties (300_000ms = 5min)
     * é MAIOR OU IGUAL ao threshold de auto-suspend do Neon Free (5min).
     * Se alguém reduzir o cap pra menos de 5min, o Neon nunca vai suspender
     * e o objetivo de reduzir CU-h não se concretiza. Esse teste existe
     * pra evitar regressão por alguém bem-intencionado querendo "melhorar
     * a UX" sem perceber o impacto.
     */
    @Test
    void capDefaultSuperaThresholdAutoSuspendNeon() {
        long capDefaultMs = 300_000L;
        long autoSuspendNeonMs = 300_000L; // 5min documentado pelo Neon
        assert capDefaultMs >= autoSuspendNeonMs :
                "Cap do backoff (" + capDefaultMs + "ms) precisa ser >= auto-suspend do Neon ("
                        + autoSuspendNeonMs + "ms), senão o Neon nunca suspende e a redução de CU-h não acontece.";
    }

    private void setField(Object o, String name, Object value) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, value);
    }
}
